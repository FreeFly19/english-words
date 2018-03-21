package models

import javax.inject.Inject
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}


class TranslationRepository  @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._


  private class TranslationsTable(tag: Tag) extends Table[Translation](tag, "translations") {

    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def phrase = column[String]("phrase")

    def * = (id, phrase) <> ((Translation.apply _).tupled, Translation.unapply)
  }

  private val translations = TableQuery[TranslationsTable]

  def list(): Future[Seq[Translation]] = db.run {
    translations.result
  }

}
