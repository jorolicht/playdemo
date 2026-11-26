package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import base.*
import base.Messages.gM

/**
 * Eigener Passwort-Reset in der App (statt wp-login.php), siehe wp-plugin/includes/api/password.php.
 *
 * - Parameter "forgot": E-Mail-Adresse eingeben, Reset-Mail anfordern (Antwort immer gleich).
 * - Parameter "sent":   Hinweis „Mail ist unterwegs“ (Umleitung von wp-login.php?checkemail=confirm).
 * - Parameter "set":    Aufruf über den Link der Mail (?tourney_rp=<login>&key=<key>): Link prüfen, neues Passwort
 *                       zweimal eingeben und setzen; danach weiter zur Anmeldung.
 */
object PasswordReset extends BasePage with JsWrapper with services.Authentication:
  def name = PageNameTyp("PasswordReset")

  val EmailId:    HtmlId = genId(name)
  val Pw1Id:      HtmlId = genId(name)
  val Pw2Id:      HtmlId = genId(name)
  val BtnSend:    HtmlId = genId(name)
  val BtnSave:    HtmlId = genId(name)
  val BtnLogin:   HtmlId = genId(name)
  val MsgId:      HtmlId = genId(name)

  /** Mindestlänge des Passworts (wie Registrierung und Server). */
  val MinLength = 8

  /**
   * Zustand der Seite.
   *  - Forgot:  Formular für die E-Mail-Adresse
   *  - Sent:    Mail ist unterwegs (falls es das Konto gibt)
   *  - Checking: Link wird geprüft
   *  - Set:     Formular für das neue Passwort
   *  - Invalid: Link ungültig oder abgelaufen
   *  - Done:    Passwort gesetzt
   */
  enum Mode:
    case Forgot, Sent, Checking, Set, Invalid, Done

  private var login = ""
  private var key   = ""

  def render(param: String = ""): Boolean =
    if param == "set" then
      val q = new dom.URLSearchParams(dom.window.location.search)
      login = Option(q.get("tourney_rp")).getOrElse("")
      key   = Option(q.get("key")).getOrElse("")
      show(Mode.Checking)
      post("check-reset", js.Dynamic.literal(login = login, key = key)).map { (status, _) =>
        show(if status == 200 then Mode.Set else Mode.Invalid)
      }
    else if param == "sent" then show(Mode.Sent)
    else show(Mode.Forgot)
    true

  private def show(mode: Mode): Unit = setMain(cviews.pages.html.PasswordReset(mode))

  /** Meldung unter dem Formular (leer = ausblenden). */
  private def message(text: String): Unit =
    Option(gE(MsgId)).foreach { el =>
      el.textContent = text
      changeClass(el, text.isEmpty, "d-none")
    }

  /**
   * POST an die Auth-Endpunkte des Plugins.
   *
   * @param route Route unter tourney/v1/auth/
   * @param body  JSON-Body
   * @return HTTP-Status und Antwort
   */
  private def post(route: String, body: js.Any): Future[(Int, js.Dynamic)] =
    val init = js.Dynamic.literal(method = "POST", credentials = "include",
                                  headers = js.Dynamic.literal("Content-Type" -> "application/json"),
                                  body = js.JSON.stringify(body)).asInstanceOf[dom.RequestInit]
    dom.fetch(s"${Global.homeUrl.stripSuffix("/")}/wp-json/tourney/v1/auth/$route", init).toFuture
      .flatMap(r => r.json().toFuture.map(j => (r.status, j.asInstanceOf[js.Dynamic])).recover { case _ => (r.status, js.Dynamic.literal()) })
      .recover { case _ => (0, js.Dynamic.literal()) }

  override def handleEvent(elem: HTMLElement, event: dom.Event): Unit =
    HtmlId(elem.id) match
      case `BtnSend` =>
        event.preventDefault()
        val email = getInput(gE(EmailId)).trim
        if !isEmailValid(email) then
          message(gM("pwreset.invalidEmail"))
        else
          message("")
          val returnUrl = dom.window.location.origin + dom.window.location.pathname
          post("forgot", js.Dynamic.literal(email = email, returnUrl = returnUrl, lang = Global.lang)).map {
            case (200, _) => show(Mode.Sent)
            case (429, _) => message(gM("pwreset.tooMany"))
            case _        => message(gM("pwreset.error"))
          }

      case `BtnSave` =>
        event.preventDefault()
        val (pw1, pw2) = (getInput(gE(Pw1Id)), getInput(gE(Pw2Id)))
        if pw1.length < MinLength then message(gM("pwreset.tooShort", MinLength.toString))
        else if pw1 != pw2 then message(gM("pwreset.mismatch"))
        else
          message("")
          post("reset", js.Dynamic.literal(login = login, key = key, password = pw1)).map {
            case (200, _) =>
              // Link aus der Adresszeile entfernen, damit ein Neuladen ihn nicht erneut verwendet
              dom.window.history.replaceState(null, "", dom.window.location.pathname)
              show(Mode.Done)
            case (403, _) => show(Mode.Invalid)
            case _        => message(gM("pwreset.error"))
          }

      case `BtnLogin` =>
        event.preventDefault()
        loadPage(UserLogin.name, "")

      case _ => ()
