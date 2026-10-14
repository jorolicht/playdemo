package base

import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import shared.model.{User, Selection}
import shared.basic.AppError
import shared.MainIds.* 
import base.Logging.*
import comps.Sidebar


@js.native
@JSGlobal("Math")
object Math extends js.Object {
  def random(): Double = js.native
}

object UUIDGen {
  def generate: String = {
    val randomPart = Math.random().toString.substring(2, 15) + System.currentTimeMillis() % 10000
    randomPart
  }
}


trait Mgmt extends JsWrapper:
  def validUser = Global.user != None  
  def getUser = Global.user

  def ucError(err: AppError): Unit =
    pages.Auth.hide()

    removeClass(gE(ContentId), "d-none")
    if pages.PgError.render(err) then
      Sidebar.setNavLink("Error")
    else   
      error(s"exec -> usecase:Error ${err}")

enum AuthMode:
  case Nonce
  case AppPassword

object Global:
  import shared.model.{User, Selection}
  val localStoragePrefix = "App."
  var user : Option[User] = None
  private var _currentSelection = Selection()
  def currentSelection: Selection = _currentSelection
  def currentSelection_=(value: Selection): Unit = {
    _currentSelection = value
    try {
      val storage = org.scalajs.dom.window.sessionStorage
      if (storage != null) {
        val selectionJson = shared.basic.Pickle.write(value)
        storage.setItem("tourney_last_selection", selectionJson)
      }
      val effectiveSlug = if (isLocalMode) "local" else value.tourney.map(_.slug).filter(_.nonEmpty).getOrElse("")
      if (effectiveSlug.nonEmpty) {
        if (storage != null) storage.setItem("tourney_slug", effectiveSlug)
        val local = org.scalajs.dom.window.localStorage
        if (local != null) local.setItem("tourney_slug", effectiveSlug)
      }
      val tourneyName = value.tourney.map(_.name).filter(_.nonEmpty).getOrElse("")
      if (tourneyName.nonEmpty) {
        if (storage != null) storage.setItem("tourney_name", tourneyName)
        val local = org.scalajs.dom.window.localStorage
        if (local != null) local.setItem("tourney_name", tourneyName)
      }
      val tourneyIdent = value.tourney.map(_.ident).filter(_.nonEmpty).getOrElse("")
      if (tourneyIdent.nonEmpty) {
        if (storage != null) storage.setItem("tourney_ident", tourneyIdent)
        val local = org.scalajs.dom.window.localStorage
        if (local != null) local.setItem("tourney_ident", tourneyIdent)
      }
    } catch {
      case _: Exception => // ignore during testing or if storage is not available
    }
    services.WebSocketService.init()
  }
  var lang    = ""
  var version = ""
  
  def isDemoMode: Boolean = {
    try {
      val storage = org.scalajs.dom.window.sessionStorage
      if (storage != null) {
        storage.getItem("tourney_is_demo_mode") == "true"
      } else false
    } catch {
      case _: Exception => false
    }
  }
  def isDemoMode_=(value: Boolean): Unit = {
    try {
      val storage = org.scalajs.dom.window.sessionStorage
      if (storage != null) {
        storage.setItem("tourney_is_demo_mode", value.toString)
      }
    } catch {
      case _: Exception =>
    }
  }

  /**
   * Indicates whether the application is running in offline local mode.
   * Checks sessionStorage, localStorage, and whether the current tournament is local (wpId 999999).
   */
  def isLocalMode: Boolean = {
    try {
      val session = org.scalajs.dom.window.sessionStorage
      val local = org.scalajs.dom.window.localStorage
      val fromSession = if (session != null) session.getItem("tourney_is_local_mode") == "true" else false
      val fromLocal = if (local != null) local.getItem("tourney_is_local_mode") == "true" else false
      val isLocalTourney = currentSelection.tourney.exists(_.wpId == 999999) || 
                           (services.TourneyDB.tourney != null && services.TourneyDB.tourney.wpId == 999999)
      !isDemoMode && (fromSession || fromLocal || isLocalTourney)
    } catch {
      case _: Exception => false
    }
  }

  /**
   * Sets the local mode flag in both sessionStorage and localStorage,
   * and synchronizes the tourney_slug storage key ("local" if true, or the active tournament slug if false).
   */
  def isLocalMode_=(value: Boolean): Unit = {
    try {
      val session = org.scalajs.dom.window.sessionStorage
      val local = org.scalajs.dom.window.localStorage
      if (session != null) {
        session.setItem("tourney_is_local_mode", value.toString)
      }
      if (local != null) {
        local.setItem("tourney_is_local_mode", value.toString)
      }
      
      val effectiveSlug = if (value) "local" else currentSelection.tourney.map(_.slug).filter(s => s.nonEmpty && s != "local").getOrElse(if (services.TourneyDB.tourney != null && services.TourneyDB.tourney.slug != "local") services.TourneyDB.tourney.slug else "")
      if (session != null) {
        if (effectiveSlug.nonEmpty) session.setItem("tourney_slug", effectiveSlug)
        else session.removeItem("tourney_slug")
      }
      if (local != null) {
        if (effectiveSlug.nonEmpty) local.setItem("tourney_slug", effectiveSlug)
        else local.removeItem("tourney_slug")
      }
    } catch {
      case _: Exception =>
    }
  }

  /**
   * Retrieves the current tourney slug from sessionStorage, localStorage, or the current selection.
   * If isLocalMode is true, returns "local".
   */
  def tourneySlug: String = {
    try {
      if (isLocalMode) return "local"
      val session = org.scalajs.dom.window.sessionStorage
      val local = org.scalajs.dom.window.localStorage
      val fromSession = if (session != null) session.getItem("tourney_slug") else null
      val fromLocal = if (local != null) local.getItem("tourney_slug") else null
      if (fromSession != null && fromSession.nonEmpty) fromSession
      else if (fromLocal != null && fromLocal.nonEmpty) fromLocal
      else currentSelection.tourney.map(_.slug).filter(_.nonEmpty).getOrElse(if (services.TourneyDB.tourney != null) services.TourneyDB.tourney.slug else "")
    } catch {
      case _: Exception => if (isLocalMode) "local" else ""
    }
  }

  /**
   * Sets the tourney slug in both sessionStorage and localStorage.
   * If isLocalMode is true, "local" is stored regardless of the parameter.
   */
  def tourneySlug_=(slug: String): Unit = {
    try {
      val effectiveSlug = if (isLocalMode) "local" else slug
      val session = org.scalajs.dom.window.sessionStorage
      val local = org.scalajs.dom.window.localStorage
      if (session != null) {
        if (effectiveSlug.nonEmpty) session.setItem("tourney_slug", effectiveSlug)
        else session.removeItem("tourney_slug")
      }
      if (local != null) {
        if (effectiveSlug.nonEmpty) local.setItem("tourney_slug", effectiveSlug)
        else local.removeItem("tourney_slug")
      }
    } catch {
      case _: Exception =>
    }
  }

  /**
   * Clears saved tournament page state from session storage and local storage.
   */
  def clearSessionTourneyState(): Unit = {
    try {
      val session = org.scalajs.dom.window.sessionStorage
      if (session != null) {
        session.removeItem("tourney_last_page")
        session.removeItem("tourney_last_param")
        session.removeItem("tourney_last_wp_page_id")
        session.removeItem("tourney_last_selection")
        session.removeItem("tourney_slug")
        session.removeItem("tourney_name")
        session.removeItem("tourney_ident")
        session.removeItem("tourney_is_local_mode")
      }
      val local = org.scalajs.dom.window.localStorage
      if (local != null) {
        local.removeItem("tourney_slug")
        local.removeItem("tourney_name")
        local.removeItem("tourney_ident")
        local.removeItem("tourney_is_local_mode")
      }
    } catch {
      case _: Exception =>
    }
  }

  var csrf    = ""
  var homeUrl = ""
  var dataUrl = ""
  var imgUrl  = ""
  var playUrl = ""
  var appMode: String = "home"
  def isViewMode: Boolean = appMode == "view" || activePageName == "TourneyWelcome" || activePageName == "CompetitionWelcome"
  var activePageName: String = ""
  def isTourneyPage(pageName: String): Boolean = 
    Set(
      "TourneyInfo", "TourneyWelcome", "TourneyAdmin", "CompetitionInfo", "CompetitionWelcome", "PlayerRegistration", 
      "PlayerList", "ResultList", "StageAdmin", "StageDraw", 
      "StageInput", "StageResult", "StageScoreSheet", "StageCertificate", "Certificate"
    ).contains(pageName)
  var pageId  = 0
  var hostPageId = 0
  var wpNonce   = ""
  var turnstileSitekey = ""
  var currentAci: shared.model.ACI = shared.model.ACI()

  var authMode: AuthMode = AuthMode.Nonce
  var wpUserName: String = ""
  var wpAppPassword: String = ""

  def setUser(usr: User) = user = Some(usr)
  def resetUser: Unit = {
    user = None
    try {
      val adminBar = org.scalajs.dom.document.getElementById("wpadminbar")
      if (adminBar != null) {
        adminBar.remove()
      }
      val body = org.scalajs.dom.document.body
      if (body != null) {
        body.classList.remove("admin-bar")
        body.classList.remove("logged-in")
        body.style.marginTop = ""
      }
      val html = org.scalajs.dom.document.documentElement.asInstanceOf[org.scalajs.dom.raw.HTMLElement]
      if (html != null) {
        html.classList.remove("html-admin-bar")
        html.style.marginTop = ""
      }
    } catch {
      case _: Exception => // ignore
    }
    try {
      val storage = org.scalajs.dom.window.sessionStorage
      if (storage != null) {
        storage.removeItem("tourney_last_page")
        storage.removeItem("tourney_last_param")
        storage.removeItem("tourney_last_wp_page_id")
        storage.removeItem("tourney_last_selection")
        storage.removeItem("tourney_slug")
        storage.removeItem("tourney_name")
        storage.removeItem("tourney_ident")
      }
      val local = org.scalajs.dom.window.localStorage
      if (local != null) {
        local.removeItem("tourney_slug")
        local.removeItem("tourney_name")
        local.removeItem("tourney_ident")
      }
    } catch {
      case _: Exception => // ignore
    }
  }

  /**
   * Checks whether the current user or session has administrative access to the given tournament.
   * Offline/Local tournaments (wpId 999999 or <= 0), Demo mode, and Local mode always have full access.
   * Logged-in WordPress users have access if they have an administrative role or match the tournament organizer.
   *
   * @param tourney The tournament to check access for.
   * @return True if edit/delete and management actions are allowed.
   */
  def hasTourneyAccess(tourney: shared.model.Tourney): Boolean =
    if (tourney == null || isDemoMode || isLocalMode || tourney.wpId == 999999 || tourney.wpId <= 0) return true
    user match {
      case Some(u) => 
        val isAdminOrMaster = u.roles.exists(r => List("administrator", "editor", "tourney_master").contains(r))
        val isTourneyAdminOfThis = u.isTurnierAdmin && (u.org == tourney.organizer || u.username == tourney.organizer || u.email == tourney.organizer)
        isAdminOrMaster || isTourneyAdminOfThis
      case None => false
    }

  def formatDateTime(dateTimeStr: String): String = {
    val formatCode = currentAci.dateFormat
    if (dateTimeStr == null || dateTimeStr.length < 16) {
      dateTimeStr
    } else {
      val datePart = dateTimeStr.substring(0, 10)
      val timePart = dateTimeStr.substring(11, 16)
      val dateParts = datePart.split("-")
      if (dateParts.length == 3) {
        val y = dateParts(0)
        val m = dateParts(1)
        val d = dateParts(2)
        formatCode match {
          case "UK" => s"$d/$m/$y $timePart"
          case "US" =>
            val hoursParts = timePart.split(":")
            if (hoursParts.length == 2) {
              val hh = hoursParts(0).toInt
              val mm = hoursParts(1)
              val ampm = if (hh >= 12) "PM" else "AM"
              val hh12 = if (hh == 0) 12 else if (hh > 12) hh - 12 else hh
              val hh12Str = if (hh12 < 10) s"0$hh12" else hh12.toString
              s"$m/$d/$y $hh12Str:$mm $ampm"
            } else {
              s"$m/$d/$y $timePart"
            }
          case _ => s"$d.$m.$y $timePart"
        }
      } else {
        dateTimeStr
      }
    }
  }

  def formatPurchaseDate(rawDate: String): String = {
    if (rawDate == null || rawDate.isEmpty) return ""
    if (rawDate.length == 12 && rawDate.forall(_.isDigit)) {
      val y = rawDate.substring(0, 4)
      val m = rawDate.substring(4, 6)
      val d = rawDate.substring(6, 8)
      val hh = rawDate.substring(8, 10)
      val mm = rawDate.substring(10, 12)
      formatDateTime(s"$y-$m-$d $hh:$mm")
    } else if (rawDate.length >= 16) {
      formatDateTime(rawDate)
    } else {
      rawDate
    }
  }


                            