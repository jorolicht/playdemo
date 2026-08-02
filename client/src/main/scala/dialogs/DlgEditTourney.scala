package dialogs

import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.{ MouseEvent, Event }
import org.scalajs.dom.raw.HTMLElement

import base.*
import base.Bootstrap.*
import base.Messages.gM
import shared.model.*
import shared.DomTypes.HtmlId
import shared.basic.AppError

/**
 * Dialog for editing existing tournament details (Contact Person & Venue/Address).
 * Allows modifying contact and venue info in-place without navigating away.
 */
object DlgEditTourney extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgEditTourney")

  val LoadId:         HtmlId = genId(name)
  val ModalId:        HtmlId = genId(name)
  val TitleId:        HtmlId = genId(name)
  
  val ContactFnameId: HtmlId = genId(name)
  val ContactLnameId: HtmlId = genId(name)
  val ContactPhoneId: HtmlId = genId(name)
  val ContactEmailId: HtmlId = genId(name)

  val AddrDescId:     HtmlId = genId(name)
  val AddrStreetId:   HtmlId = genId(name)
  val AddrCountryId:  HtmlId = genId(name)
  val AddrZipId:      HtmlId = genId(name)
  val AddrCityId:     HtmlId = genId(name)

  val ApplyId:        HtmlId = genId(name)
  val CancelId:       HtmlId = genId(name)
  val CloseId:        HtmlId = genId(name)

  private var modal: Modal = null
  private var activeTourney: Tourney = null

  def render(param: String = ""): Boolean = true

  /**
   * Shows the tournament edit dialog for contact and address details.
   *
   * @param tourney The existing tournament to edit.
   * @return Future containing Right(updatedTourney) on success, or Left(AppError) on cancel/error.
   */
  def show(tourney: Tourney): Future[Either[AppError, Tourney]] =
    val p = Promise[Either[AppError, Tourney]]()
    val f = p.future
    activeTourney = tourney

    setHtml(eE(LoadId, "span"), cviews.dialogs.html.DlgEditTourney(tourney))
    modal = Modal(gE(ModalId))

    // Event listener for Apply (Save) button
    gE(ApplyId).onclick = (e: MouseEvent) => {
      e.preventDefault()

      def getValue(id: HtmlId): String = 
        val elem = gE(id, withWarning = false).asInstanceOf[dom.html.Input]
        if (elem != null) elem.value.trim else ""

      val contact = Contact(
        lastname = getValue(ContactLnameId),
        firstname = getValue(ContactFnameId),
        phone = getValue(ContactPhoneId),
        email = getValue(ContactEmailId)
      )

      val address = Address(
        description = getValue(AddrDescId),
        country = if (getValue(AddrCountryId).nonEmpty) getValue(AddrCountryId) else "DE",
        zip = getValue(AddrZipId),
        city = getValue(AddrCityId),
        street = getValue(AddrStreetId)
      )

      activeTourney.contact = Some(contact)
      activeTourney.address = Some(address)

      val feedback = dom.document.getElementById("dlgEditTourneyFeedback")
      if (feedback != null) {
        feedback.innerHTML = s"<div class='alert alert-info py-2 small mb-0'>Speichere Änderungen...</div>"
      }

      services.TourneyDB.update(activeTourney)
      services.TourneyDB.sync().map { _ =>
        modal.hide()
        p.trySuccess(Right(activeTourney))
      }.recover { case ex: Throwable =>
        if (feedback != null) {
          feedback.innerHTML = s"<div class='alert alert-danger py-2 small mb-0'>Fehler beim Speichern: ${ex.getMessage}</div>"
        }
      }
    }

    // Event listeners for Cancel / Close buttons
    val closeHandler = (e: Event) => {
      modal.hide()
      p.trySuccess(Left(AppError("cancelled")))
    }

    val cancelBtn = gE(CancelId, withWarning = false)
    if (cancelBtn != null) cancelBtn.onclick = closeHandler
    
    val closeBtn = gE(CloseId, withWarning = false)
    if (closeBtn != null) closeBtn.onclick = closeHandler

    modal.show()
    f
