package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.*

import cviews.pages.*
import shared.model.*
import shared.GlobalIds.*
import shared.DomTypes.HtmlId
import services._
import base._
import shared._
 

object Auth extends BasePage with JsWrapper with Mgmt with Authentication:

  val LoginInfoId: HtmlId = HtmlId.fromName(name)
  val LoggedInAsId: HtmlId = HtmlId.fromName(name)
  val ShowLoginId: HtmlId = HtmlId.fromName(name)
  val DoLogoutId: HtmlId = HtmlId.fromName(name)
  val AuthContentId: HtmlId = HtmlId.fromName(name)


  def setUser(usr: User) = 
    Global.user = Some(usr)
    changeClass(gE3(ShowLoginId), validUser, "disabled")
    changeClass(gE3(DoLogoutId), !validUser, "disabled")
    changeClass(gE3(LoginInfoId), !validUser, "d-none")
    setHtml(gE3(LoggedInAsId), s"${usr.firstname} ${usr.lastname}")


  def resetUser = 
    Global.user = None
    changeClass(gE3(ShowLoginId), validUser, "disabled")
    changeClass(gE3(DoLogoutId), !validUser, "disabled")
    changeClass(gE3(LoginInfoId), !validUser, "d-none")
    setHtml(gE3(LoggedInAsId), "")


  def hide() = addClass(gE3(AuthContentId), "d-none")
  def show() = removeClass(gE3(AuthContentId), "d-none")

  def render(param: String = ""): Boolean = 
    param.toLowerCase match       
      case "reset"  =>  
        // reset password
        debug(s"Auth.render -> ${param}")
      case "register"  =>  
        // register user
        addClass(gE3(AuthContentId), "d-none")
        removeClass(gE3(AppContentId), "d-none")
        setMain(html.Register())
        debug(s"Auth.render -> ${param}")
    true

  override def event(elem: HTMLElement, event: dom.Event) =   
    HtmlId(elem.id) match
      case `ShowLoginId` => 
        // switch to login content as dynamic creation of
        // login content doesn't work with google sign in
        addClass(gE3(AppContentId), "d-none")
        removeClass(gE3(AuthContentId), "d-none")
      case `DoLogoutId`   => doLogout()
      case `DoLoginId`    => doLogin()
      case `DoForgotId`   => doForgot()
      case `DoRegisterId` => 
        // register user
        addClass(gE3(AuthContentId), "d-none")
        removeClass(gE3(AppContentId), "d-none")
        setMain(html.Register())     
      case `EmailId`      => removeClass(gE3(EmailId), "is-invalid")
      case `PasswordId`   => removeClass(gE3(PasswordId), "is-invalid")
      case _              => error(s"event -> invalid id/key: ${elem.id}")     


  def doLogin() =
    val eMail    = getInput(gE3(EmailId))
    val password = getInput(gE3(PasswordId))
    val validEmail    = isEmailValid(eMail) 
    val validPwFormat = isPasswordFormatValid(password) 
    changeClass(gE3(EmailId), !validEmail, "is-invalid")
    changeClass(gE3(PasswordId), !validPwFormat, "is-invalid")
    if (validEmail && validPwFormat) then
      basicLogin(eMail, password).map {
        case Left(err)  => resetUser; loadPage("PgError", err.toString) 
        case Right(usr) => setUser(usr); loadPage("Home", "welcome") 
      } 

      
  def doLogout() =
    logout(getUser).map {
      case Left(err)  => resetUser; loadPage("PgError", err.toString)
      case Right(res) => resetUser; loadPage("Home", "goodbye") 
    }


  @JSExportTopLevel("handleGoogleCredential")
  def googleLogin(credentials: String): Unit = 
    ajaxPost[User]("/auth/googleLogin", List(), credentials).map { 
      case Left(err)  => println(s"Error: ${err}") 
      case Right(usr) => setUser(usr); loadPage("Home", "welcome")
    }      

  def doForgot() =
    val eMail      = getInput(gE3(EmailId))
    val validEmail = isEmailValid(eMail)
    changeClass(gE3(EmailId), !validEmail, "is-invalid")
