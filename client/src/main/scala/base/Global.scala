package base

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import shared.model.{ AppError, User }
import shared.Ids._


def debug(msg: => String) = Logging.logger.debug(msg)
def info(msg: => String)  = Logging.logger.info(msg)
def warn(msg: => String)  = Logging.logger.warn(msg)
def error(msg: => String) = Logging.logger.error(msg)

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
  def validUser = !User.isNil(Global.user)   
  def initUser = setUser(User.nil(UUIDGen.generate))             
  def setUser(usr: User) = 
    Global.user = usr
    changeClass(gE(Auth_showLogin), validUser, "disabled")
    changeClass(gE(Auth_doLogout), !validUser, "disabled")
    changeClass(gE(Auth_LoginInfo), !validUser, "d-none")
    setHtml(gE(Auth_LoggedInAs), s"${Global.user.firstname} ${Global.user.lastname}")

  def resetUser = 
    Global.user = User.nil(UUIDGen.generate)
    changeClass(gE(Auth_showLogin), validUser, "disabled")
    changeClass(gE(Auth_doLogout), !validUser, "disabled")
    changeClass(gE(Auth_LoginInfo), !validUser, "d-none")
    setHtml(gE(Auth_LoggedInAs), s"${Global.user.firstname} ${Global.user.lastname}")

  def getUser = Global.user  

  def setServer(srv: String)   = Global.server = srv 
  def setLang(lang: String) = Global.lang = lang  
  def setCsrf(csrf: String) = Global.csrf = csrf
  def setVersion(version: String) = Global.version = version

  def ucError(err: AppError): Unit =
    addClass(gE(Auth_Content), "d-none")
    removeClass(gE(Main_Content), "d-none")
    if usecases.UCError.render(err) then
      setNavLink("Error")
    else   
      error(s"exec -> usecase:Error ${err}")


  def ucExec(usecase: String, param: String): Unit = 
    try
      addClass(gE(Auth_Content), "d-none")
      removeClass(gE(Main_Content), "d-none")
      if Global.ucMap(usecase).render(param) then
        setNavLink(usecase)
      else   
        error(s"exec -> usecase:${usecase} param:${param}")
    catch
      case e: Exception => error(s"exec -> usecase:${usecase} param:${param} not found")


object Global extends JsWrapper:
  import shared.model.User
  import usecases._
  import dialog._
  val localStoragePrefix = "App."
  var server  = ""
  var csrf    = ""
  var lang    = ""
  var version = ""
  var user = User.nil("")

  // usecase map usecase name to usecase object   
  val ucMap = List(Home, Auth, Console, UCError, 
                   DlgPrompt, 
                   ChatExample,
                   UseCase2, UseCase31, UseCase32, UseCase41, UseCase42,
                   UseCase511, UseCase512, UseCase52, UseCase53)
                   .map(uc => uc.name -> uc).toMap  
