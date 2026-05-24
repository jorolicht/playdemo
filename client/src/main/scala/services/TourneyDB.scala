package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.timers.*

import shared.basic.Pickle.*
import shared.basic.AppError
import scala.util.control.NonFatal
import base.{Global, Logging}
import shared.model.Tourney

/**
 * TourneyDB provides methods to manage tournament data and synchronize it with the server.
 * It uses a global version-based locking mechanism.
 */
object TourneyDB extends ComWrapper with Debouncer:

  /**
   * Triggers a delayed synchronization with the server.
   */
  def triggerSync(): Unit =
    debounce(delay = 800) {
      sync()
    }

  var tourney: Tourney = Tourney.default
  var version: Int = 0



  /**
   * Initializes the application state by loading all data from the server.
   */
  def init(): Future[Either[AppError, Long]] = {
    Logging.info("Initialisiere TourneyDB: Lade alle Daten vom Server...")
    
    // Explicitly define each load to keep track of this.load()
    val loadTourney = this.load()
    val loads = Seq(
      loadTourney,
      CompetitionDB.load(),
      ClubDB.load(),
      PlayerDB.load(),
      RoundDB.load()
    )

    Future.sequence(loads).flatMap { results =>
      val errors = results.collect { case Left(err) => err }
      if (errors.nonEmpty) {
        val combinedMsg = errors.map(_.msgCode).mkString(", ")
        Future.successful(Left(AppError("init.failed", combinedMsg)))
      } else {
        Logging.info("TourneyDB erfolgreich initialisiert.")
        // Return the result of loadTourney
        loadTourney
      }
    }
  }

  case class TourneySyncRequest(version: Int, tourney: Tourney) derives ReadWriter
  case class TourneySyncResponse(version: Int) derives ReadWriter
  case class TourneyResponse(version: Int, tourney: Tourney) derives ReadWriter
  case class TourneyCreateResponse(
    success: Boolean,
    action: String,
    pageId: Int,
    parentId: Int,
    username: String,
    organizer: String,
    slug: String
  ) derives ReadWriter

  private val routeSync   = "/wp-json/tourney/v1/tourney-sync"
  private val routeGet    = "/wp-json/tourney/v1/read"
  private val routeCreate = "/wp-json/tourney/v1/create"

  /**
   * Creates a new tournament post on the server.
   */
  def apiCreate(t: Tourney): Future[Either[AppError, String]] = {
    ajaxPost[Tourney, TourneyCreateResponse](routeCreate, List(), t).map {
      case Right(res) =>
        t.id = res.pageId
        Right(res.slug)
      case Left(err) => Left(err)
    }
  }

  /**
   * Synchronizes pending tourney changes with the WordPress server.
   * If a version mismatch occurs (conflict), it reloads the data from the server.
   */
  def sync(): Future[Either[AppError, Unit]] = {
    val req = TourneySyncRequest(version, tourney)
    val params = List("postId" -> tourney.id.toString)

    ajaxPost[TourneySyncRequest, TourneySyncResponse](
      routeSync,
      params,
      req
    ).flatMap {
      case Right(res) =>
        version = res.version
        Future.successful(Right(()))
      case Left(err) if err.is("version_mismatch") =>
        Logging.error(s"Sync fehlgeschlagen: Version-Mismatch bei Turnierdaten. Lade neu... ${err.msg}")
        load().map(_ => Left(err))
      case Left(err) =>
        Future.successful(Left(err))
    }
  }

  /**
   * Loads tournament data from the WordPress server.
   */
  def load(): Future[Either[AppError, Long]] = {
    if (tourney.id == 0) {
      Logging.debug("TourneyDB.load: tourney.id is 0, skipping load")
      return Future.successful(Right(0L))
    }

    val params = List("postId" -> tourney.id.toString)
    ajaxGet[TourneyResponse](routeGet, params).map {
      case Right(res) =>
        tourney = res.tourney
        version = res.version
        ClubDB.initHandler() 
        PlayerDB.initHandler()
        CompetitionDB.initHandler()
        RoundDB.initHandler()
        Logging.debug(s"TourneyDB.load: tournament loaded, version: $version")
        Right(version.toLong)
      case Left(err) => Left(err)
    }
  }

  /**
   * Updates the current tournament data and triggers sync.
   */
  def update(t: Tourney): Unit =
    tourney = t
    ClubDB.initHandler()
    PlayerDB.initHandler()
    CompetitionDB.initHandler()
    RoundDB.initHandler()
    triggerSync()
