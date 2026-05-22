package addon

import shared.model.*
import shared.basic.*
import services.PlayerDB
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/**
 * TestPlayer provides addon console commands to test PlayerDB functionality.
 */
object TestPlayer:

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match
      case 1 => testPlayer_add(group, number, param)
      case 2 => testPlayer_delete(group, number, param)
      case 3 => testPlayer_merge(group, number, param)
      case 4 => testPlayer_list(group, number, param)
      case 5 => testPlayer_sync(group, number, param)
      case _ =>
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknown test number")))

  /**
   * Test 1: Add a player. Param format: "firstName,lastName,clubId,birthYear"
   */
  def testPlayer_add(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val parts = param.split(",")
    if (parts.length < 3) {
      addOutput("Param must be 'firstName,lastName,clubId[,birthYear]'")
      return Future(Left(AppError("invalid.param")))
    }

    val firstName = parts(0).trim
    val lastName = parts(1).trim
    val clubId = parts(2).trim.toInt
    val birthYear = if (parts.length > 3) Some(parts(3).trim.toInt) else None

    services.TourneyDB.tourney.addPlayer(firstName, lastName, clubId, birthYear) match
      case Left(err) =>
        addOutput(s"Error adding player '$firstName $lastName': ${err.msg}")
        Future(Left(err))
      case Right(player) =>
        addOutput(s"Added player: ${player.fullName} (ID: ${player.id.value}, Club: ${player.clubId}, Birth: ${player.birthyearString})")
        Future(Right(s"FINISHED: ${group}-Test:${number}"))

  /**
   * Test 2: Delete a player. Param: playerId
   */
  def testPlayer_delete(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    try
      val id = PlayerId(param.toInt)
      services.TourneyDB.tourney.deletePlayer(id) match
        case Left(err) =>
          addOutput(s"Error deleting player ID $param: ${err.msg}")
          Future(Left(err))
        case Right(_) =>
          addOutput(s"Deleted (deactivated) player ID $param successfully")
          Future(Right(s"FINISHED: ${group}-Test:${number}"))
    catch
      case _: Exception =>
        addOutput(s"Invalid ID: $param")
        Future(Left(AppError("invalid.id")))

  /**
   * Test 3: Merge two players. Param format: "mainId,mergedId"
   */
  def testPlayer_merge(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val ids = param.split(",")
    if (ids.length != 2) {
      addOutput("Param must be 'mainId,mergedId'")
      return Future(Left(AppError("invalid.param")))
    }

    try
      val mainId = PlayerId(ids(0).trim.toInt)
      val mergedId = PlayerId(ids(1).trim.toInt)
      services.TourneyDB.tourney.mergePlayer(mainId, mergedId) match
        case Left(err) =>
          addOutput(s"Error merging: ${err.msg}")
          Future(Left(err))
        case Right(_) =>
          addOutput(s"Merged successfully: ID $mergedId into ID $mainId")
          Future(Right(s"FINISHED: ${group}-Test:${number}"))
    catch
      case _: Exception =>
        addOutput(s"Invalid IDs in: $param")
        Future(Left(AppError("invalid.id")))

  /**
   * Test 4: List players.
   */
  def testPlayer_list(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val t = services.TourneyDB.tourney
    addOutput(s"Players in DB (${t.players.length}):")
    t.players.foreach { p =>
      addOutput(s"- [${p.id.value}] ${p.fullName} (Club: ${p.clubId}, Active: ${p.active}, MergedInto: ${p.merge.map(_.value).getOrElse("-")})")
    }
    Future.successful(Right(s"FINISHED: ${group}-Test:${number}"))

  /**
   * Test 5: Sync players with server.
   */
  def testPlayer_sync(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    addOutput(s"Syncing players...")
    services.TourneyDB.tourney.syncPlayers()
    Future.successful(Right(s"FINISHED: ${group}-Test:${number}"))
