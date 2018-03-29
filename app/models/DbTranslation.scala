package models

case class DbTranslation(id: Option[Long] = None, phraseId: Option[Long] = None, value: String, picture: String, votes: Long) {
  def this(value: String, picture: String, votes: Long) = this(None, None, value, picture, votes)
}
