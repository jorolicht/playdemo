package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.*

import shared.basic.Pickle.*
import shared.basic.AppError
import scala.util.control.NonFatal
import scala.collection.mutable.ArrayBuffer
import shared.model.{Player, PlayerId}
import base.{Global, Logging}

/**
 * PlayerDB provides methods to manage players and synchronize them with the server.
 * It follows the structure of ClubDB with global version-based locking.
 */
object PlayerDB extends ComWrapper with Debouncer:

  /**
   * Triggers a delayed synchronization with the server.
   */
  def triggerSync(all: Seq[Player]): Unit =
    debounce(delay = 800) {
      Logging.debug(s"Synchronisiere Spieler mit dem Server...")
      sync(all)
    }

  var version: Int = 0

  case class PlayerSyncRequest(version: Int, players: Seq[Player]) derives ReadWriter
  case class PlayerSyncResponse(version: Int) derives ReadWriter
  case class PlayersResponse(version: Int, players: Seq[Player]) derives ReadWriter

  /**
   * Initializes the sync handler for the current tournament.
   */
  def initHandler(): Unit =
    TourneyDB.tourney.setPlayerSyncHandler { all => triggerSync(all) }

  /**
   * Synchronizes pending player events with the WordPress server.
   */
  def sync(all: Seq[Player]): Future[Either[AppError, Unit]] = {
    if (base.Global.isDemoMode) {
      val req = PlayerSyncRequest(version, all)
      org.scalajs.dom.window.localStorage.setItem("App.demo_players", write(req))
      Future.successful(Right(()))
    } else if (TourneyDB.tourney.wpId == 0) {
      Future.successful(Right(()))
    } else {
      val route = "/wp-json/tourney/v1/players-sync"
      // Send all players since they are stored in a single meta field
      val req = PlayerSyncRequest(version, all)
      val params = List("postId" -> TourneyDB.tourney.wpId.toString)

      ajaxPost[PlayerSyncRequest, PlayerSyncResponse](
        route,
        params,
        req,
        host = Global.homeUrl
      ).flatMap {
        case Right(res) =>
          version = res.version
          Future.successful(Right(()))
        case Left(err) if err.is("version_mismatch") =>
          Logging.error(s"Sync fehlgeschlagen: Version-Mismatch bei Spielern. Lade neu... ${err.msg}")
          load().map(_ => Left(err))
        case Left(err) =>
          Future.successful(Left(err))
      }
    }
  }

  /**
   * Loads players from the WordPress server.
   */
  def load(): Future[Either[AppError, Long]] = {
    if (base.Global.isDemoMode) {
      val jsStr = org.scalajs.dom.window.localStorage.getItem("App.demo_players")
      if (jsStr != null && jsStr.nonEmpty) {
        val req = read[PlayerSyncRequest](jsStr)
        TourneyDB.tourney.players.clear()
        TourneyDB.tourney.players ++= req.players
        TourneyDB.tourney.dirtyPlayer.clear()
        initHandler()
        version = req.version
        Logging.debug(s"PlayerDB.load(demo): loaded ${req.players.length} players")
      }
      return Future.successful(Right(version.toLong))
    } else if (TourneyDB.tourney.wpId == 0) {
      Logging.debug("PlayerDB.load: tourney.id is 0, skipping load")
      return Future.successful(Right(0L))
    }

    val params = List("postId" -> TourneyDB.tourney.wpId.toString)
    ajaxGet[PlayersResponse]("/wp-json/tourney/v1/players", params, host = Global.homeUrl).map {
      case Right(res) =>
        if (TourneyDB.tourney.wpId != 0) {
          TourneyDB.tourney.players.clear()
          TourneyDB.tourney.players ++= res.players
          TourneyDB.tourney.dirtyPlayer.clear()
          initHandler() // Ensure handler is set
        }
        version = res.version
        Logging.debug(s"PlayerDB.load: loaded ${res.players.length} players, version: $version")
        Right(version.toLong)
      case Left(err) => Left(err)
    }
  }
