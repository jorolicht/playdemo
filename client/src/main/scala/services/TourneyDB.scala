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
   * Loads the basic tournament data first to resolve the WP ID/Slug,
   * then loads competitions, clubs, players, and stages in parallel.
   */
  def init(idOrSlug: Int | String): Future[Either[AppError, Long]] =
    Logging.info(s"Initialisiere TourneyDB für $idOrSlug: Lade alle Daten vom Server...")
    
    // Set internal ID immediately if it's an Int
    idOrSlug match {
      case id: Int => this.tourney = Tourney.default.copy(wpId = id)
      case _ => // slug will be resolved during load
    }

    this.load(idOrSlug).flatMap {
      case Right(version) =>
        val loads = Seq(
          CompetitionDB.load(),
          ClubDB.load(),
          PlayerDB.load(),
          StageDB.load()
        )
        Future.sequence(loads).map { results =>
          val errors = results.collect { case Left(err) => err }
          if (errors.nonEmpty) then
            val combinedMsg = errors.map(_.msgCode).mkString(", ")
            Left(AppError("init.failed", combinedMsg))
          else
            Logging.info("TourneyDB erfolgreich initialisiert.")
            Right(version)
        }
      case Left(err) =>
        Future.successful(Left(err))
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
      ajaxPost[Tourney, TourneyCreateResponse](routeCreate, List(), t, host = Global.homeUrl).map {
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
        req,
        host = Global.homeUrl
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
  def load(idOrSlug: Int | String): Future[Either[AppError, Long]] =
    println("Lade Turnierdaten von Server...")
    if (base.Global.isDemoMode) then
      val jsStr = org.scalajs.dom.window.localStorage.getItem("App.demo_tourney")
      if (jsStr != null && jsStr.nonEmpty) {
        val req = read[TourneySyncRequest](jsStr)
        tourney = req.tourney
        version = req.version
        ClubDB.initHandler() 
        PlayerDB.initHandler()
        CompetitionDB.initHandler()
        StageDB.initHandler()
        Future.successful(Right(version.toLong))
      } else {
        Future.successful(Right(0L))
      }
    else {
      println(s"Loading tournament data for idOrSlug: $idOrSlug")
      val paramsOpt = idOrSlug match {
        case id: Int if id == 0 => 
           Logging.debug("TourneyDB.load: trnyId is 0, skipping load")
           None
        case id: Int => Some(List("postId" -> id.toString))
        case slug: String if slug.isEmpty =>
           Logging.debug("TourneyDB.load: slug is empty, skipping load")
           None
        case slug: String => Some(List("slug" -> slug))
      }

      paramsOpt match {
        case Some(params) =>
          println(s"Loading tournament data with params: $params")
          ajaxGet[TourneyResponse](routeGet, params, host = Global.homeUrl).map {
            case Right(res) =>
              println(s"Successfully loaded tournament data: ${res.tourney}")
              tourney = res.tourney
              println(s"Loaded tournament: ${tourney.name}, version: ${res.version}")
              version = res.version
              println(s"Initializing dependent databases...")
              ClubDB.initHandler() 
              PlayerDB.initHandler()
              CompetitionDB.initHandler()
              StageDB.initHandler()
              Logging.debug(s"TourneyDB.load: tournament loaded, version: $version")
              Right(version.toLong)
            case Left(err) => Left(err)
          }
        case None => Future.successful(Right(0L))
      }
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
      org.scalajs.dom.window.localStorage.removeItem("App.demo_stages")
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
    StageDB.initHandler()
    if (doSync) triggerSync()
