package addon

import shared.model.*
import shared.basic.*
import shared.format.*
import services.{TourneyDB, StageDB}
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.dom.raw.HTMLElement
import shared.DomTypes.HtmlId

/**
 * TestStage provides addon console commands to test Stage functionality.
 */
object TestStage extends base.JsWrapper:

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match
      case 1 => testStage_add(group, number, param)
      case 2 => testStage_delete(group, number, param)
      case 3 => testStage_list(group, number, param)
      case 4 => testStage_sync(group, number, param)
      case 5 => testStage_referee(group, number, param)
      case 6 => testStage_swapPairing(group, number, param)
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

  /**
   * Test 5: Generate referee cards for a stage. Param: stageId
   */
  def testStage_referee(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    try
      val activeStages = TourneyDB.tourney.stages.filter(r => r != null)
      if (activeStages.isEmpty) {
        addOutput("No active stages found in the tournament to generate referee cards for.")
        return Future(Left(AppError("no.stages.found")))
      }
      
      val stageOpt = if (param.trim.isEmpty) {
        val defaultStage = activeStages.head
        addOutput(s"No Stage ID specified in --param. Using default Stage: '${defaultStage.name}' (ID: ${defaultStage.id.value}).")
        Some(defaultStage)
      } else {
        val stageId = StageId.fromInt(param.trim.toInt)
        activeStages.find(_.id == stageId)
      }
      
      stageOpt match {
        case Some(stage) =>
          val parentId = HtmlId(s"RefereeContent_${stage.coId.value}_${stage.id.value}")
          val elem = eE[HTMLElement](parentId, "div")
          elem.style.display = "block"
          
          addOutput(s"Generating referee page for Stage ${stage.name} (ID: ${stage.id.value})...")
          pages.Stage.StageScoreSheet.setPage(stage)
          
          addOutput(s"SUCCESS: Generated referee cards in #${parentId.id}")
          Future.successful(Right(s"FINISHED: ${group}-Test:${number}"))
        case None =>
          addOutput(s"Stage with ID $param not found")
          Future(Left(AppError("stage.not.found")))
      }
    catch
      case ex: Exception =>
        addOutput(s"Error executing referee test: ${ex.getClass.getName}: ${ex.getMessage}")
        ex.printStackTrace()
        Future(Left(AppError("referee.test.failed", ex.getMessage)))

  /**
   * Test 6: Test Swiss pairing swapping. Param: stageId
   */
  def testStage_swapPairing(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    try
      val stageId = StageId.fromInt(param.trim.toInt)
      val stageOpt = TourneyDB.tourney.stages.filter(_ != null).find(_.id == stageId)
      stageOpt match
        case Some(stage) =>
          stage.data match
            case StageData.SwissStage(sw) =>
              addOutput(s"Testing Swiss pairing swap for stage: ${stage.name}")
              addOutput(s"Initial swPants order: ${sw.swPants.map(_.sno).mkString(", ")}")
              
              // 1. Test swapPlayers (Round 1 swap)
              if (sw.swPants.length >= 2) {
                val sno1 = sw.swPants(0).sno
                val sno2 = sw.swPants(1).sno
                addOutput(s"Swapping players: $sno1 and $sno2 in swPants")
                SwissSys.swapPlayers(stage, sno1, sno2)
                addOutput(s"New swPants order: ${sw.swPants.map(_.sno).mkString(", ")}")
              }
              
              // 2. Test swapPairing (Round >= 2 swap)
              while (sw.pairing.length < 2) {
                sw.pairing += scala.collection.mutable.ArrayBuffer.empty[SwissPair]
              }
              val p1A = 0
              val p1B = 1
              val p2A = 2
              val p2B = 3
              if (sw.swPants.length >= 4) {
                sw.pairing(1).clear()
                sw.pairing(1) += SwissPair((p1A, p1B), (0,0), (0,0))
                sw.pairing(1) += SwissPair((p2A, p2B), (0,0), (0,0))
                addOutput(s"Initial Round 2 Pairings: (${sw.swPants(p1A).sno} vs ${sw.swPants(p1B).sno}), (${sw.swPants(p2A).sno} vs ${sw.swPants(p2B).sno})")
                
                val snoA = sw.swPants(p1A).sno
                val snoB = sw.swPants(p2A).sno
                addOutput(s"Swapping pairings for $snoA and $snoB in Round 2")
                SwissSys.swapPairing(stage, 2, snoA, snoB)
                
                val newP1A = sw.pairing(1)(0).id._1
                val newP1B = sw.pairing(1)(0).id._2
                val newP2A = sw.pairing(1)(1).id._1
                val newP2B = sw.pairing(1)(1).id._2
                addOutput(s"New Round 2 Pairings: (${sw.swPants(newP1A).sno} vs ${sw.swPants(newP1B).sno}), (${sw.swPants(newP2A).sno} vs ${sw.swPants(newP2B).sno})")
              }
              
              Future.successful(Right(s"FINISHED: ${group}-Test:${number}"))
            case _ =>
              addOutput("Stage is not a Swiss stage.")
              Future(Left(AppError("not.swiss.stage")))
        case None =>
          addOutput(s"Stage with ID $param not found")
          Future(Left(AppError("stage.not.found")))
    catch
      case ex: Exception =>
        addOutput(s"Error: ${ex.getMessage}")
        Future(Left(AppError("test.failed", ex.getMessage)))
