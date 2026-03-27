package addon

import shared.model.*
import shared.basic.*
import services.RoundDB
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/**
 * TestRound provides addon console commands to test RoundDB functionality.
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
   * Test 1: Add a round. Param format: "compId,prefId,name,rndCfg,size,noPlayers"
   * Example: "1,,Round 1,VRGR,8,8" (prefId is empty for startRound)
   * Example: "1,1,Round 2,KO,4,4" (prefId is 1)
   */
  def testRound_add(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val parts = param.split(",")
    if (parts.length < 6) {
      addOutput("Param must be 'compId,prefId,name,rndCfg,size,noPlayers'")
      return Future(Left(AppError("invalid.param")))
    }

    try
      val coId = CompId.fromInt(parts(0).trim.toInt)
      val prefId = if (parts(1).trim.isEmpty) None else Some(RoundId.fromInt(parts(1).trim.toInt))
      val name = parts(2).trim
      val rndCfgStr = parts(3).trim
      val size = parts(4).trim.toInt
      val noPlayers = parts(5).trim.toInt
      
      val rndCfg = RoundCfg.values.find(_.toString == rndCfgStr).getOrElse(RoundCfg.VRGR)

      RoundDB.addRound(coId, prefId, name, rndCfg, size, noPlayers) match
        case Left(err) =>
          addOutput(s"Error adding round '$name': ${err.msg}")
          Future(Left(err))
        case Right(r) =>
          addOutput(s"Added round: ${r.name} (ID: ${r.id.value}, Comp: ${r.coId.value}, Pref: ${r.prefId.map(_.value).getOrElse("None")})")
          Future(Right(s"FINISHED: ${group}-Test:${number}"))
    catch
      case _: Exception =>
        addOutput(s"Invalid parameters in: $param")
        Future(Left(AppError("invalid.param")))

  /**
   * Test 2: Delete a round. Param: roundId
   */
  def testRound_delete(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    try
      val id = RoundId.fromInt(param.toInt)
      RoundDB.deleteRound(id) match
        case Left(err) =>
          addOutput(s"Error deleting round ID $param: ${err.msg}")
          Future(Left(err))
        case Right(_) =>
          addOutput(s"Deleted round (and successors) ID: $param")
          Future(Right(s"FINISHED: ${group}-Test:${number}"))
    catch
      case _: Exception =>
        addOutput(s"Invalid ID: $param")
        Future(Left(AppError("invalid.id")))

  /**
   * Test 3: List rounds.
   */
  def testRound_list(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val activeRounds = RoundDB.rounds.filter(r => r != null)
    addOutput(s"Rounds in DB (${activeRounds.length}):")
    activeRounds.foreach { r =>
      addOutput(s"- [${r.id.value}] ${r.name} (Comp: ${r.coId.value}, Pref: ${r.prefId.map(_.value).getOrElse("-")}, Next: ${r.nextIds.map(_.value).mkString(",")}, Deleted: ${r.deleted})")
    }
    Future(Right(s"FINISHED: ${group}-Test:${number}"))

  /**
   * Test 4: Sync rounds with server.
   */
  def testRound_sync(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    addOutput(s"Syncing rounds (${RoundDB.pendingEvents.length} pending events)...")
    RoundDB.sync().map {
      case Left(err) =>
        addOutput(s"Error syncing rounds: ${err.msg}")
        Left(err)
      case Right(_) =>
        addOutput(s"Rounds synced successfully. New timestamp: ${RoundDB.timestamp}")
        Right(s"FINISHED: ${group}-Test:${number}")
    }
