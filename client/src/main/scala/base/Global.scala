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
        storage.setItem("playdemo_last_selection", selectionJson)
      }
    } catch {
      case _: Exception => // ignore during testing or if storage is not available
    }
    services.WebSocketService.init()
  }
  var lang    = ""
  var version = ""
  var isDemoMode: Boolean = false
  var csrf    = ""
  var homeUrl = ""
  var dataUrl = ""
  var imgUrl  = ""
  var playUrl = ""
  var pageId  = 0
  var hostPageId = 0
  var wpNonce   = ""
  var turnstileSitekey = ""

  var authMode: AuthMode = AuthMode.Nonce
  var wpUserName: String = ""
  var wpAppPassword: String = ""

  def setUser(usr: User) = user = Some(usr)
  def resetUser = user = None

  def hasTourneyAccess(tourney: shared.model.Tourney): Boolean =
    if (isDemoMode) return true
    user match {
      case Some(u) => 
        u.isTurnierAdmin && (u.org == tourney.organizer || u.username == tourney.organizer || u.roles.contains("administrator"))
      case None => false
    }


                            