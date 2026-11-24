package dialogs

import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.{ MouseEvent, Event }
import org.scalajs.dom.html.Input
import org.scalajs.dom.raw.HTMLElement

import base.*
import base.Bootstrap.*
import pages.BasePage
import shared.model.*
import shared.basic.AppError

/**
 * Öffentliche Anmeldung zu einem Wettbewerb (Einzel) auf der Turnierseite (TourneyWelcome).
 *
 * Pflichtangaben: Name, Vorname, Verein, TTR; optional: Geburtsjahrgang, E-Mail, WhatsApp-Nummer.
 * Abgesichert durch Captcha (Cloudflare Turnstile, sonst einfache Rechenaufgabe); der Server prüft Captcha,
 * Eingaben und ob die Anmeldung offen ist (public-register) und legt Verein, Spieler und Teilnehmer an.
 */
object DlgPublicRegistration extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgPublicRegistration")

  val LoadId:   HtmlId = genId(name)
  val ModalId:  HtmlId = genId(name)
  val ApplyId:  HtmlId = genId(name)
  val CancelId: HtmlId = genId(name)
  val CloseId:  HtmlId = genId(name)

  private var modal: Modal = null

  def render(param: String = ""): Boolean = true

  /** Angaben aus dem Formular. */
  private case class Form(lastname: String, firstname: String, club: String, ttr: Option[Int],
                          birthYear: Option[Int], email: String, whatsApp: String)

  /**
   * Hinweis für Spieler nach der Anmeldung (Turnier-Administration, Reiter "Homepage").
   *
   * @return Text des Turnierleiters, ohne eigene Angabe der Standardtext, "" = kein Hinweis
   */
  def registrationHint: String =
    Global.currentAci.registrationHint.getOrElse(gM("reg.defaultHint")).trim

  /** Liest das Formular. */
  private def readForm(): Form =
    def v(id: String) = getInput(gE(HtmlId(id))).trim
    Form(v("reg-lastname"), v("reg-firstname"), v("reg-club"), v("reg-ttr").toIntOption,
         v("reg-birthyear").toIntOption, v("reg-email"), v("reg-whatsapp"))

  /**
   * Prüft die Angaben.
   *
   * @return Fehlertext oder None
   */
  private def validate(f: Form): Option[String] =
    val year = new js.Date().getFullYear().toInt
    if (f.lastname.isEmpty || f.firstname.isEmpty || f.club.isEmpty || f.ttr.isEmpty) Some(gM("reg.errRequired"))
    else if (f.ttr.exists(t => t < 0 || t > 3500)) Some(gM("reg.errTtr"))
    else if (f.birthYear.exists(y => y < 1900 || y > year)) Some(gM("reg.errBirthYear"))
    else if (getInput(gE(HtmlId("reg-birthyear"))).trim.nonEmpty && f.birthYear.isEmpty) Some(gM("reg.errBirthYear"))
    else if (f.email.nonEmpty && !f.email.matches("""[^@\s]+@[^@\s]+\.[^@\s]+""")) Some(gM("reg.errEmail"))
    else if (f.whatsApp.nonEmpty && !f.whatsApp.matches("""\+?[0-9 ()/-]{6,25}""")) Some(gM("reg.errWhatsApp"))
    else None

  /**
   * Zeigt den Anmeldedialog für einen Wettbewerb.
   *
   * @param tourney Turnier
   * @param comp    Wettbewerb (Einzel)
   * @return Right(Spieler) nach erfolgreicher Anmeldung, Left bei Abbruch oder Fehler
   */
  def show(tourney: Tourney, comp: Competition): Future[Either[AppError, Player]] =
    val p = Promise[Either[AppError, Player]]()
    val f = p.future

    // bei jedem Öffnen neu aufbauen (Wettbewerb im Kopf, leeres Formular, neues Captcha)
    setHtml(eE[HTMLElement](LoadId, "span"), cviews.dialogs.html.DlgPublicRegistration(tourney, comp))
    modal = Modal(gE(ModalId))
    if (Global.turnstileSitekey.nonEmpty) triggerTurnstile()
    modal.show()

    val errorAlert = gE(HtmlId("reg-error-alert"))
    def showError(msg: String): Unit =
      errorAlert.textContent = msg
      errorAlert.classList.remove("d-none")
    def hideError(): Unit =
      errorAlert.textContent = ""
      errorAlert.classList.add("d-none")

    val applyBtn = gE(ApplyId)

    // Zeigt die Erfolgsmeldung. Mit Hinweis des Turnierleiters bleibt der Dialog offen, bis der Spieler ihn
    // schließt, sonst schließt er sich nach kurzer Zeit; Ergebnis ist der angemeldete Spieler.
    // waitPos > 0: Platz auf der Warteliste (statt Hinweis des Turnierleiters ein Hinweis zur Warteliste)
    def finish(form: Form, player: Player, waitPos: Int): Unit =
      gE(HtmlId("reg-step-1")).classList.add("d-none")
      gE(HtmlId("reg-step-3")).classList.remove("d-none")
      applyBtn.classList.add("d-none")
      gE(HtmlId("reg-success-msg")).textContent =
        if waitPos > 0 then gM("reg.waitlistMsg", form.firstname, comp.name, waitPos.toString)
        else gM("reg.successMsg", form.firstname, comp.name)
      if waitPos > 0 then gE(HtmlId("reg-success-title")).textContent = gM("reg.waitlistTitle")
      val hint =
        if waitPos > 0 then gM(if form.email.nonEmpty then "reg.waitlistHintMail" else "reg.waitlistHint")
        else registrationHint
      if (hint.isEmpty) {
        dom.window.setTimeout(() => {
          modal.hide()
          p.trySuccess(Right(player))
        }, 1800)
      } else {
        val hintElem = gE(HtmlId("reg-success-hint"))
        hintElem.textContent = hint
        hintElem.classList.remove("d-none")
        val closeBtn = gE(CancelId)
        closeBtn.textContent = gM("btn.close")
        def done(): Unit = { modal.hide(); p.trySuccess(Right(player)) }
        closeBtn.onclick = (_: MouseEvent) => done()
        gE(CloseId).onclick = (_: MouseEvent) => done()
      }

    applyBtn.onclick = { (_: MouseEvent) =>
      hideError()
      val form = readForm()
      validate(form) match
        case Some(msg) => showError(msg)
        case None =>
          // Captcha
          val token =
            if (Global.turnstileSitekey.nonEmpty) {
              val elems = dom.document.getElementsByName("cf-turnstile-response")
              if (elems.length > 0) elems(0).asInstanceOf[Input].value else ""
            } else ""
          val captchaOk =
            if (Global.turnstileSitekey.nonEmpty) token.nonEmpty
            else getInput(gE(HtmlId("reg-captcha-fallback"))).trim == "7"
          if (!captchaOk) showError(gM("reg.errCaptcha"))
          else register(tourney, comp, form, token, applyBtn, showError, finish)
    }

    gE(CancelId).onclick = { (_: MouseEvent) =>
      p.trySuccess(Left(AppError("registration.cancelled", "Anmeldung abgebrochen")))
    }
    gE(CloseId).onclick = { (_: MouseEvent) =>
      p.trySuccess(Left(AppError("registration.cancelled", "Anmeldung abgebrochen")))
    }
    f

  /**
   * Meldet an: Cloud-Turnier über den Server, Demo-/Lokalmodus im lokalen Modell.
   */
  private def register(tourney: Tourney, comp: Competition, form: Form, token: String, applyBtn: HTMLElement,
                       showError: String => Unit, finish: (Form, Player, Int) => Unit): Unit =
    val email    = Option(form.email).filter(_.nonEmpty)
    val whatsApp = Option(form.whatsApp).filter(_.nonEmpty)
    val ttr      = form.ttr.getOrElse(0)
    if (!Global.isDemoMode && !Global.isLocalMode && tourney.wpId != 0) {
      val similarClub = Club.findSimilar(form.club, tourney.clubs).map(_._1.toInt).getOrElse(0)
      val req = services.PlayerDB.PublicRegisterRequest(
        comp.id.value, form.firstname, form.lastname, form.club, similarClub, Club.normalize(form.club), ttr,
        form.email, token, form.birthYear.getOrElse(0), form.whatsApp, if Global.lang == "en" then "en" else "de"
      )
      applyBtn.setAttribute("disabled", "true")
      services.PlayerDB.publicRegister(req).foreach { res =>
        applyBtn.removeAttribute("disabled")
        res match {
          case Right((playerId, waitPos)) =>
            finish(form, tourney.players.find(_.id == playerId)
              .getOrElse(Player(id = playerId, firstName = form.firstname, lastName = form.lastname)), waitPos)
          case Left(err) if err.is("registration_closed") => showError(gM("reg.errClosed"))
          case Left(err) if err.is("captcha_failed")      => showError(gM("reg.errCaptchaServer"))
          case Left(err) if err.is("invalid_input")       => showError(gM("reg.errRequired"))
          case Left(_)                                    => showError(gM("reg.errSave"))
        }
      }
    } else {
      // Demo-/Lokalmodus: im lokalen Modell anmelden
      val clubId = tourney.addClub(form.club).map(_.id.toInt).getOrElse(1)
      val player = tourney.players.find(p => p.firstName == form.firstname && p.lastName == form.lastname && p.clubId == clubId)
        .getOrElse {
          tourney.addPlayer(form.firstname, form.lastname, clubId, birthYear = form.birthYear, email = email,
                            whatsApp = whatsApp, doSync = true)
            .getOrElse(Player(id = tourney.nextPlayerId(), firstName = form.firstname, lastName = form.lastname, clubId = clubId))
        }
      val full = comp.isFull
      val pant = Pant(id = SNO.single(player.id), name = s"${player.lastName}, ${player.firstName}", club = form.club,
                      rating = ttr, status = if full then PantStatus.WAIT else PantStatus.REGI, active = !full, clubId = clubId)
      if (!comp.pants1Stage.exists(_.id == pant.id)) comp.pants1Stage += pant
      services.PlayerDB.triggerSync(tourney.players.toSeq)
      services.CompetitionDB.triggerSync(tourney.competitions.toSeq)
      finish(form, player, comp.waitPosition(pant.id))
    }
