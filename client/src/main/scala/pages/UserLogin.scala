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
import shared.basic.Pickle.*

object UserLogin extends BasePage with JsWrapper with ComWrapper:
  def name = PageNameTyp("UserLogin")

  val LoginId:      HtmlId = genId(name)
  val PasswordId:   HtmlId = genId(name)
  val CaptchaId:    HtmlId = genId(name)
  val BtnLogin:         HtmlId = genId(name)
  val BtnLogout:        HtmlId = genId(name)
  val BtnPasskey:       HtmlId = genId(name)
  val BtnPasskeyAdd:    HtmlId = genId(name)
  val BtnPasskeyDelete: HtmlId = genId(name)
  val BtnForgot:        HtmlId = genId(name)
  val BtnStartDemo:     HtmlId = genId(name)
  val BtnStartLocal:    HtmlId = genId(name)
  val BtnImportLocal:   HtmlId = genId(name)

  private var isCaptchaVisible = false

  def render(param: String = ""): Boolean = 
    isCaptchaVisible = false
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
      case `BtnPasskeyDelete` =>
        doPasskeyDelete()
      case `BtnForgot` =>
        event.preventDefault()
        dom.window.location.href = Global.homeUrl + "/wp-login.php?action=lostpassword"
      case `BtnStartDemo` =>
        services.DemoManager.promptDemoMode("template-full", () => {
          pages.loadPage(PageNameTyp("TourneyInfo"), "")
        }, () => ())
      case `BtnStartLocal` =>
        services.DemoManager.startLocalMode(() => {
          pages.loadPage(PageNameTyp("TourneyInfo"), "")
        })
      case `BtnImportLocal` =>
        val fileInput = dom.document.getElementById("local-import-file-input").asInstanceOf[dom.html.Input]
        if (fileInput != null) {
          fileInput.click()
          fileInput.onchange = (e: dom.Event) => {
            if (fileInput.files.length > 0) {
              val file = fileInput.files(0)
              val reader = new dom.FileReader()
              reader.onload = (e: dom.Event) => {
                val jsonString = reader.result.asInstanceOf[String]
                try {
                  val payload = read[services.DemoManager.DemoPayload](jsonString)
                  
                  // Set Local Mode
                  Global.isLocalMode = true
                  Global.isDemoMode = false
                  
                  // Restore Tourney
                  services.TourneyDB.tourney = payload.tourney.tourney
                  services.TourneyDB.version = payload.tourney.version
                  
                  // Force sync to local storage
                  services.TourneyDB.sync()
                  services.ClubDB.sync(services.TourneyDB.tourney.clubs.toSeq)
                  services.PlayerDB.sync(services.TourneyDB.tourney.players.toSeq)
                  services.CompetitionDB.sync(services.TourneyDB.tourney.competitions.filter(_ != null).toSeq)
                  services.StageDB.sync(services.TourneyDB.tourney.stages.filter(_ != null).toSeq)
                  
                  dom.window.alert("Import erfolgreich! Das lokale Turnier wird nun geladen.")
                  pages.loadPage(PageNameTyp("TourneyInfo"), "")
                } catch {
                  case ex: Exception =>
                    dom.window.alert(s"Fehler beim Importieren der Datei: ${ex.getMessage}")
                }
              }
              reader.readAsText(file)
            }
          }
        }
      case _ => debug(s"UserLogin Event: ${elem.id}")

  private def doPasskeyAdd(): Unit =
    services.WebAuthnService.registerPasskey().map {
      case Right(msg) => 
        Global.user.foreach(_.hasPasskey = true)
        dom.window.alert(msg)
        render()
      case Left(err)  => dom.window.alert(s"Passkey konnte nicht hinzugefügt werden: ${err.msg}")
    }

  private def doPasskeyDelete(): Unit =
    import shared.BoxButton
    dialogs.DlgMsgbox.show(
      "Möchten Sie Ihren Passkey wirklich löschen?",
      "Passkey löschen",
      List(BoxButton.Yes, BoxButton.No)
    ).map {
      case BoxButton.Yes =>
        ajaxDelete[Map[String, String]]("/wp-json/tourney/v1/auth/webauthn/delete", host = Global.homeUrl).map {
          case Right(_) =>
            Global.user.foreach(_.hasPasskey = false)
            dom.window.alert("Passkey erfolgreich gelöscht.")
            render()
          case Left(err) =>
            dom.window.alert(s"Fehler beim Löschen des Passkeys: ${err.msg}")
        }
      case _ => // do nothing
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
        try {
          dom.window.sessionStorage.removeItem("tourney_last_page")
          dom.window.sessionStorage.removeItem("tourney_last_param")
          dom.window.sessionStorage.removeItem("tourney_last_wp_page_id")
          dom.window.sessionStorage.removeItem("tourney_last_selection")
        } catch { case _: Exception => }
        dom.window.setTimeout(() => {
          dom.window.location.href = Global.homeUrl
        }, 2000)
      case Left(err) =>
        showError(s"Passkey Login fehlgeschlagen: ${err.msg}")
    }

  private def doLogin(): Unit =
    val login    = getInput(gE(LoginId))
    val password = getInput(gE(PasswordId))
    
    val errorAlert = dom.document.getElementById("LoginErrorAlert")
    if (errorAlert != null) {
      errorAlert.classList.add("d-none")
      errorAlert.textContent = ""
    }

    // Turnstile Token (falls Captcha eingeblendet ist)
    val turnstileElements = dom.document.getElementsByName("cf-turnstile-response")
    val turnstileToken = if (turnstileElements.length > 0) {
      turnstileElements(0).asInstanceOf[dom.html.Input].value
    } else ""

    println(s"doLogin -> sitekey: '${Global.turnstileSitekey}' elements: ${turnstileElements.length} token: '${turnstileToken}'")

    if (login.isEmpty || password.isEmpty) {
      showError("Bitte E-Mail-Adresse und Passwort eingeben.")
      return
    }

    val data = Map(
      "email"    -> login,
      "password" -> password,
      "turnstileToken" -> turnstileToken
    )

    ajaxPost[Map[String, String], Map[String, String]]("/wp-json/tourney/v1/auth/login", List(), data, hdrs = Map("Content-Type" -> "application/json"), host = Global.homeUrl).map {
      case Right(res) => 
        debug(s"Login successful: $res")
        res.get("nonce").foreach(n => Global.wpNonce = n)
        val alert = dom.document.getElementById("LoginSuccessAlert")
        if (alert != null) alert.classList.remove("d-none")
        try {
          dom.window.sessionStorage.removeItem("tourney_last_page")
          dom.window.sessionStorage.removeItem("tourney_last_param")
          dom.window.sessionStorage.removeItem("tourney_last_wp_page_id")
          dom.window.sessionStorage.removeItem("tourney_last_selection")
        } catch { case _: Exception => }
        dom.window.setTimeout(() => {
          dom.window.location.href = Global.homeUrl
        }, 2000)
      case Left(err) => 
        val msg = if (err.msg != null && err.msg.nonEmpty) err.msg else "E-Mail-Adresse oder Passwort ungültig."
        showError(msg)

        // Dynamisches Risk-Based Captcha aktivieren bei Fehllogins
        if (Global.turnstileSitekey.nonEmpty) {
          showDynamicCaptcha()
        }
    }

  private def showError(message: String): Unit =
    val errorAlert = dom.document.getElementById("LoginErrorAlert")
    if (errorAlert != null) {
      errorAlert.textContent = message
      errorAlert.classList.remove("d-none")
    } else {
      dom.window.alert(message)
    }

  private def showDynamicCaptcha(): Unit =
    val container = dom.document.getElementById("CaptchaContainer")
    if (container != null && Global.turnstileSitekey.nonEmpty) {
      isCaptchaVisible = true
      container.innerHTML = s"""<div class="cf-turnstile mb-3 d-flex justify-content-center" data-sitekey="${Global.turnstileSitekey}"></div>"""
      container.classList.remove("d-none")
      triggerTurnstile()
    }
