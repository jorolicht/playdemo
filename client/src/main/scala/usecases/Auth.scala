package usecases

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

import cviews.usecases._
import shared.model._
import shared.Ids._
import services._
import base._


object Auth extends UseCase with JsWrapper with Mgmt with Authentication:
  
  def render(param: String = ""): Boolean = 
    param.toLowerCase match       
      case "reset"  =>  
        // reset password
        debug(s"Auth.render -> ${param}")
      case "register"  =>  
        // register user
        addClass(gE(Auth_Content), "d-none")
        removeClass(gE(Main_Content), "d-none")
        setMain(html.Register())
        debug(s"Auth.render -> ${param}")
    true

  override def event(elem: HTMLElement, event: dom.Event) =   
    elem.id match
      case Auth_showLogin => 
        // switch to login content as dynamic creation of
        // login content doesn't work with google sign in
        addClass(gE(Main_Content), "d-none")
        removeClass(gE(Auth_Content), "d-none")
      case Auth_doLogout   => doLogout()
      case Auth_doLogin    => doLogin()
      case Auth_doForgot   => doForgot()
      case Auth_doRegister => 
        // register user
        addClass(gE(Auth_Content), "d-none")
        removeClass(gE(Main_Content), "d-none")
        setMain(html.Register())     
      case Auth_Email      => removeClass(gE(Auth_Email), "is-invalid")
      case Auth_Password   => removeClass(gE(Auth_Password), "is-invalid")
      case _               => error(s"event -> invalid id/key: ${elem.id}")     


  def doLogin() =
    val eMail    = getInput(gE(Auth_Email))
    val password = getInput(gE(Auth_Password))
    val validEmail    = isEmailValid(eMail) 
    val validPwFormat = isPasswordFormatValid(password) 
    changeClass(gE(Auth_Email), !validEmail, "is-invalid")
    changeClass(gE(Auth_Password), !validPwFormat, "is-invalid")
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
    val eMail      = getInput(gE(Auth_Email))
    val validEmail = isEmailValid(eMail)
    changeClass(gE(Auth_Email), !validEmail, "is-invalid")



  