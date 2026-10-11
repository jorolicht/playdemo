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
import shared.DomTypes.HtmlId

/**
 * Target storage mode for new tournaments.
 */
enum TourneyTargetMode:
  case Local, Cloud

/**
 * Dialog for selecting whether to create a tournament locally (in-browser) or in the Cloud (on server).
 * If the user is not authenticated, the cloud option is disabled.
 */
object DlgTourneyNewMode extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgTourneyNewMode")

  val LoadId:       HtmlId = genId(name)
  val ModalId:      HtmlId = genId(name)
  val TitleId:      HtmlId = genId(name)
  val RadioLocalId: HtmlId = genId(name)
  val RadioCloudId: HtmlId = genId(name)
  val ApplyId:      HtmlId = genId(name)
  val CancelId:     HtmlId = genId(name)
  val CloseId:      HtmlId = genId(name)

  private var modal: Modal = null

  def render(param: String = ""): Boolean = true

  /**
   * Shows the tournament target mode selection dialog.
   *
   * @param isLoggedIn Whether the current user is logged in.
   * @param defaultLocal Whether local mode should be checked by default.
   * @return Future containing Some(TourneyTargetMode) if confirmed, or None if cancelled.
   */
  def show(isLoggedIn: Boolean, defaultLocal: Boolean = true): Future[Option[TourneyTargetMode]] =
    val p = Promise[Option[TourneyTargetMode]]()

    setHtml(getOrCreateDiv(LoadId), cviews.dialogs.html.DlgTourneyNewMode(isLoggedIn, defaultLocal))
    val modalElem = gE(ModalId)
    modal = Modal(modalElem)

    def doConfirm(): Unit =
      val cloudRadio = gE(RadioCloudId, withWarning = false).asInstanceOf[dom.html.Input]
      val selectedMode = if (cloudRadio != null && cloudRadio.checked && isLoggedIn) {
        TourneyTargetMode.Cloud
      } else {
        TourneyTargetMode.Local
      }
      if (!p.isCompleted) p.trySuccess(Some(selectedMode))
      modal.hide()

    val applyBtn = gE(ApplyId, withWarning = false)
    if (applyBtn != null) {
      applyBtn.onclick = (e: MouseEvent) => {
        e.preventDefault()
        doConfirm()
      }
    }

    val cancelHandler = (e: MouseEvent) => {
      e.preventDefault()
      if (!p.isCompleted) p.trySuccess(None)
      modal.hide()
    }

    val cancelBtn = gE(CancelId, withWarning = false)
    if (cancelBtn != null) cancelBtn.onclick = cancelHandler

    val closeBtn = gE(CloseId, withWarning = false)
    if (closeBtn != null) closeBtn.onclick = cancelHandler

    modalElem.addEventListener("hidden.bs.modal", (_: Event) => {
      if (!p.isCompleted) p.trySuccess(None)
    })

    modal.show()
    p.future
