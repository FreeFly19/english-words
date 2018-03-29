package controllers

import java.util.Date

import akka.stream.Materializer
import com.fasterxml.jackson.databind.ObjectMapper
import io.gatling.jsonpath.JsonPath
import javax.inject._
import models.{Phrase, PhrasesRepository}
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
  implicit val translatesFormat = Json.format[Translation]
  implicit val translatesFormat2 = Json.format[Phrase]

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
    translationRepository.list().map(list => Ok(Json.toJson(list)))
  }

  private def translateAndSave(wordToTranslate: String) =
    ws
      .url("http://api.lingualeo.com/gettranslates?port=1001")
      .post(Map("word" -> wordToTranslate))
      .map(res => {
        val list = JsonPath.query("$.translate[*]['value', 'pic_url', 'votes']", new ObjectMapper().readValue(res.body, classOf[Object]))

        var translates = list.right.get.toList.grouped(3).toList.map(l => {
          Translation(l(0).toString, l(1).toString, l(2).toString.toLong)
        })

        TranslationResult(wordToTranslate, new Date().getTime, translates)
      })
      .flatMap(save)
      .map(_.translations)

  private def save(requestTranslate: TranslationResult) =
    translationRepository.add(Phrase(text = requestTranslate.phrase))
      .map(_ => requestTranslate)
}

case class Translation(value: String, pic_url: String, votes: Long)

case class TranslationResult(phrase: String, date: Long, translations: List[Translation])
