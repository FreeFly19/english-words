package models

import java.sql.Timestamp

case class DbPhrase(id: Option[Long], text: String, createdAt: Timestamp)

