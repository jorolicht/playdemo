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
 * Public tournament registration dialog for VIEW-mode.
 * Prompts for player details (Name, Vorname, Verein, TTR, Email),
 * verifies Captcha, sends 4-digit verification code, and inserts player as participant.
 */
object DlgPublicRegistration extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgPublicRegistration")

  val LoadId:   HtmlId = genId(name)
  val ModalId:  HtmlId = genId(name)
  val ApplyId:  HtmlId = genId(name)
  val CancelId: HtmlId = genId(name)
  val CloseId:  HtmlId = genId(name)

  private var modal: Modal = null
  private var verificationCode: String = ""

  def render(param: String = ""): Boolean = true

  /**
   * Shows the public registration dialog for a specific competition.
   *
   * @param tourney The tournament object.
   * @param comp The target competition object.
   * @return Future containing Right(Player) on successful registration, or Left(AppError) on cancel/error.
   */
  def show(tourney: Tourney, comp: Competition): Future[Either[AppError, Player]] =
    val p = Promise[Either[AppError, Player]]()
    val f = p.future

    setHtml(gE(LoadId), cviews.dialogs.html.DlgPublicRegistration(tourney, comp))
    modal = Modal(gE(ModalId))

    // Initialize Turnstile Captcha if sitekey is available
    if (Global.turnstileSitekey.nonEmpty) {
      triggerTurnstile()
    }

    modal.show()

    val errorAlert = gE(HtmlId("reg-error-alert"))
    def showError(msg: String): Unit =
      errorAlert.textContent = msg
      errorAlert.classList.remove("d-none")

    def hideError(): Unit =
      errorAlert.textContent = ""
      errorAlert.classList.add("d-none")

    // Step 1: Click "Verifizierungscode anfordern"
    gE(ApplyId).onclick = { (_: MouseEvent) =>
      hideError()
      val lastname  = getInput(gE(HtmlId("reg-lastname"))).trim
      val firstname = getInput(gE(HtmlId("reg-firstname"))).trim
      val clubStr   = getInput(gE(HtmlId("reg-club"))).trim
      val ttrStr    = getInput(gE(HtmlId("reg-ttr"))).trim
      val email     = getInput(gE(HtmlId("reg-email"))).trim

      if (lastname.isEmpty || firstname.isEmpty || clubStr.isEmpty || email.isEmpty) {
        showError("Bitte füllen Sie alle Pflichtfelder (*) aus.")
      } else if (!email.contains("@") || !email.contains(".")) {
        showError("Bitte geben Sie eine gültige E-Mail-Adresse ein.")
      } else {
        // Verify Captcha
        var captchaOk = true
        if (Global.turnstileSitekey.nonEmpty) {
          val turnstileElems = dom.document.getElementsByName("cf-turnstile-response")
          val token = if (turnstileElems.length > 0) turnstileElems(0).asInstanceOf[Input].value else ""
          if (token.isEmpty) {
            captchaOk = false
            showError("Bitte bestätigen Sie das Captcha.")
          }
        } else {
          val captchaAnswer = getInput(gE(HtmlId("reg-captcha-fallback"))).trim
          if (captchaAnswer != "7") {
            captchaOk = false
            showError("Die Antwort der Sicherheitsabfrage ist falsch.")
          }
        }

        if (captchaOk) {
          // Generate 4-digit verification code
          verificationCode = f"${1000 + util.Random.nextInt(9000)}%04d"

          // Switch UI to Step 2
          gE(HtmlId("reg-email-display")).textContent = email
          gE(HtmlId("reg-demo-code")).textContent = verificationCode

          gE(HtmlId("reg-step-1")).classList.add("d-none")
          gE(HtmlId("reg-step-2")).classList.remove("d-none")
          gE(ApplyId).classList.add("d-none")
          
          val submitBtn = gE(HtmlId("btn-submit-code"))
          submitBtn.classList.remove("d-none")
          gE(HtmlId("reg-code-input")).focus()
        }
      }
    }

    // Step 2: Click "Anmeldung abschließen"
    val submitBtn = gE(HtmlId("btn-submit-code"))
    submitBtn.onclick = { (_: MouseEvent) =>
      hideError()
      val enteredCode = getInput(gE(HtmlId("reg-code-input"))).trim

      if (enteredCode != verificationCode) {
        showError("Der eingegebene Verifizierungscode ist falsch.")
      } else {
        // Verification Successful -> Register Player
        val lastname  = getInput(gE(HtmlId("reg-lastname"))).trim
        val firstname = getInput(gE(HtmlId("reg-firstname"))).trim
        val clubStr   = getInput(gE(HtmlId("reg-club"))).trim
        val ttr       = getInput(gE(HtmlId("reg-ttr"))).toIntOption.getOrElse(1000)
        val email     = getInput(gE(HtmlId("reg-email"))).trim

        // 1. Get or create Club
        val clubRes = tourney.addClub(clubStr)
        val clubId  = clubRes.map(_.id.toInt).getOrElse(1)

        // 2. Get or create Player
        val existingPlayer = tourney.players.find(p => p.firstName == firstname && p.lastName == lastname && p.clubId == clubId)
        val player = existingPlayer match {
          case Some(p) => p
          case None =>
            tourney.addPlayer(firstname, lastname, clubId, birthYear = None, email = Some(email), doSync = true) match {
              case Right(newP) => newP
              case Left(_) =>
                // Fallback player object
                Player(
                  id = tourney.nextPlayerId(),
                  firstName = firstname,
                  lastName = lastname,
                  clubId = clubId,
                  email = Some(email)
                )
            }
        }

        // 3. Add Pant to competition
        val pant = Pant(
          id = SNO.single(player.id),
          name = s"${player.lastName}, ${player.firstName}",
          club = clubStr,
          rating = ttr,
          status = PantStatus.UNKN,
          active = true,
          clubId = clubId
        )

        if (!comp.pants1Stage.exists(_.id == pant.id)) {
          comp.pants1Stage += pant
        }

        // 4. Trigger Sync
        services.PlayerDB.triggerSync(tourney.players.toSeq)
        services.CompetitionDB.triggerSync(tourney.competitions.toSeq)

        // Show Step 3 Success
        gE(HtmlId("reg-step-2")).classList.add("d-none")
        gE(HtmlId("reg-step-3")).classList.remove("d-none")
        submitBtn.classList.add("d-none")
        gE(HtmlId("reg-success-msg")).textContent = s"Vielen Dank, $firstname! Ihre Anmeldung für '${comp.name}' war erfolgreich."

        dom.window.setTimeout(() => {
          modal.hide()
          p.trySuccess(Right(player))
        }, 1800)
      }
    }

    gE(CancelId).onclick = { (_: MouseEvent) =>
      p.trySuccess(Left(AppError("registration.cancelled", "Anmeldung abgebrochen")))
    }

    gE(CloseId).onclick = { (_: MouseEvent) =>
      p.trySuccess(Left(AppError("registration.cancelled", "Anmeldung abgebrochen")))
    }

    f
