package addon

import shared.model.*
import shared.basic.*
import services.ClubDB
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object TestClub:

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match
      case 1 => testClub_add(group, number, param)
      case 2 => testClub_delete(group, number, param)
      case 3 => testClub_merge(group, number, param)
      case 4 => testClub_list(group, number, param)
      case 5 => testClub_sync(group, number, param)
      case _ =>
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknown test number")))

  def testClub_add(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val parts = param.split(",")
    val name = parts(0).trim
    val checkSimilarity = if parts.length > 1 then parts(1).trim.toBoolean else true

    services.TourneyDB.tourney.addClub(name, checkSimilarity) match
      case Left(err) =>
        addOutput(s"Error adding club '$name' (checkSimilarity=$checkSimilarity): ${err.msg}")
        Future(Left(err))
      case Right(club) =>
        addOutput(s"Added club: ${club.name} (ID: ${ClubId.value(club.id)}, Active: ${club.active}, checkSimilarity=$checkSimilarity)")
        Future(Right(s"FINISHED: ${group}-Test:${number}"))

  def testClub_delete(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    try
      val id = ClubId(param.toInt)
      services.TourneyDB.tourney.deleteClub(id) match
        case Left(err) =>
          addOutput(s"Error deleting club ID $param: ${err.msg}")
          Future(Left(err))
        case Right(_) =>
          addOutput(s"Deleted (deactivated) club ID $param successfully")
          Future(Right(s"FINISHED: ${group}-Test:${number}"))
    catch
      case _: Exception =>
        addOutput(s"Invalid ID: $param")
        Future(Left(AppError("invalid.id")))

  def testClub_merge(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    // Expecting param in format "sourceId,targetId"
    val ids = param.split(",")
    if ids.length != 2 then
      addOutput("Param must be 'sourceId,targetId'")
      Future(Left(AppError("invalid.param")))
    else
      try
        val sourceId = ClubId(ids(0).trim.toInt)
        val targetId = ClubId(ids(1).trim.toInt)
        services.TourneyDB.tourney.mergeClubs(sourceId, targetId) match
          case Left(err) =>
            addOutput(s"Error merging: $err")
            Future(Left(AppError(err)))
          case Right(_) =>
            addOutput(s"Merged successfully: $sourceId into $targetId")
            Future(Right(s"FINISHED: ${group}-Test:${number}"))
      catch
        case _: Exception =>
          addOutput(s"Invalid IDs in: $param")
          Future(Left(AppError("invalid.id")))

  def testClub_list(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val t = services.TourneyDB.tourney
    addOutput(s"Clubs in DB (${t.clubs.length}):")
    t.clubs.foreach { club =>
      addOutput(s"- [${ClubId.value(club.id)}] ${club.name} (Active: ${club.active})")
    }
    Future.successful(Right(s"FINISHED: ${group}-Test:${number}"))

  def testClub_sync(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    addOutput(s"Syncing clubs...")
    services.TourneyDB.tourney.syncClubs()
    // Sync is async, but this test just triggers it
    Future.successful(Right(s"FINISHED: ${group}-Test:${number}"))
