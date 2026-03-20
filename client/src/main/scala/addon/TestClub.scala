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
      case _ =>
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknown test number")))

  def testClub_add(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val parts = param.split(",")
    val name = parts(0).trim
    val checkSimilarity = if parts.length > 1 then parts(1).trim.toBoolean else true

    ClubDB.add(name, checkSimilarity) match
      case Left(err) =>
        addOutput(s"Error adding club '$name' (checkSimilarity=$checkSimilarity): ${err.msg}")
        Future(Left(err))
      case Right(club) =>
        addOutput(s"Added club: ${club.name} (ID: ${ClubId.value(club.id)}, Active: ${club.active}, checkSimilarity=$checkSimilarity)")
        Future(Right(s"FINISHED: ${group}-Test:${number}"))

  def testClub_delete(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    try
      val id = ClubId(param.toInt)
      ClubDB.deleteClub(id) match
        case Left(err) =>
          addOutput(s"Error deleting club ID $param: ${err.msg}")
          Future(Left(err))
        case Right(club) =>
          addOutput(s"Deleted (deactivated) club: ${club.name} (ID: ${ClubId.value(club.id)}, Active: ${club.active})")
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
        ClubDB.merge(sourceId, targetId) match
          case Left(err) =>
            addOutput(s"Error merging: $err")
            Future(Left(AppError(err)))
          case Right(targetClub) =>
            addOutput(s"Merged successfully into: ${targetClub.name}")
            Future(Right(s"FINISHED: ${group}-Test:${number}"))
      catch
        case _: Exception =>
          addOutput(s"Invalid IDs in: $param")
          Future(Left(AppError("invalid.id")))

  def testClub_list(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    addOutput(s"Clubs in DB (${ClubDB.clubs.length}):")
    ClubDB.clubs.foreach { club =>
      addOutput(s"- [${ClubId.value(club.id)}] ${club.name} (Active: ${club.active})")
    }
    Future(Right(s"FINISHED: ${group}-Test:${number}"))
