package shared.model

import shared.basic.Pickle.{ReadWriter, given}

case class MatchboardEntry(
  id: String,  
  entryType: String, // "start", "finish", "info" or "delete"
  court: Option[String] = None,
  nameA: Option[String] = None,
  nameB: Option[String] = None,
  compName: Option[String] = None,
  stageId: Option[StageId] = None,
  gameNo:  Option[Int] = None,
  text: Option[String] = None
) derives ReadWriter

case class Matchboard(
  tourneyName: String,
  entries: Seq[MatchboardEntry]
) derives ReadWriter

case class MatchboardSetRequest(
  action: String, // "set", "ended", "delete"
  tourneyName: Option[String] = None,
  entry: Option[MatchboardEntry] = None
) derives ReadWriter

case class MatchboardSetResponse(
  success: Boolean
) derives ReadWriter
