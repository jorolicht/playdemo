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

object UserRegistration extends BasePage with JsWrapper with services.ComWrapper:
  def name = PageNameTyp("UserRegistration")

  val NameId:       HtmlId = genId(name)
  val EmailId:      HtmlId = genId(name)
  val PasswordId:   HtmlId = genId(name)
  val IsAdminCheck: HtmlId = genId(name)
  val OrganizerId:  HtmlId = genId(name)
  val BtnSubmit:    HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    setMain(cviews.pages.html.UserRegistration())
    true

  override def handleEvent(elem: HTMLElement, event: dom.Event): Unit = 
    HtmlId(elem.id) match
      case `IsAdminCheck` => 
        // Organizer-Feld ein/ausblenden
        val isChecked = elem.asInstanceOf[dom.html.Input].checked
        changeClass(gE(OrganizerId).parentElement, !isChecked, "d-none")
      
      case `BtnSubmit` => 
        doRegister()
      
      case _ => debug(s"UserRegistration Event: ${elem.id}")

  private def doRegister(): Unit =
    val nameStr = getInput(gE(NameId))
    val email   = getInput(gE(EmailId))
    val pwd     = getInput(gE(PasswordId))
    val isAdmin = gE(IsAdminCheck).asInstanceOf[dom.html.Input].checked
    val org     = if (isAdmin) getInput(gE(OrganizerId)) else ""

    // Validierung
    if (nameStr.length < 3) { dom.window.alert("Name zu kurz"); return }
    if (!email.contains("@")) { dom.window.alert("Email ungültig"); return }
    if (pwd.length < 8) { dom.window.alert("Passwort muss mind. 8 Zeichen haben"); return }
    if (isAdmin && org.length < 6) { dom.window.alert("Veranstalter-Name muss mind. 6 Zeichen haben"); return }

    // Turnstile Token (falls konfiguriert)
    val turnstileElements = dom.document.getElementsByName("cf-turnstile-response")
    val turnstileToken = if (turnstileElements.length > 0) {
      turnstileElements(0).asInstanceOf[dom.html.Input].value
    } else ""

    // API Call
    val data = Map(
      "name"      -> nameStr,
      "email"     -> email,
      "password"  -> pwd,
      "role"      -> (if (isAdmin) "turnier_admin" else "subscriber"),
      "organizer" -> org,
      "turnstileToken" -> turnstileToken
    )

    ajaxPost[Map[String, String], Map[String, String]]("/wp-json/tourney/v1/auth/register", List(), data, hdrs = Map("Content-Type" -> "application/json"), host = Global.homeUrl).map {
      case Right(_) => 
        dom.window.alert("Registrierung fast abgeschlossen! Bitte prüfen Sie Ihre E-Mails zur Verifizierung.")
        loadPage(Auth.name, "") // Zurück zum Login
      case Left(err) => 
        dom.window.alert(s"Fehler: ${err}")
    }
