package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.*

import cviews.pages.*
import shared.model.*

import shared.DomTypes.HtmlId
import services._
import base._
import shared.AuthIds.*
import shared.MainIds.*

import comps.Sidebar.LoginInfoId
import comps.Sidebar.LoggedInAsId
import comps.Navbar.DoLogoutId
import comps.Navbar.ShowLoginId



object Auth extends BasePage with JsWrapper with Mgmt with Authentication:

  def setUser(usr: User) = 
    Global.user = Some(usr)
    changeClass(gE(ShowLoginId), validUser, "disabled")
    changeClass(gE(DoLogoutId), !validUser, "disabled")
    changeClass(gE(LoginInfoId), !validUser, "d-none")
    setHtml(gE(LoggedInAsId), s"${usr.firstname} ${usr.lastname}")


  def resetUser = 
    Global.user = None
    changeClass(gE(ShowLoginId), validUser, "disabled")
    changeClass(gE(DoLogoutId), !validUser, "disabled")
    changeClass(gE(LoginInfoId), !validUser, "d-none")
    setHtml(gE(LoggedInAsId), "")


  def hide() = addClass(gE(AuthContentId), "d-none")
  def show() = removeClass(gE(AuthContentId), "d-none")

  def render(param: String = ""): Boolean = 
    param.toLowerCase match       
      case "reset"  =>  
        // reset password
        debug(s"Auth.render -> ${param}")
      case "register"  =>  
        // register user
        addClass(gE(ContentId), "d-none")
        removeClass(gE(ContentId), "d-none")
        setMain(html.Register())
        debug(s"Auth.render -> ${param}")
    true

  override def event(elem: HTMLElement, event: dom.Event) =   
    HtmlId(elem.id) match
      case `ShowLoginId` => 
        // switch to login content as dynamic creation of
        // login content doesn't work with google sign in
        addClass(gE(ContentId), "d-none")
        removeClass(gE(AuthContentId), "d-none")
      case `DoLogoutId`   => doLogout()
      case `DoLoginId`    => doLogin()
      case `DoForgotId`   => doForgot()
      case `DoRegisterId` => 
        // register user
        addClass(gE(ContentId), "d-none")
        removeClass(gE(ContentId), "d-none")
        setMain(html.Register())     
      case `EmailId`      => removeClass(gE(EmailId), "is-invalid")
      case `PasswordId`   => removeClass(gE(PasswordId), "is-invalid")
      case _              => error(s"event -> invalid id/key: ${elem.id}")     


  def doLogin() =
    val eMail    = getInput(gE(EmailId))
    val password = getInput(gE(PasswordId))
    val validEmail    = isEmailValid(eMail) 
    val validPwFormat = isPasswordFormatValid(password) 
    changeClass(gE(EmailId), !validEmail, "is-invalid")
    changeClass(gE(PasswordId), !validPwFormat, "is-invalid")
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
    val eMail      = getInput(gE(EmailId))
    val validEmail = isEmailValid(eMail)
    changeClass(gE(EmailId), !validEmail, "is-invalid")
