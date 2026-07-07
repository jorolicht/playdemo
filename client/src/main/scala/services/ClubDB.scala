package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.*

import shared.basic.Pickle.*
import shared.basic.AppError
import scala.util.control.NonFatal
import scala.collection.mutable.{ ArrayBuffer, Map }
import shared.model.ClubId
import shared.model.Club
import base.{ Global, Logging }



object ClubDB extends ComWrapper with Debouncer:

  def triggerSync(all: Seq[Club]): Unit =
    debounce(delay = 800) {
      Logging.debug(s"Synchronisiere Vereine mit dem Server...")
      sync(all)
    }

  var version: Int = 0

  case class ClubSyncRequest(version: Int, clubs: Seq[Club]) derives ReadWriter
  case class ClubSyncResponse(version: Int) derives ReadWriter
  case class ClubsResponse(version: Int, clubs: Seq[Club]) derives ReadWriter

  /**
   * Initializes the sync handler for the current tournament.
   */
  def initHandler(): Unit =
    TourneyDB.tourney.setSyncHandler { all => triggerSync(all) }


  def sync(all: Seq[Club]): Future[Either[AppError, Unit]] = {
    if (base.Global.isDemoMode) {
      val req = ClubSyncRequest(version, all)
      org.scalajs.dom.window.localStorage.setItem("App.demo_clubs", write(req))
      Future.successful(Right(()))
    } else if (TourneyDB.tourney.wpId == 0) {
      Future.successful(Right(()))
    } else {
      val route = "/wp-json/tourney/v1/clubs-sync"
      // Send all clubs since they are stored in a single meta field
      val req = ClubSyncRequest(version, all)
      val params = List("postId" -> TourneyDB.tourney.wpId.toString)

      ajaxPost[ClubSyncRequest, ClubSyncResponse](
        route,
        params,
        req,
        host = Global.homeUrl
      ).flatMap {
        case Right(res) =>
          version = res.version
          Future.successful(Right(()))
        case Left(err) if err.is("version_mismatch") =>
          Logging.error(s"Sync fehlgeschlagen: Version-Mismatch bei Vereinen. Lade neu... ${err.msg}")
          load().map(_ => Left(err))
        case Left(err) =>
          Future.successful(Left(err))
      }
    }
  }

  def load(): Future[Either[AppError, Long]] = {
    if (base.Global.isDemoMode) {
      val jsStr = org.scalajs.dom.window.localStorage.getItem("App.demo_clubs")
      if (jsStr != null && jsStr.nonEmpty) {
        val req = read[ClubSyncRequest](jsStr)
        TourneyDB.tourney.clubs.clear()
        TourneyDB.tourney.clubs ++= req.clubs
        TourneyDB.tourney.dirtyClubs.clear()
        initHandler()
        version = req.version
        Logging.debug(s"ClubDB.load(demo): loaded ${req.clubs.length} clubs")
      }
      return Future.successful(Right(version.toLong))
    } else if (TourneyDB.tourney.wpId == 0) {
      Logging.debug("ClubDB.load: tourney.id is 0, skipping load")
      return Future.successful(Right(0L))
    }

    val params = List("postId" -> TourneyDB.tourney.wpId.toString)
    ajaxGet[ClubsResponse](s"/wp-json/tourney/v1/clubs", params, host = Global.homeUrl).map {
      case Right(res) =>
        if (TourneyDB.tourney.wpId != 0) {
          TourneyDB.tourney.clubs.clear()
          TourneyDB.tourney.clubs ++= res.clubs
          TourneyDB.tourney.dirtyClubs.clear()
          initHandler() // Ensure handler is set
        }
        version = res.version
        Logging.debug(s"ClubDB.load: loaded ${res.clubs.length} clubs, version: $version")
        Right(version.toLong)
      case Left(err) => Left(err)
    }
  }