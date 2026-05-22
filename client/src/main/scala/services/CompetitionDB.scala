package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.*

import shared.basic.Pickle.*
import shared.basic.AppError
import scala.util.control.NonFatal
import scala.collection.mutable.ArrayBuffer
import shared.model.{Competition, CompId, CompTyp, CompStatus}
import base.{Global, Logging}

/**
 * CompetitionDB provides methods to manage competitions and synchronize them with the server.
 * It supports up to 64 competitions.
 * Optimistic locking is implemented using a version counter.
 */
object CompetitionDB extends ComWrapper with Debouncer:

  def triggerSync(): Unit =
    debounce(delay = 800) {
      Logging.debug("Synchronisiere Wettbewerbe mit dem Server...")
      sync()
    }

  // Helper to access the current tourney's competitions
  def competitions: ArrayBuffer[Competition] = TourneyDB.tourney.competitions
  def pendingEvents: ArrayBuffer[Competition] = TourneyDB.tourney.dirtyCompetition

  private val route = "/wp-json/tourney/v1/competitions-sync"

  case class CompetitionSyncRequest(events: Seq[Competition]) derives ReadWriter
  case class CompetitionSyncResponse(success: Boolean) derives ReadWriter
  case class CompetitionsResponse(competitions: Seq[Competition]) derives ReadWriter

  /**
   * Initializes the sync handler for the current tournament.
   */
  def initHandler(): Unit =
    if (TourneyDB.tourney.id != 0) {
      TourneyDB.tourney.setCompSyncHandler { _ => triggerSync() }
    }

  /**
   * Synchronizes pending competition events with the WordPress server.
   */
  def sync(): Future[Either[AppError, Unit]] = {
    if pendingEvents.isEmpty || TourneyDB.tourney.id == 0 then
      Future.successful(Right(()))
    else
      val req = CompetitionSyncRequest(pendingEvents.toSeq)
      val params = List("postId" -> TourneyDB.tourney.id.toString)

      ajaxPost[CompetitionSyncRequest, CompetitionSyncResponse](
        route,
        params,
        req
      ).flatMap {
        case Right(res) =>
          pendingEvents.clear()
          Future.successful(Right(()))
        case Left(err) if err.is("version_mismatch") =>
          Logging.error(s"Sync fehlgeschlagen: Version-Mismatch bei Wettbewerben. Lade neu... ${err.msg}")
          pendingEvents.clear()
          load().map(_ => Left(err))
        case Left(err) =>
          Future.successful(Left(err))
      }
  }

  /**
   * Loads competitions from the WordPress server.
   */
  def load(): Future[Either[AppError, Long]] = {
    if (TourneyDB.tourney.id == 0) {
      Logging.debug("CompetitionDB.load: tourney.id is 0, skipping load")
      return Future.successful(Right(0L))
    }

    val params = List("postId" -> TourneyDB.tourney.id.toString)
    ajaxGet[CompetitionsResponse]("/wp-json/tourney/v1/competitions", params).map {
      case Right(res) =>
        if (TourneyDB.tourney.id != 0) {
          val t = TourneyDB.tourney
          t.competitions.clear()
          for (i <- 0 until 64) t.competitions += null
          res.competitions.foreach { c =>
             val i = c.id.value - 1
             if (i >= 0 && i < 64) then
               t.competitions(i) = c
          }
          t.dirtyCompetition.clear()
          initHandler() // Ensure handler is set
        }
        Logging.debug(s"CompetitionDB.load: loaded ${res.competitions.length} competitions")
        Right(0L)
      case Left(err) => 
        Logging.error(s"CompetitionDB.load: failed to load competitions: ${err.msgCode}")
        Left(err)
    }
  }
