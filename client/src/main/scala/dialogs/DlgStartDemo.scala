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
 * Bestätigungsdialog vor dem lokalen Starten eines Demo-Turniers.
 * Weist den Benutzer mit einer Warnmeldung darauf hin, dass das aktuelle lokale Turnier gelöscht wird.
 */
object DlgStartDemo extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgStartDemo")

  val LoadId:   HtmlId = genId(name)
  val ModalId:  HtmlId = genId(name)
  val TitleId:  HtmlId = genId(name)
  val PromptId: HtmlId = genId(name)
  val ApplyId:  HtmlId = genId(name)
  val CancelId: HtmlId = genId(name)
  val CloseId:  HtmlId = genId(name)

  private var modal: Modal = null

  def render(param: String = ""): Boolean = true

  /**
   * Zeigt den Bestätigungsdialog zum lokalen Starten des Demos an.
   *
   * @return Future mit true, wenn "DEMO STARTEN" geklickt wurde, andernfalls false bei "ABBRECHEN" oder Schließen.
   */
  def show(): Future[Boolean] =
    val p = Promise[Boolean]()

    setHtml(getOrCreateDiv(LoadId), cviews.dialogs.html.DlgStartDemo())
    val modalElem = gE(ModalId)
    modal = Modal(modalElem)

    val applyBtn = gE(ApplyId, withWarning = false)
    if (applyBtn != null) {
      applyBtn.onclick = (e: MouseEvent) => {
        e.preventDefault()
        if (!p.isCompleted) p.trySuccess(true)
        modal.hide()
      }
    }

    val cancelHandler = (e: MouseEvent) => {
      e.preventDefault()
      if (!p.isCompleted) p.trySuccess(false)
      modal.hide()
    }

    val cancelBtn = gE(CancelId, withWarning = false)
    if (cancelBtn != null) cancelBtn.onclick = cancelHandler

    val closeBtn = gE(CloseId, withWarning = false)
    if (closeBtn != null) closeBtn.onclick = cancelHandler

    modalElem.addEventListener("hidden.bs.modal", (_: Event) => {
      if (!p.isCompleted) p.trySuccess(false)
    })

    modal.show()
    p.future
