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

  val EmailId:            HtmlId = genId(name)
  val PasswordlessSwitch: HtmlId = genId(name)
  val PasswordContainer:  HtmlId = genId(name)
  val PasswordId:         HtmlId = genId(name)
  val IsAdminCheck:       HtmlId = genId(name)
  val OrganizerId:        HtmlId = genId(name)
  val BtnSubmit:          HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    setMain(cviews.pages.html.UserRegistration())
    triggerTurnstile()
    true

  override def handleEvent(elem: HTMLElement, event: dom.Event): Unit = 
    HtmlId(elem.id) match
      case `IsAdminCheck` => 
        // Organizer-Feld ein/ausblenden
        val isChecked = elem.asInstanceOf[dom.html.Input].checked
        changeClass(gE(OrganizerId).parentElement, !isChecked, "d-none")

      case `PasswordlessSwitch` =>
        val isPasswordless = elem.asInstanceOf[dom.html.Input].checked
        changeClass(gE(PasswordContainer), isPasswordless, "d-none")
      
      case `BtnSubmit` => 
        doRegister()
      
      case _ => debug(s"UserRegistration Event: ${elem.id}")

  private def doRegister(): Unit =
    val email   = getInput(gE(EmailId))
    val isPasswordless = gE(PasswordlessSwitch).asInstanceOf[dom.html.Input].checked
    val pwd     = if (!isPasswordless) getInput(gE(PasswordId)) else ""
    val isAdmin = gE(IsAdminCheck).asInstanceOf[dom.html.Input].checked
    val org     = if (isAdmin) getInput(gE(OrganizerId)) else ""

    // Validierung
    if (!email.contains("@")) { dom.window.alert("Email ungültig"); return }
    if (!isPasswordless && pwd.length < 8) { dom.window.alert("Passwort muss mind. 8 Zeichen haben"); return }
    if (isAdmin && org.length < 6) { dom.window.alert("Veranstalter-Name muss mind. 6 Zeichen haben"); return }

    // Turnstile Token (falls konfiguriert)
    val turnstileElements = dom.document.getElementsByName("cf-turnstile-response")
    val turnstileToken = if (turnstileElements.length > 0) {
      turnstileElements(0).asInstanceOf[dom.html.Input].value
    } else ""

    // API Call
    val data = Map(
      "email"     -> email,
      "password"  -> pwd,
      "role"      -> (if (isAdmin) "turnier_admin" else "subscriber"),
      "organizer" -> org,
      "turnstileToken" -> turnstileToken
    )

    ajaxPost[Map[String, String], Map[String, String]]("/wp-json/tourney/v1/auth/register", List(), data, hdrs = Map("Content-Type" -> "application/json"), host = Global.homeUrl).flatMap {
      case Right(res) => 
        val webauthnArgsOpt = res.get("webauthn_args")
        val userIdOpt = res.get("user_id")

        if (webauthnArgsOpt.isDefined && userIdOpt.isDefined) {
          try {
            val argsStr = webauthnArgsOpt.get
            val userId = userIdOpt.get

            val resJson = js.JSON.parse(argsStr).asInstanceOf[js.Dynamic]
            val publicKey = resJson.publicKey

            val credentialOptions = services.WebAuthnService.transformCreateArgs(publicKey)
            val credentials = dom.window.navigator.asInstanceOf[js.Dynamic].credentials
            val promise = credentials.create(credentialOptions).asInstanceOf[js.Promise[js.Dynamic]]

            promise.toFuture.flatMap { credential =>
              val registerData = services.WebAuthnService.transformCreateResponse(credential) + ("user_id" -> userId)

              ajaxPost[Map[String, String], Map[String, String]](
                "/wp-json/tourney/v1/auth/webauthn/register-public", 
                List(), 
                registerData, 
                host = Global.homeUrl
              ).map {
                case Right(_) => 
                  loadPage(RegistrationSuccess.name, "")
                case Left(err) => 
                  dom.window.alert(s"Fehler beim Registrieren des Passkeys: ${err.msg}")
                  loadPage(RegistrationSuccess.name, "")
              }
            }.recover {
              case e: Throwable =>
                debug(s"WebAuthn registration cancelled/failed: ${e.getMessage}")
                loadPage(RegistrationSuccess.name, "")
            }
          } catch {
            case e: Exception =>
              debug(s"Parsing error during passkey creation: ${e.getMessage}")
              Future.successful(loadPage(RegistrationSuccess.name, ""))
          }
        } else {
          Future.successful(loadPage(RegistrationSuccess.name, ""))
        }
      case Left(err) => 
        dom.window.alert(s"Fehler: ${err}")
        Future.successful(())
    }
