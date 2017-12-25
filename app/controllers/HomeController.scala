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
import play.api.libs.json.Json
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
                               implicit val ec: ExecutionContext,
                               implicit val mv: Materializer) extends AbstractController(cc) {

  val objectMapper = new ObjectMapper() with ScalaObjectMapper
  objectMapper.registerModule(DefaultScalaModule)

  val file = Paths.get(config.get[String]("db.file"))

  var data = List[RequestTranslate]()

  val foreach: Future[IOResult] = FileIO.fromPath(file)
    .via(Framing.delimiter(ByteString("\n"), 8192, true).map(_.utf8String))
    .filter(_.nonEmpty)
    .map(content => objectMapper.readValue(content, classOf[RequestTranslate]))
    .to(Sink.foreach(s => data = s :: data))
    .run()


  def index() = Action.async { implicit request: Request[AnyContent] =>
    val translate = request.getQueryString("translate")

    if (translate.isEmpty) {
      Future(Ok(views.html.index(Nil, data)))
    }
    else {
      ws
        .url("http://api.lingualeo.com/gettranslates?port=1001")
        .post(Map("word" -> translate.get))
        .map(r => {
          println(Json.parse(r.body))

          val list = JsonPath.query("$.translate[*]['value', 'pic_url', 'votes']", new ObjectMapper().readValue(r.body, classOf[Object]))

          var res = list.right.get.toList.grouped(3).toList.map(l => {
            Translate(l(0).toString, l(1).toString, l(2).toString.toLong)
          })

          val t = RequestTranslate(translate.get, new Date().getTime, res)
          data = t :: data
          write(t)

          Ok(views.html.index(res, data))
        })
    }
  }

  def plugin() = Action.async { implicit request: Request[AnyContent] =>
    val translate = request.getQueryString("translate")

    ws
      .url("http://api.lingualeo.com/gettranslates?port=1001")
      .post(Map("word" -> translate.get))
      .map(r => {
        println(Json.parse(r.body))

        val list = JsonPath.query("$.translate[*]['value', 'pic_url', 'votes']", new ObjectMapper().readValue(r.body, classOf[Object]))

        var res = list.right.get.toList.grouped(3).toList.map(l => {
          Translate(l(0).toString, l(1).toString, l(2).toString.toLong)
        })

        val t = RequestTranslate(translate.get, new Date().getTime, res)
        data = t :: data
        write(t)

        Ok(views.html.plugin(translate.get, res))
      })
  }

  def lineSink(file: Path): Sink[String, Future[IOResult]] =
    Flow[String]
      .map(s => ByteString(s + "\n"))
      .toMat(FileIO.toPath(file, Set(StandardOpenOption.APPEND)))(Keep.right)


  def write(requestTranslate: RequestTranslate) = {
    Source
      .single(objectMapper.writeValueAsString(requestTranslate))
      .runWith(lineSink(file))
  }
}

case class Translate(value: String, pic_url: String, votes: Long)
case class RequestTranslate(phrase: String, date: Long, translates: List[Translate])
