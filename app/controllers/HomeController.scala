package controllers

import java.sql.Timestamp
import java.util.Date

import akka.stream.Materializer
import com.fasterxml.jackson.databind.ObjectMapper
import io.gatling.jsonpath.JsonPath
import javax.inject._
import models.{DbPhrase, DbTranslation, PhrasesRepository}
import play.api.Configuration
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class HomeController @Inject()(cc: ControllerComponents,
                               ws: WSClient,
                               config: Configuration,
                               translationRepository: PhrasesRepository,
                               playBodyParsers: PlayBodyParsers,
                               implicit val ec: ExecutionContext,
                               implicit val mv: Materializer) extends AbstractController(cc) {

  implicit val timestampFormat = new Format[Timestamp] {
    def writes(t: Timestamp): JsValue = toJson(t.getTime)
    def reads(json: JsValue): JsResult[Timestamp] = fromJson[Long](json).map(new Timestamp(_))
  }

  implicit val translationDtoFormatter = Json.format[TranslationDto]
  implicit val phraseTranslationResultDtoFormatter = Json.format[PhraseTranslationResultDto]

  def translate() = Action.async(playBodyParsers.json) { implicit request: Request[JsValue] =>
    request.body \ "phrase" match {
      case JsDefined(JsString(word)) => translatePhraseAndSave(word).map(translates => Ok(Json.toJson(translates)))
      case _ => Future(BadRequest)
    }
  }

  def phrasesList() = Action.async {
    translationRepository.list()
      .map(_.map(t => dbPhraseToPhraseTranslationResultDto(t._1, t._2)))
      .map(list => Ok(Json.toJson(list)))
  }

  def deletePhrase(id: Long) = Action.async {
    translationRepository.deletePhrase(id).map(_ => Ok)
  }

  private def translatePhraseAndSave(phrase: String): Future[PhraseTranslationResultDto] =
    ws
      .url("http://api.lingualeo.com/gettranslates?port=1001")
      .post(Map("word" -> phrase))
      .map(res => {
        val list = JsonPath.query("$.translate[*]['value', 'pic_url', 'votes']", new ObjectMapper().readValue(res.body, classOf[Object]))

        var translations = list.right.get.toList.grouped(3).toList
          .map(l => DbTranslation(None, None, l(0).toString, l(1).toString, l(2).toString.toLong))

        (DbPhrase(None, phrase, new Timestamp(new Date().getTime)), translations)
      })
      .flatMap(phraseTranslations => translationRepository.add(phraseTranslations._1, phraseTranslations._2))
      .map(phraseTranslations => dbPhraseToPhraseTranslationResultDto(phraseTranslations._1, phraseTranslations._2))

  private def dbPhraseToPhraseTranslationResultDto(p: DbPhrase, ts: Seq[DbTranslation]) =
    PhraseTranslationResultDto(p.id.get, p.text, p.createdAt.getTime, ts.map(dbTranslationToTranslationDto))

  private def dbTranslationToTranslationDto(t: DbTranslation) =
    TranslationDto(t.id.get, t.value, t.picture, t.votes)
}

case class TranslationDto(id: Long, value: String, picture: String, votes: Long)
case class PhraseTranslationResultDto(id: Long, text: String, date: Long, translations: Seq[TranslationDto])
