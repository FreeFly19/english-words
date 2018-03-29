package controllers

import akka.stream.Materializer
import com.fasterxml.jackson.databind.ObjectMapper
import io.gatling.jsonpath.JsonPath
import javax.inject._
import models.{DbPhrase, DbTranslation, PhrasesRepository}
import play.api.Configuration
import play.api.libs.json._
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
  implicit val translatesFormat = Json.format[DbPhrase]
  implicit val translatesFormat2 = Json.format[TranslationDto]
  implicit val translatesFormat3 = Json.format[TranslationResult]
  implicit val translatesFormat4 = Json.format[DbTranslation]

  def index() = Action {
    Ok(views.html.index(Nil))
  }

  def translate() = Action.async(playBodyParsers.json) { implicit request: Request[JsValue] =>
    request.body \ "word" match {
      case JsDefined(JsString(word)) => translateAndSave(word).map(translates => Ok(Json.toJson(translates)))
      case _ => Future(BadRequest)
    }
  }

  def pagedTranslations() = Action.async {
    translationRepository.list()
      .map(_.map(t => dbPhraseToTranslationResult(t._1, t._2)))
      .map(list => Ok(Json.toJson(list)))
  }

  private def translateAndSave(wordToTranslate: String): Future[TranslationResult] =
    ws
      .url("http://api.lingualeo.com/gettranslates?port=1001")
      .post(Map("word" -> wordToTranslate))
      .map(res => {
        val list = JsonPath.query("$.translate[*]['value', 'pic_url', 'votes']", new ObjectMapper().readValue(res.body, classOf[Object]))

        var translations = list.right.get.toList.grouped(3).toList
          .map(l => DbTranslation(None, None, l(0).toString, l(1).toString, l(2).toString.toLong))

        (DbPhrase(None, wordToTranslate), translations)
      })
      .flatMap(phraseTranslations => translationRepository.add(phraseTranslations._1, phraseTranslations._2))
      .map(phraseTranslations => dbPhraseToTranslationResult(phraseTranslations._1, phraseTranslations._2))

  private def dbPhraseToTranslationResult(p: DbPhrase, ts: Seq[DbTranslation]) =
    TranslationResult(p.id.get, p.text, 0, ts.map(dbTranslationToTranslationDto))

  private def dbTranslationToTranslationDto(t: DbTranslation) =
    TranslationDto(t.id.get, t.value, t.picture, t.votes)
}

case class TranslationDto(id: Long, value: String, picture: String, votes: Long)
case class TranslationResult(id: Long, phrase: String, date: Long, translations: Seq[TranslationDto])
