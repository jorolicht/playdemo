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
    this.tourney = Tourney.default.copy(wpId = tourneyId)

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
  private val routeDelete = "/wp-json/tourney/v1/delete"

  /**
   * Creates a new tournament post on the server.
   */
  def apiCreate(t: Tourney): Future[Either[AppError, String]] =
    if (base.Global.isDemoMode) then
      t.wpId = 999999
      val req = TourneySyncRequest(1, t)
      org.scalajs.dom.window.localStorage.setItem("App.demo_tourney", write(req))
      Future.successful(Right("demo-slug"))
    else
      ajaxPost[Tourney, TourneyCreateResponse](routeCreate, List(), t).map {
        case Right(res) =>
          t.wpId = res.pageId
          t.version = res.version
          t.slug = res.slug
          this.version = res.version
          Right(res.slug)
        case Left(err) => Left(err)
      }

  /**
   * Synchronizes pending tourney changes with the WordPress server.
   * If a version mismatch occurs (conflict), it reloads the data from the server.
   */
  def sync(): Future[Either[AppError, Unit]] =
    if (base.Global.isDemoMode) then
      val req = TourneySyncRequest(version, tourney)
      org.scalajs.dom.window.localStorage.setItem("App.demo_tourney", write(req))
      Future.successful(Right(()))
    else if (tourney.wpId == 0) then
      Future.successful(Right(()))
    else
      val req = TourneySyncRequest(version, tourney)
      val params = List("postId" -> tourney.wpId.toString)

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
          load(tourney.wpId).map(_ => Left(err))
        case Left(err) =>
          Future.successful(Left(err))
      }

  /**
   * Loads basic tournament data from the WordPress server.
   */
  def load(trnyId: Int): Future[Either[AppError, Long]] =
    if (base.Global.isDemoMode) then
      val jsStr = org.scalajs.dom.window.localStorage.getItem("App.demo_tourney")
      if (jsStr != null && jsStr.nonEmpty) {
        val req = read[TourneySyncRequest](jsStr)
        tourney = req.tourney
        version = req.version
        ClubDB.initHandler() 
        PlayerDB.initHandler()
        CompetitionDB.initHandler()
        RoundDB.initHandler()
        Future.successful(Right(version.toLong))
      } else {
        Future.successful(Right(0L))
      }
    else if (trnyId == 0) then
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
   * Deletes the current tournament from the WordPress server.
   */
  def apiDelete(trnyId: Int): Future[Either[AppError, Unit]] =
    if (base.Global.isDemoMode) then
      org.scalajs.dom.window.localStorage.removeItem("App.demo_tourney")
      org.scalajs.dom.window.localStorage.removeItem("App.demo_clubs")
      org.scalajs.dom.window.localStorage.removeItem("App.demo_players")
      org.scalajs.dom.window.localStorage.removeItem("App.demo_comps")
      org.scalajs.dom.window.localStorage.removeItem("App.demo_rounds")
      Future.successful(Right(()))
    else if (trnyId == 0) then
      Future.successful(Right(()))
    else
      val params = List("postId" -> trnyId.toString)
      ajaxDelete[Unit](routeDelete, params).map {
        case Right(_) => 
          Logging.info(s"Tournament $trnyId deleted from server.")
          Right(())
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
