package controllers

import java.nio.file.{Path, Paths, StandardOpenOption}
import java.util.Date
import javax.inject._

import akka.stream.scaladsl.{FileIO, Flow, Framing, Keep, Sink, Source}
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import io.gatling.jsonpath.JsonPath
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
                               playBodyParsers: PlayBodyParsers,
                               implicit val ec: ExecutionContext,
                               implicit val mv: Materializer) extends AbstractController(cc) {
  implicit val translatesFormat = Json.format[Translate]

  def index() = Action(Ok(views.html.index(data)))

  def translate() = Action.async(playBodyParsers.json) { implicit request: Request[JsValue] =>
    request.body \ "word" match {
      case JsDefined(JsString(word)) => translateAndSave(word).map(translates => Ok(Json.toJson(translates)))
      case _ => Future(BadRequest)
    }
  }

  private val objectMapper = new ObjectMapper() with ScalaObjectMapper
  objectMapper.registerModule(DefaultScalaModule)

  private val file = Paths.get(config.get[String]("db.file"))

  private var data = List[RequestTranslate]()

  private val foreach: Future[IOResult] = FileIO.fromPath(file)
    .via(Framing.delimiter(ByteString("\n"), 8192, true).map(_.utf8String))
    .filter(_.nonEmpty)
    .map(content => objectMapper.readValue(content, classOf[RequestTranslate]))
    .to(Sink.foreach(s => data = s :: data))
    .run()

  private def lineSink(file: Path): Sink[String, Future[IOResult]] =
    Flow[String]
      .map(s => ByteString(s + "\n"))
      .toMat(FileIO.toPath(file, Set(StandardOpenOption.APPEND)))(Keep.right)


  private def translateAndSave(wordToTranslate: String) =
    ws
      .url("http://api.lingualeo.com/gettranslates?port=1001")
      .post(Map("word" -> wordToTranslate))
      .map(r => {
        val list = JsonPath.query("$.translate[*]['value', 'pic_url', 'votes']", new ObjectMapper().readValue(r.body, classOf[Object]))

        var translates = list.right.get.toList.grouped(3).toList.map(l => {
          Translate(l(0).toString, l(1).toString, l(2).toString.toLong)
        })

        val t = RequestTranslate(wordToTranslate, new Date().getTime, translates)
        write(t)
        data = t :: data

        translates
      })

  private def write(requestTranslate: RequestTranslate) =
    Source
      .single(objectMapper.writeValueAsString(requestTranslate))
      .runWith(lineSink(file))
}

case class Translate(value: String, pic_url: String, votes: Long)

case class RequestTranslate(phrase: String, date: Long, translates: List[Translate])
