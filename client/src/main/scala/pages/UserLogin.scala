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
  val BtnPasskey:   HtmlId = genId(name)
  val BtnPasskeyAdd: HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    setMain(cviews.pages.html.UserLogin(Global.user))
    true

  override def handleEvent(elem: HTMLElement, event: dom.Event): Unit = 
    HtmlId(elem.id) match
      case `BtnLogin` => 
        doLogin()
      case `BtnLogout` =>
        doLogout()
      case `BtnPasskey` =>
        doPasskeyLogin()
      case `BtnPasskeyAdd` =>
        doPasskeyAdd()
      case _ => debug(s"UserLogin Event: ${elem.id}")

  private def doPasskeyAdd(): Unit =
    services.WebAuthnService.registerPasskey().map {
      case Right(msg) => dom.window.alert(msg)
      case Left(err)  => dom.window.alert(s"Passkey konnte nicht hinzugefügt werden: ${err.msg}")
    }

  private def doLogout(): Unit =
    ajaxPost[String, Map[String, String]]("/wp-json/tourney/v1/auth/logout", List(), "", host = Global.homeUrl).map { res =>
      res match {
        case Right(m) => m.get("nonce").foreach(n => Global.wpNonce = n)
        case _ => // ignore
      }
      Global.resetUser
      Global.currentSelection = Selection()
      comps.ContextHeader.hide()
      comps.Navbar.render()
      loadPage(shared.PageNameTyp("Goodbye"), "")
    }

  private def doPasskeyLogin(): Unit =
    services.WebAuthnService.loginPasskey().map {
      case Right(msg) => 
        debug(s"Passkey Login successful: $msg")
        val alert = dom.document.getElementById("LoginSuccessAlert")
        if (alert != null) alert.classList.remove("d-none")
        dom.window.setTimeout(() => {
          dom.window.location.href = Global.homeUrl
        }, 2000)
      case Left(err) =>
        dom.window.alert(s"Passkey Login fehlgeschlagen: ${err.msg}")
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

    ajaxPost[Map[String, String], Map[String, String]]("/wp-json/tourney/v1/auth/login", List(), data, hdrs = Map("Content-Type" -> "application/json"), host = Global.homeUrl).map {
      case Right(res) => 
        debug(s"Login successful: $res")
        res.get("nonce").foreach(n => Global.wpNonce = n)
        val alert = dom.document.getElementById("LoginSuccessAlert")
        if (alert != null) alert.classList.remove("d-none")
        dom.window.setTimeout(() => {
          dom.window.location.href = Global.homeUrl
        }, 2000)
      case Left(err) => 
        dom.window.alert(s"Login fehlgeschlagen: $err")
    }
