package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.model.*
import shared.AuthIds.*
import scala.scalajs.js
import scala.scalajs.js.JSON
import services.ComWrapper

object UserLogin extends BasePage with JsWrapper with ComWrapper:
  def name = PageNameTyp("UserLogin")

  val LoginId:      HtmlId = genId(name)
  val PasswordId:   HtmlId = genId(name)
  val CaptchaId:    HtmlId = genId(name)
  val BtnLogin:     HtmlId = genId(name)
  val BtnLogout:    HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    setMain(cviews.pages.html.UserLogin(Global.user))
    true

  override def handleEvent(elem: HTMLElement, event: dom.Event): Unit = 
    HtmlId(elem.id) match
      case `BtnLogin` => 
        doLogin()
      case `BtnLogout` =>
        doLogout()
      case _ => debug(s"UserLogin Event: ${elem.id}")

  private def doLogout(): Unit =
    debug("Logging out...")
    ajaxPost[String, String]("/wp-json/playdemo/v1/auth/logout", List(), "", host = Global.homeUrl).map { _ =>
      Global.resetUser
      dom.window.location.href = Global.homeUrl
    }

  private def doLogin(): Unit =
    val login    = getInput(gE(LoginId))
    val password = getInput(gE(PasswordId))
    
    // Captcha Token (if implemented)
    // val captchaToken = dom.window.asInstanceOf[js.Dynamic].getCaptchaToken().toString

    if (login.isEmpty || password.isEmpty) {
      dom.window.alert("Bitte Benutzername/E-Mail und Passwort eingeben.")
      return
    }

    val data = Map(
      "email"    -> login,
      "password" -> password
    )

    ajaxPost[Map[String, String], Map[String, String]]("/wp-json/playdemo/v1/auth/login", List(), data, host = Global.homeUrl).map {
      case Right(res) => 
        debug(s"Login successful: $res")
        // Redirect to home or reload to show WP Admin bar/state
        dom.window.location.href = Global.homeUrl
      case Left(err) => 
        dom.window.alert(s"Login fehlgeschlagen: $err")
    }
