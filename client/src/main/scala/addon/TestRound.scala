package addon

import shared.model.*
import shared.basic.*
import services.{TourneyDB, RoundDB}
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/**
 * TestRound provides addon console commands to test Round functionality.
 */
object TestRound:

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match
      case 1 => testRound_add(group, number, param)
      case 2 => testRound_delete(group, number, param)
      case 3 => testRound_list(group, number, param)
      case 4 => testRound_sync(group, number, param)
      case _ =>
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknown test number")))

  /**
   * Test 1: Add a round. Param format: "coId,name,size,noPlayers[,prefId]"
   */
  def testRound_add(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val parts = param.split(",")
    if (parts.length < 4) {
      addOutput("Param must be 'coId,name,size,noPlayers[,prefId]'")
      return Future(Left(AppError("invalid.param")))
    }

    try
      val coId = CompId.fromInt(parts(0).trim.toInt)
      val name = parts(1).trim
      val size = parts(2).trim.toInt
      val noPlayers = parts(3).trim.toInt
      val prefId = if (parts.length > 4) Some(RoundId.fromInt(parts(4).trim.toInt)) else None
      val rndCfg = RoundCfg.VRGR // Default for test

      TourneyDB.tourney.addRound(coId, prefId, name, rndCfg, size, noPlayers) match
        case Left(err) =>
          addOutput(s"Error adding round '$name': ${err.msg}")
          Future(Left(err))
        case Right(r) =>
          addOutput(s"Added round: ${r.name} (ID: ${r.id.value}, CompID: ${r.coId.value}, Version: ${r.version})")
          Future(Right(s"FINISHED: ${group}-Test:${number}"))
    catch
      case _: Exception =>
        addOutput(s"Invalid parameters: $param")
        Future(Left(AppError("invalid.param")))

  /**
   * Test 2: Delete a round. Param: roundId
   */
  def testRound_delete(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    try
      val id = RoundId.fromInt(param.toInt)
      TourneyDB.tourney.deleteRound(id) match
        case Left(err) =>
          addOutput(s"Error deleting round ID $param: ${err.msg}")
          Future(Left(err))
        case Right(_) =>
          addOutput(s"Deleted (soft delete) round ID $param successfully")
          Future(Right(s"FINISHED: ${group}-Test:${number}"))
    catch
      case _: Exception =>
        addOutput(s"Invalid ID: $param")
        Future(Left(AppError("invalid.id")))

  /**
   * Test 3: List rounds.
   */
  def testRound_list(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val activeRounds = TourneyDB.tourney.rounds.filter(r => r != null)
    addOutput(s"Rounds in DB (${activeRounds.length}):")
    activeRounds.foreach { r =>
      addOutput(s"- [${r.id.value}] ${r.name} (CompID: ${r.coId.value}, Size: ${r.size}, Players: ${r.noPlayers}, Deleted: ${r.deleted}, Version: ${r.version})")
    }
    Future(Right(s"FINISHED: ${group}-Test:${number}"))

  /**
   * Test 4: Sync rounds with server.
   */
  def testRound_sync(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    addOutput(s"Syncing rounds...")
    TourneyDB.tourney.syncRounds()
    Future.successful(Right(s"FINISHED: ${group}-Test:${number}"))
