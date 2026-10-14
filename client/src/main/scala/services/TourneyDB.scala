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
    
    // Set internal ID immediately if it's an Int or local
    idOrSlug match {
      case id: Int => this.tourney = Tourney.default.copy(wpId = id)
      case s: String if s == "local" || s == "999999" => this.tourney = Tourney.default.copy(wpId = 999999)
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
    if (base.Global.isDemoMode || base.Global.isLocalMode) then
      t.wpId = 999999
      base.Global.isLocalMode = true
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
          Global.tourneySlug = res.slug
          Right(res.slug)
        case Left(err) => Left(err)
      }

  /**
   * Synchronizes pending tourney changes with the WordPress server.
   * If a version mismatch occurs (conflict), it reloads the data from the server.
   */
  def sync(): Future[Either[AppError, Unit]] =
    if (base.Global.isDemoMode || base.Global.isLocalMode || tourney.wpId == 999999) then
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
    val isDemoOrLocal = base.Global.isDemoMode || base.Global.isLocalMode || idOrSlug == 999999 || idOrSlug == "999999" || idOrSlug == "local"
    if (isDemoOrLocal) then
      val jsStr = org.scalajs.dom.window.localStorage.getItem("App.demo_tourney")
      if (jsStr != null && jsStr.nonEmpty) {
        val req = read[TourneySyncRequest](jsStr)
        tourney = req.tourney
        version = req.version
        ClubDB.initHandler() 
        PlayerDB.initHandler()
        CompetitionDB.initHandler()
        StageDB.initHandler()
        // Load demo ACI
        val demoWpId = req.tourney.wpId
        val localData = org.scalajs.dom.window.localStorage.getItem("App.demo_aci_" + demoWpId)
        if (localData != null && localData.nonEmpty) {
          try {
            base.Global.currentAci = read[shared.model.ACI](localData)
          } catch {
            case _: Exception => base.Global.currentAci = shared.model.ACI()
          }
        } else {
          base.Global.currentAci = shared.model.ACI()
        }
        Future.successful(Right(version.toLong))
      } else {
        Future.successful(Right(0L))
      }
    else {
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
          ajaxGet[TourneyResponse](routeGet, params, host = Global.homeUrl).flatMap {
            case Right(res) =>
              tourney = res.tourney
              version = res.version
              if (!Global.isLocalMode && res.tourney.slug.nonEmpty) {
                Global.tourneySlug = res.tourney.slug
              }
              ClubDB.initHandler() 
              PlayerDB.initHandler()
              CompetitionDB.initHandler()
              StageDB.initHandler()
              Logging.debug(s"TourneyDB.load: tournament loaded, version: $version")
              // Load ACI asynchronously
              loadAci(res.tourney.wpId).map { _ =>
                Right(version.toLong)
              }
            case Left(err) => Future.successful(Left(err))
          }
        case None => Future.successful(Right(0L))
      }
    }

  /**
   * Checks whether a local tournament currently exists in browser local storage or active memory.
   *
   * @return True if a local tournament is present, false otherwise.
   */
  def hasLocalTourney: Boolean = {
    try {
      val local = org.scalajs.dom.window.localStorage
      val raw = if (local != null) local.getItem("App.demo_tourney") else null
      val hasStorage = raw != null && raw.trim.nonEmpty && !raw.trim.equals("{}")
      val hasMemory = (base.Global.isLocalMode || base.Global.isDemoMode || tourney.wpId == 999999) &&
                      (tourney.name.nonEmpty || base.Global.currentSelection.tourney.exists(_.name.nonEmpty))
      hasStorage || hasMemory
    } catch {
      case _: Exception => false
    }
  }

  /**
   * Thoroughly purges all tournament data, related database caches, and session/local storage keys
   * from the browser storage and resets tournament state in memory.
   *
   * @param wpId Optional WordPress Post ID to clean specific ACI storage key.
   */
  def purgeAllTourneyStorage(wpId: Int = 0): Unit = {
    tourney = shared.model.Tourney.default
    base.Global.currentSelection = shared.model.Selection()
    base.Global.isLocalMode = false
    base.Global.isDemoMode = false
    base.Global.tourneySlug = ""
    try {
      val local = org.scalajs.dom.window.localStorage
      if (local != null) {
        local.removeItem("App.demo_tourney")
        local.removeItem("App.demo_clubs")
        local.removeItem("App.demo_players")
        local.removeItem("App.demo_comps")
        local.removeItem("App.demo_stages")
        local.removeItem("App.demo_aci_999999")
        if (wpId > 0) local.removeItem("App.demo_aci_" + wpId)
        local.removeItem("tourney_is_local_mode")
        local.removeItem("tourney_slug")
        local.removeItem("tourney_name")
        local.removeItem("tourney_ident")
        local.removeItem("tourney_last_page")
        local.removeItem("tourney_last_param")
        local.removeItem("tourney_last_selection")
        local.removeItem("tourney_last_wp_page_id")
      }
      val session = org.scalajs.dom.window.sessionStorage
      if (session != null) {
        session.removeItem("tourney_is_local_mode")
        session.removeItem("tourney_slug")
        session.removeItem("tourney_name")
        session.removeItem("tourney_ident")
        session.removeItem("tourney_last_page")
        session.removeItem("tourney_last_param")
        session.removeItem("tourney_last_selection")
        session.removeItem("tourney_last_wp_page_id")
      }
    } catch {
      case _: Exception => // ignore storage access exceptions
    }
    base.Global.clearSessionTourneyState()
  }

  /**
   * Deletes the tournament (either local storage or server WordPress post)
   * and thoroughly purges all browser storage keys and memory state so no traces
   * or status bar remnants remain.
   *
   * @param trnyId Tournament Post ID (or 999999 for local mode).
   * @return Future containing Either AppError or Unit on success.
   */
  def apiDelete(trnyId: Int): Future[Either[AppError, Unit]] =
    val isLocal = base.Global.isDemoMode || base.Global.isLocalMode || trnyId == 999999
    if (isLocal) then
      purgeAllTourneyStorage(trnyId)
      Future.successful(Right(()))
    else if (trnyId == 0) then
      purgeAllTourneyStorage(0)
      Future.successful(Right(()))
    else
      val params = List("postId" -> trnyId.toString)
      ajaxDelete[Unit](routeDelete, params).map {
        case Right(_) => 
          Logging.info(s"Tournament $trnyId deleted from server.")
          purgeAllTourneyStorage(trnyId)
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

  /**
   * Loads ACI configuration for the given tournament post ID.
   */
  def loadAci(wpId: Int): Future[Unit] =
    if (base.Global.isDemoMode || base.Global.isLocalMode || wpId == 999999) {
      val localData = org.scalajs.dom.window.localStorage.getItem("App.demo_aci_" + wpId)
      if (localData != null && localData.nonEmpty) {
        try {
          base.Global.currentAci = read[shared.model.ACI](localData)
        } catch {
          case _: Exception => base.Global.currentAci = shared.model.ACI()
        }
      } else {
        base.Global.currentAci = shared.model.ACI()
      }
      Future.successful(())
    } else {
      ajaxGet[shared.model.ACI]("/wp-json/tourney/v1/read-aci", List("postId" -> wpId.toString), host = Global.homeUrl).map {
        case Right(aci) => base.Global.currentAci = aci
        case Left(err) =>
          Logging.error(s"Failed to load ACI: ${err.msgCode}")
          base.Global.currentAci = shared.model.ACI()
      }
    }

  /**
   * Saves ACI configuration for the given tournament post ID.
   */
  def saveAci(wpId: Int, aci: shared.model.ACI): Future[Either[AppError, Unit]] =
    if (base.Global.isDemoMode || base.Global.isLocalMode || wpId == 999999) {
      base.Global.currentAci = aci
      org.scalajs.dom.window.localStorage.setItem("App.demo_aci_" + wpId, write(aci))
      Future.successful(Right(()))
    } else {
      ajaxPost[shared.model.ACI, shared.model.SaveAciResponse]("/wp-json/tourney/v1/save-aci", List("postId" -> wpId.toString), aci, host = Global.homeUrl).map {
        case Right(_) =>
          base.Global.currentAci = aci
          Right(())
        case Left(err) => Left(err)
      }
    }

  /**
   * Generates the default export filename for a tournament in the format:
   * "tourney_<turniername>_<datum>_export.json"
   *
   * @param t The tournament to generate the filename for.
   * @return Formatted filename string.
   */
  def defaultExportFilename(t: Tourney = tourney): String = {
    val rawName = if (t != null && t.name.trim.nonEmpty) t.name.trim else "turnier"
    val safeName = rawName.replaceAll("\\s+", "_").replaceAll("[^a-zA-Z0-9äöüÄÖÜß_\\.-]", "")
    val dateStr = if (t != null && t.startDate > 0) {
      t.startDate.toString
    } else {
      val d = new scala.scalajs.js.Date()
      val y = d.getFullYear().toInt
      val m = f"${d.getMonth().toInt + 1}%02d"
      val day = f"${d.getDate().toInt}%02d"
      s"$y$m$day"
    }
    s"tourney_${safeName}_${dateStr}_export.json"
  }

  /**
   * Triggers a browser download of the tournament as a JSON export file.
   *
   * @param filename Target file name.
   * @param t Tournament to export (defaults to current active tournament).
   */
  def triggerExportDownload(filename: String, t: Tourney = tourney): Unit = {
    import services.DemoManager.DemoPayload
    import shared.basic.Pickle.*

    val finalFilename = if (filename.toLowerCase.endsWith(".json")) filename else s"$filename.json"
    val tReq = TourneySyncRequest(version, t)
    val payload = DemoPayload(tReq)
    val jsonString = write(payload)
    
    val blob = new org.scalajs.dom.Blob(scala.scalajs.js.Array(jsonString), org.scalajs.dom.BlobPropertyBag("application/json"))
    val url = org.scalajs.dom.URL.createObjectURL(blob)
    val a = org.scalajs.dom.document.createElement("a").asInstanceOf[org.scalajs.dom.html.Anchor]
    a.href = url
    a.download = finalFilename
    org.scalajs.dom.document.body.appendChild(a)
    a.click()
    org.scalajs.dom.document.body.removeChild(a)
    org.scalajs.dom.URL.revokeObjectURL(url)

    addExportedFilename(finalFilename)
  }

  /**
   * Retrieves the set of filenames that have been exported or imported in the current browser session/storage.
   */
  def getExportedFilenames(): Set[String] =
    try {
      val raw = org.scalajs.dom.window.localStorage.getItem(s"${base.Global.localStoragePrefix}exported_files")
      if (raw != null && raw.nonEmpty) {
        shared.basic.Pickle.read[Set[String]](raw)
      } else Set.empty
    } catch {
      case _: Exception => Set.empty
    }

  /**
   * Records a filename as exported/imported to track file existence.
   *
   * @param filename Filename to record.
   */
  def addExportedFilename(filename: String): Unit =
    try {
      val clean = filename.trim.toLowerCase
      val finalName = if (clean.endsWith(".json")) clean else s"$clean.json"
      val current = getExportedFilenames()
      val updated = current + finalName
      org.scalajs.dom.window.localStorage.setItem(
        s"${base.Global.localStoragePrefix}exported_files",
        shared.basic.Pickle.write(updated)
      )
    } catch {
      case _: Exception =>
    }

  /**
   * Checks whether a file with the given name has already been exported or imported.
   *
   * @param filename Name of the file to check.
   * @return True if a file with this name is already recorded.
   */
  def fileExists(filename: String): Boolean =
    val clean = filename.trim.toLowerCase
    val finalName = if (clean.endsWith(".json")) clean else s"$clean.json"
    getExportedFilenames().contains(finalName)

