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
    ajaxPost[String, String]("/wp-json/tourney/v1/auth/logout", List(), "", host = Global.homeUrl).map { _ =>
      Global.resetUser
      Global.currentSelection = Selection()
      comps.ContextHeader.hide()
      comps.Navbar.render()
      loadPage(shared.PageNameTyp("Goodbye"), "")
    }

  private def doPasskeyLogin(): Unit =
    services.WebAuthnService.loginPasskey().flatMap {
      case Right(msg) => 
        debug(s"Passkey Login successful: $msg")
        ajaxGet[UserInfo]("/wp-json/tourney/v1/user", List(), Map("X-WP-NONCE" -> Global.wpNonce), Global.homeUrl).map {
          case Right(ui) if ui.user_id > 0 =>
            Global.user = Some(User(
              id = ("", ui.user_id),
              username = ui.username,
              email = ui.email,
              firstname = ui.firstname,
              lastname = ui.lastname,
              org = ui.club,
              picUrl = ui.avatar_url,
              description = ui.description,
              roles = ui.roles
            ))
            comps.Navbar.render()
          case _ =>
            debug("Could not fetch user info after passkey login")
        }.map { _ =>
          val alert = dom.document.getElementById("LoginSuccessAlert")
          if (alert != null) alert.classList.remove("d-none")
          dom.window.setTimeout(() => {
            dom.window.location.href = Global.homeUrl
          }, 2500)
        }
      case Left(err) =>
        dom.window.alert(s"Passkey Login fehlgeschlagen: ${err.msg}")
        Future.successful(())
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

    ajaxPost[Map[String, String], Map[String, String]]("/wp-json/tourney/v1/auth/login", List(), data, host = Global.homeUrl).flatMap {
      case Right(res) => 
        debug(s"Login successful: $res")
        ajaxGet[UserInfo]("/wp-json/tourney/v1/user", List(), Map("X-WP-NONCE" -> Global.wpNonce), Global.homeUrl).map {
          case Right(ui) if ui.user_id > 0 =>
            Global.user = Some(User(
              id = ("", ui.user_id),
              username = ui.username,
              email = ui.email,
              firstname = ui.firstname,
              lastname = ui.lastname,
              org = ui.club,
              picUrl = ui.avatar_url,
              description = ui.description,
              roles = ui.roles
            ))
            comps.Navbar.render()
          case _ =>
            debug("Could not fetch user info after login")
        }.map { _ =>
          val alert = dom.document.getElementById("LoginSuccessAlert")
          if (alert != null) alert.classList.remove("d-none")
          dom.window.setTimeout(() => {
            dom.window.location.href = Global.homeUrl
          }, 2500)
        }
      case Left(err) => 
        dom.window.alert(s"Login fehlgeschlagen: $err")
        Future.successful(())
    }
