package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.*

import cviews.pages.*
import shared.model.*

import services.*
import base.*
import shared.AuthIds.*
import shared.MainIds.*
import comps.Sidebar.updateUserInfo


// import comps.Sidebar.LoginInfoId
// import comps.Sidebar.LoggedInAsId
import comps.Navbar.DoLogoutId
import comps.Navbar.ShowLoginId


object Auth extends BasePage with JsWrapper with Mgmt with Authentication:
  def name = PageNameTyp("Auth")

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
        setMain(html.UserRegistration())
        debug(s"Auth.render -> ${param}")
    true

  override def handleEvent(elem: HTMLElement, event: dom.Event) =   
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
        setMain(html.UserRegistration())     
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
        case Left(err)  => Global.resetUser; updateUserInfo; loadPage(PageNameTyp("PgError"), err.toString) 
        case Right(usr) => 
          Global.setUser(usr)
          updateUserInfo
          val alert = dom.document.getElementById("AuthSuccessAlert")
          if (alert != null) alert.classList.remove("d-none")
          dom.window.setTimeout(() => {
            loadPage(pages.MainView.name, "welcome")
          }, 2000)
      } 

      
  def doLogout() =
    logout(getUser).map {
      case Left(err)  => Global.resetUser; updateUserInfo; loadPage(PageNameTyp("PgError"), err.toString)
      case Right(res) => Global.resetUser; updateUserInfo; loadPage(pages.MainView.name, "goodbye") 
    }


  @JSExportTopLevel("handleGoogleCredential")
  def googleLogin(credentials: String): Unit = 
    ajaxPost[String, User]("/auth/googleLogin", List(), credentials).map { 
      case Left(err)  => println(s"Error: ${err}") 
      case Right(usr) => 
        Global.setUser(usr)
        updateUserInfo
        val alert = dom.document.getElementById("AuthSuccessAlert")
        if (alert != null) alert.classList.remove("d-none")
        dom.window.setTimeout(() => {
          loadPage(pages.MainView.name, "welcome")
        }, 2000)
    }      

  def doForgot() =
    val eMail      = getInput(gE(EmailId))
    val validEmail = isEmailValid(eMail)
    changeClass(gE(EmailId), !validEmail, "is-invalid")
