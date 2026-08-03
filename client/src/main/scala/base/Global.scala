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

  def isLocalMode: Boolean = {
    try {
      val storage = org.scalajs.dom.window.sessionStorage
      if (storage != null) {
        storage.getItem("tourney_is_local_mode") == "true"
      } else false
    } catch {
      case _: Exception => false
    }
  }
  def isLocalMode_=(value: Boolean): Unit = {
    try {
      val storage = org.scalajs.dom.window.sessionStorage
      if (storage != null) {
        storage.setItem("tourney_is_local_mode", value.toString)
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
  def isViewMode: Boolean = appMode == "view" || activePageName == "TourneyWelcome"
  var activePageName: String = ""
  def isTourneyPage(pageName: String): Boolean = 
    Set(
      "TourneyInfo", "TourneyWelcome", "TourneyAdmin", "CompetitionInfo", "PlayerRegistration", 
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
      storage.removeItem("tourney_last_page")
      storage.removeItem("tourney_last_param")
      storage.removeItem("tourney_last_wp_page_id")
      storage.removeItem("tourney_last_selection")
    } catch {
      case _: Exception => // ignore
    }
  }

  def hasTourneyAccess(tourney: shared.model.Tourney): Boolean =
    if (isDemoMode) return true
    user match {
      case Some(u) => 
        u.isTurnierAdmin && (u.org == tourney.organizer || u.username == tourney.organizer || u.roles.contains("administrator"))
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


                            