package base

import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import shared.model.User
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


object Global:
  import shared.model.User
  val localStoragePrefix = "App."
  var user : Option[User] = None
  var lang    = ""
  var version = ""
  var csrf    = ""
  var homeUrl = ""
  var dataUrl = ""
  var playUrl = ""
  var pageId  = 0
  var nonce   = ""

                            