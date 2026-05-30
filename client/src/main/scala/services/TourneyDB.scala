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
  def init(tourneyId: Int): Future[Either[AppError, Long]] =
    Logging.info(s"Initialisiere TourneyDB für ID $tourneyId: Lade alle Daten vom Server...")
    
    // Set internal ID immediately to provide context for other loaders
    this.tourney = Tourney.default.copy(id = tourneyId)

    // Explicitly define each load to keep track of this.load()
    val loadTourney = this.load(tourneyId)
    val loads = Seq(
      loadTourney,
      CompetitionDB.load(),
      ClubDB.load(),
      PlayerDB.load(),
      RoundDB.load()
    )

    Future.sequence(loads).flatMap { results =>
      val errors = results.collect { case Left(err) => err }
      if (errors.nonEmpty) then
        val combinedMsg = errors.map(_.msgCode).mkString(", ")
        Future.successful(Left(AppError("init.failed", combinedMsg)))
      else
        Logging.info("TourneyDB erfolgreich initialisiert.")
        // Return the result of loadTourney
        loadTourney
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
    slug: String,
    version: Int
  ) derives ReadWriter

  private val routeSync   = "/wp-json/tourney/v1/tourney-sync"
  private val routeGet    = "/wp-json/tourney/v1/read"
  private val routeCreate = "/wp-json/tourney/v1/create"

  /**
   * Creates a new tournament post on the server.
   */
  def apiCreate(t: Tourney): Future[Either[AppError, String]] =
    ajaxPost[Tourney, TourneyCreateResponse](routeCreate, List(), t).map {
      case Right(res) =>
        t.id = res.pageId
        t.version = res.version
        this.version = res.version
        Right(res.slug)
      case Left(err) => Left(err)
    }

  /**
   * Synchronizes pending tourney changes with the WordPress server.
   * If a version mismatch occurs (conflict), it reloads the data from the server.
   */
  def sync(): Future[Either[AppError, Unit]] =
    if (tourney.id == 0) then
      Future.successful(Right(()))
    else
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
          load(tourney.id).map(_ => Left(err))
        case Left(err) =>
          Future.successful(Left(err))
      }

  /**
   * Loads basic tournament data from the WordPress server.
   */
  def load(trnyId: Int): Future[Either[AppError, Long]] =
    if (trnyId == 0) then
      Logging.debug("TourneyDB.load: trnyId is 0, skipping load")
      Future.successful(Right(0L))
    else
      val params = List("postId" -> trnyId.toString)
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

  /**
   * Updates the current tournament data and optionally triggers sync.
   */
  def update(t: Tourney, doSync: Boolean = true): Unit =
    tourney = t
    ClubDB.initHandler()
    PlayerDB.initHandler()
    CompetitionDB.initHandler()
    RoundDB.initHandler()
    if (doSync) triggerSync()
