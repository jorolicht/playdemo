package addon

import shared.model.*
import shared.basic.*
import services.{TourneyDB, StageDB}
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/**
 * TestStage provides addon console commands to test Stage functionality.
 */
object TestStage:

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match
      case 1 => testStage_add(group, number, param)
      case 2 => testStage_delete(group, number, param)
      case 3 => testStage_list(group, number, param)
      case 4 => testStage_sync(group, number, param)
      case _ =>
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknown test number")))

  /**
   * Test 1: Add a stage. Param format: "coId,name,size,noPlayers[,prefId]"
   */
  def testStage_add(group: String, number: Int, param: String): Future[Either[AppError, String]] =
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
      val prefId = if (parts.length > 4) Some(StageId.fromInt(parts(4).trim.toInt)) else None
      val stageConfig = StageConfig.VRGR // Default for test

      TourneyDB.tourney.addStage(coId, prefId, name, stageConfig, size, noPlayers) match
        case Left(err) =>
          addOutput(s"Error adding stage '$name': ${err.msg}")
          Future(Left(err))
        case Right(r) =>
          addOutput(s"Added stage: ${r.name} (ID: ${r.id.value}, CompID: ${r.coId.value}, Version: ${r.version})")
          Future(Right(s"FINISHED: ${group}-Test:${number}"))
    catch
      case _: Exception =>
        addOutput(s"Invalid parameters: $param")
        Future(Left(AppError("invalid.param")))

  /**
   * Test 2: Delete a stage. Param: stageId
   */
  def testStage_delete(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    try
      val id = StageId.fromInt(param.toInt)
      TourneyDB.tourney.deleteStage(id) match
        case Left(err) =>
          addOutput(s"Error deleting stage ID $param: ${err.msg}")
          Future(Left(err))
        case Right(_) =>
          addOutput(s"Deleted (soft delete) stage ID $param successfully")
          Future(Right(s"FINISHED: ${group}-Test:${number}"))
    catch
      case _: Exception =>
        addOutput(s"Invalid ID: $param")
        Future(Left(AppError("invalid.id")))

  /**
   * Test 3: List stages.
   */
  def testStage_list(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val activeStages = TourneyDB.tourney.stages.filter(r => r != null)
    addOutput(s"Stages in DB (${activeStages.length}):")
    activeStages.foreach { r =>
      addOutput(s"- [${r.id.value}] ${r.name} (CompID: ${r.coId.value}, Size: ${r.size}, Players: ${r.noPlayers}, Deleted: ${r.deleted}, Version: ${r.version})")
    }
    Future(Right(s"FINISHED: ${group}-Test:${number}"))

  /**
   * Test 4: Sync stages with server.
   */
  def testStage_sync(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    addOutput(s"Syncing stages...")
    TourneyDB.tourney.syncStages()
    Future.successful(Right(s"FINISHED: ${group}-Test:${number}"))
