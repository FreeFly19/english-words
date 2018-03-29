package models

import javax.inject.Inject
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}


class PhrasesRepository  @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._


  private class PhrasesTable(tag: Tag) extends Table[Phrase](tag, "phrases") {

    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def text = column[String]("text")

    def * = (id.?, text) <> ((Phrase.apply _).tupled, Phrase.unapply)
  }

  private val translations = TableQuery[PhrasesTable]

  def list(): Future[Seq[Phrase]] = db.run {
    translations.result
  }

  def add(phrase: Phrase): Future[Phrase] = db.run {
    translations += phrase
  }.map(_ => phrase)
}
