package models

import java.sql.Timestamp

import javax.inject.Inject
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}


class PhrasesRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  class PhrasesTable(tag: Tag) extends Table[DbPhrase](tag, "phrases") {

    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def text = column[String]("text")
    def createdAt = column[Timestamp]("created_at")

    def * = (id.?, text, createdAt) <> ((DbPhrase.apply _).tupled, DbPhrase.unapply)
  }

  val phrases = TableQuery[PhrasesTable]


  class TranslationsTable(tag: Tag) extends Table[DbTranslation](tag, "translations") {

    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def phraseId = column[Long]("phrase_id")
    def picture = column[String]("picture")
    def value = column[String]("value")
    def votes = column[Long]("votes")

    def * = (id.?, phraseId.?, picture, value, votes) <> ((DbTranslation.apply _).tupled, DbTranslation.unapply)
    def phrase = foreignKey("phrase", phraseId, phrases)(_.id)
  }

  val translations = TableQuery[TranslationsTable]

  def list(): Future[Seq[(DbPhrase, Seq[DbTranslation])]] =
    db.run(phrases joinLeft translations on (_.id === _.phraseId) result)
      .map(list => {
        // Todo: requires some refactoring
        list.groupBy(_._1)
          .map(p => p._2.foldLeft((p._1, ArrayBuffer[DbTranslation]()))((acc, el) => {
            el._2.foreach(acc._2.+=)
            acc
          }))
          .toSeq
          .sortBy(-_._1.createdAt.getTime)
      })

  def add(phrase: DbPhrase, trs: Seq[DbTranslation]): Future[(DbPhrase, List[DbTranslation])] = {
    val insertPhrase = phrases returning phrases += _
    val insertTranslation = translations returning translations += _

    var actions = for {
      p <- insertPhrase(phrase)
      translations <- DBIO.sequence(trs.map(t => insertTranslation(t.copy(phraseId = p.id))))
    } yield (p, translations.toList)

    db.run(actions.transactionally)
  }
}
