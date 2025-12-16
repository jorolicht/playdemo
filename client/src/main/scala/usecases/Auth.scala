package usecases

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.*

import cviews.usecases._
import shared.model._
import shared.IdGlobal.*
import services._
import base._
import shared._
 

object Auth extends UseCase with JsWrapper with Mgmt with Authentication:

  def setUser(usr: User) = 
    Global.user = usr
    changeClass(gE(ShowLoginId), validUser, "disabled")
    changeClass(gE(DoLogoutId), !validUser, "disabled")
    changeClass(gE(LoginInfoId), !validUser, "d-none")
    setHtml(gE(LoggedInAsId), s"${Global.user.firstname} ${Global.user.lastname}")


  def resetUser = 
    Global.user = User.nil(UUIDGen.generate)
    changeClass(gE(ShowLoginId), validUser, "disabled")
    changeClass(gE(DoLogoutId), !validUser, "disabled")
    changeClass(gE(LoginInfoId), !validUser, "d-none")
    setHtml(gE(LoggedInAsId), s"${Global.user.firstname} ${Global.user.lastname}")


  def hide() = addClass(gE(AuthContentId), "d-none")
  def show() = removeClass(gE(AuthContentId), "d-none")

  def render(param: String = ""): Boolean = 
    param.toLowerCase match       
      case "reset"  =>  
        // reset password
        debug(s"Auth.render -> ${param}")
      case "register"  =>  
        // register user
        addClass(gE(AuthContentId), "d-none")
        removeClass(gE(AppContentId), "d-none")
        setMain(html.Register())
        debug(s"Auth.render -> ${param}")
    true

  override def event(elem: HTMLElement, event: dom.Event) =   
    IdGlobal.fromId(elem.id) match
      case Some(ShowLoginId) => 
        // switch to login content as dynamic creation of
        // login content doesn't work with google sign in
        addClass(gE(AppContentId), "d-none")
        removeClass(gE(AuthContentId), "d-none")
      case Some(DoLogoutId)   => doLogout()
      case Some(DoLoginId)    => doLogin()
      case Some(DoForgotId)   => doForgot()
      case Some(DoRegisterId) => 
        // register user
        addClass(gE(AuthContentId), "d-none")
        removeClass(gE(AppContentId), "d-none")
        setMain(html.Register())     
      case Some(EmailId)      => removeClass(gE(EmailId), "is-invalid")
      case Some(PasswordId)   => removeClass(gE(PasswordId), "is-invalid")
      case _                  => error(s"event -> invalid id/key: ${elem.id}")     


  def doLogin() =
    val eMail    = getInput(gE(EmailId))
    val password = getInput(gE(PasswordId))
    val validEmail    = isEmailValid(eMail) 
    val validPwFormat = isPasswordFormatValid(password) 
    changeClass(gE(EmailId), !validEmail, "is-invalid")
    changeClass(gE(PasswordId), !validPwFormat, "is-invalid")
    if (validEmail && validPwFormat) then
      basicLogin(eMail, password).map {
        case Left(err)  => resetUser; ucError(err)
        case Right(usr) => setUser(usr); ucExec("Home", "welcome") 
      } 

      
  def doLogout() =
    logout(getUser).map {
      case Left(err)  => resetUser; ucError(err)
      case Right(res) => resetUser; ucExec("Home", "goodbye") 
    }


  @JSExportTopLevel("handleGoogleCredential")
  def googleLogin(credentials: String): Unit = 
    ajaxPost[User]("/auth/googleLogin", List(), credentials).map { 
      case Left(err)  => println(s"Error: ${err}") 
      case Right(usr) => setUser(usr); ucExec("Home", "welcome")
    }      

  def doForgot() =
    val eMail      = getInput(gE(EmailId))
    val validEmail = isEmailValid(eMail)
    changeClass(gE(EmailId), !validEmail, "is-invalid")
