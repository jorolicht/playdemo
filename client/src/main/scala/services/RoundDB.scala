package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.*

import shared.basic.Pickle.*
import shared.basic.AppError
import scala.util.control.NonFatal
import scala.collection.mutable.ArrayBuffer
import shared.model.{Round, RoundId, CompId, RoundCfg, RoundStatus}
import base.{Global, Logging}

/**
 * RoundDB provides methods to manage rounds and synchronize them with the WordPress server.
 * It supports up to 128 rounds.
 * Rounds are stored in custom post type meta fields round001 to round128.
 * Optimistic locking is implemented using a version counter.
 */
object RoundDB extends ComWrapper with Debouncer:

  def triggerSync(dirty: Seq[Round]): Unit =
    debounce(delay = 800) {
      Logging.debug(s"Synchronisiere ${dirty.length} Runden mit dem Server...")
      sync(dirty)
    }

  // Helper to access the current tourney's rounds
  def rounds: ArrayBuffer[Round] = TourneyDB.tourney.rounds

  private val route = "/wp-json/tourney/v1/rounds-sync"

  case class RoundSyncRequest(events: Seq[Round]) derives ReadWriter
  case class RoundSyncResponse(success: Boolean) derives ReadWriter
  case class RoundsResponse(rounds: Seq[Round]) derives ReadWriter

  /**
   * Initializes the sync handler for the current tournament.
   */
  def initHandler(): Unit =
    TourneyDB.tourney.setRoundSyncHandler { dirty => triggerSync(dirty) }

  /**
   * Synchronizes pending round events with the WordPress server.
   */
  def sync(dirty: Seq[Round]): Future[Either[AppError, Unit]] = {
    if (dirty.isEmpty || TourneyDB.tourney.id == 0) {
      Future.successful(Right(()))
    } else {
      val req = RoundSyncRequest(dirty)
      val params = List("postId" -> TourneyDB.tourney.id.toString)

      ajaxPost[RoundSyncRequest, RoundSyncResponse](
        route,
        params,
        req
      ).flatMap {
        case Right(res) =>
          Future.successful(Right(()))
        case Left(err) if err.is("version_mismatch") =>
          Logging.error(s"Sync fehlgeschlagen: Version-Mismatch bei Runden. Lade neu... ${err.msg}")
          load().map(_ => Left(err))
        case Left(err) =>
          Future.successful(Left(err))
      }
    }
  }

  /**
   * Loads rounds from the WordPress server.
   */
  def load(): Future[Either[AppError, Long]] = {
    if (TourneyDB.tourney.id == 0) {
      Logging.debug("RoundDB.load: tourney.id is 0, skipping load")
      return Future.successful(Right(0L))
    }

    val params = List("postId" -> TourneyDB.tourney.id.toString)
    ajaxGet[RoundsResponse]("/wp-json/tourney/v1/rounds", params).map {
      case Right(res) =>
        if (TourneyDB.tourney.id != 0) {
          val t = TourneyDB.tourney
          t.rounds.clear()
          for (i <- 0 until 128) t.rounds += null
          res.rounds.foreach { r =>
             val i = r.id.value - 1
             if (i >= 0 && i < 128) then
               t.rounds(i) = r
          }
          t.dirtyRound.clear()
          initHandler() // Ensure handler is set
        }
        Logging.debug(s"RoundDB.load: loaded ${res.rounds.length} rounds")
        Right(0L)
      case Left(err) => Left(err)
    }
  }
