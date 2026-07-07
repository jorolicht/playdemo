package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.*

import shared.basic.Pickle.*
import shared.basic.AppError
import scala.util.control.NonFatal
import scala.collection.mutable.ArrayBuffer
import shared.model.{Stage, StageId, CompId, StageConfig, StageStatus}
import base.{Global, Logging}

/**
 * StageDB provides methods to manage stages and synchronize them with the WordPress server.
 * It supports up to 128 stages.
 * Stages are stored in custom post type meta fields stage001 to stage128.
 * Optimistic locking is implemented using a version counter.
 */
object StageDB extends ComWrapper with Debouncer:

  def triggerSync(dirty: Seq[Stage]): Unit =
    debounce(delay = 800) {
      Logging.debug(s"Synchronisiere ${dirty.length} Stages mit dem Server...")
      sync(dirty)
    }
  // Helper to access the current tourney's stages
  def stages: ArrayBuffer[Stage] = TourneyDB.tourney.stages

  private val route = "/wp-json/tourney/v1/stages-sync"

  case class StageSyncRequest(events: Seq[Stage]) derives ReadWriter
  case class StageSyncResponse(success: Boolean) derives ReadWriter
  case class StagesResponse(stages: Seq[Stage]) derives ReadWriter

  /**
   * Initializes the sync handler for the current tournament.
   */
  def initHandler(): Unit =
    TourneyDB.tourney.setStageSyncHandler { dirty => triggerSync(dirty) }

  /**
   * Synchronizes pending stage events with the WordPress server.
   */
  def sync(dirty: Seq[Stage]): Future[Either[AppError, Unit]] = {
    if (base.Global.isDemoMode) {
      val req = StageSyncRequest(TourneyDB.tourney.stages.filter(_ != null).toSeq)
      org.scalajs.dom.window.localStorage.setItem("App.demo_stages", write(req))
      Future.successful(Right(()))
    } else if (dirty.isEmpty || TourneyDB.tourney.wpId == 0) {
      Future.successful(Right(()))
    } else {
      val req = StageSyncRequest(dirty)
      val params = List("postId" -> TourneyDB.tourney.wpId.toString)

      ajaxPost[StageSyncRequest, StageSyncResponse](
        route,
        params,
        req,
        host = Global.homeUrl
      ).flatMap {
        case Right(res) =>
          Future.successful(Right(()))
        case Left(err) if err.is("version_mismatch") =>
          Logging.error(s"Sync fehlgeschlagen: Version-Mismatch bei Stages. Lade neu... ${err.msg}")
          load().map(_ => Left(err))
        case Left(err) =>
          Future.successful(Left(err))
      }
    }
  }

  /**
   * Loads stages from the WordPress server.
   */
  def load(): Future[Either[AppError, Long]] = {
    if (base.Global.isDemoMode) {
      val jsStr = org.scalajs.dom.window.localStorage.getItem("App.demo_stages")
      if (jsStr != null && jsStr.nonEmpty) {
        val req = read[StageSyncRequest](jsStr)
        val t = TourneyDB.tourney
        t.stages.clear()
        for (i <- 0 until 128) t.stages += null
        req.events.foreach { r =>
           val i = r.id.value - 1
           if (i >= 0 && i < 128) then
             t.stages(i) = r
        }
        t.dirtyStage.clear()
        initHandler()
        Logging.debug(s"StageDB.load(demo): loaded ${req.events.length} stages")
      }
      return Future.successful(Right(0L))
    } else if (TourneyDB.tourney.wpId == 0) {
      Logging.debug("StageDB.load: tourney.id is 0, skipping load")
      return Future.successful(Right(0L))
    }

    val params = List("postId" -> TourneyDB.tourney.wpId.toString)
    ajaxGet[StagesResponse]("/wp-json/tourney/v1/stages", params, host = Global.homeUrl).map {
      case Right(res) =>
        if (TourneyDB.tourney.wpId != 0) {
          val t = TourneyDB.tourney
          t.stages.clear()
          for (i <- 0 until 128) t.stages += null
          res.stages.foreach { r =>
             val i = r.id.value - 1
             if (i >= 0 && i < 128) then
               t.stages(i) = r
          }
          t.dirtyStage.clear()
          initHandler() // Ensure handler is set
        }
        Logging.debug(s"StageDB.load: loaded ${res.stages.length} stages")
        Right(0L)
      case Left(err) => Left(err)
    }
  }
