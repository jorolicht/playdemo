package usecases

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

import cviews.usecases._
import shared.model._
import shared.IdsGlobal.*
import shared.IdsGlobal
import services._
import base._
import shared._





object Auth extends UseCase with JsWrapper with Mgmt with Authentication:
  import IdsAuth.* 

  def render(param: String = ""): Boolean = 
    param.toLowerCase match       
      case "reset"  =>  
        // reset password
        debug(s"Auth.render -> ${param}")
      case "register"  =>  
        // register user
        addClass(gE2(ContentId), "d-none")
        removeClass(gE2(AppContentId), "d-none")
        setMain(html.Register())
        debug(s"Auth.render -> ${param}")
    true

  override def event(elem: HTMLElement, event: dom.Event) =   
    IdsAuth.fromId(elem.id) match
      case Some(ShowLoginId) => 
        // switch to login content as dynamic creation of
        // login content doesn't work with google sign in
        addClass(gE2(AppContentId), "d-none")
        removeClass(gE2(ContentId), "d-none")
      case Some(DoLogoutId)   => doLogout()
      case Some(DoLoginId)    => doLogin()
      case Some(DoForgotId)   => doForgot()
      case Some(DoRegisterId) => 
        // register user
        addClass(gE2(ContentId), "d-none")
        removeClass(gE2(AppContentId), "d-none")
        setMain(html.Register())     
      case Some(EmailId)      => removeClass(gE2(EmailId), "is-invalid")
      case Some(PasswordId)   => removeClass(gE2(PasswordId), "is-invalid")
      case _                  => error(s"event -> invalid id/key: ${elem.id}")     


  def doLogin() =
    val eMail    = getInput(gE2(EmailId))
    val password = getInput(gE2(PasswordId))
    val validEmail    = isEmailValid(eMail) 
    val validPwFormat = isPasswordFormatValid(password) 
    changeClass(gE2(EmailId), !validEmail, "is-invalid")
    changeClass(gE2(PasswordId), !validPwFormat, "is-invalid")
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


  def googleLogin(credentials: String): Unit = 
    ajaxPost[User]("/auth/googleLogin", List(), credentials).map { 
      case Left(err)  => println(s"Error: ${err}") 
      case Right(usr) => setUser(usr); ucExec("Home", "welcome")
    }      

  def doForgot() =
    val eMail      = getInput(gE2(EmailId))
    val validEmail = isEmailValid(eMail)
    changeClass(gE2(EmailId), !validEmail, "is-invalid")



  