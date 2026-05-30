package dialogs

import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.{ MouseEvent, Event }
import org.scalajs.dom.raw.HTMLElement

import base.*
import base.Bootstrap.*
import shared.model.*
import services.*

/**
 * Result structure for DlgRoundStart
 */
case class DlgRoundStartResult(
  name:   String,
  prefId: Option[RoundId]
)

object DlgRoundStart extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgRoundStart")
  
  val LoadId:         HtmlId = genId(name)
  val FormId:         HtmlId = genId(name)
  val ModalId:        HtmlId = genId(name)
  val InputNameId:    HtmlId = genId(name)
  val InputPrefId:    HtmlId = genId(name)
  val ApplyId:        HtmlId = genId(name)
  val CancelId:       HtmlId = genId(name)
  val CloseId:        HtmlId = genId(name)
  val StartLabelId:   HtmlId = genId(name)

  private var modal: Modal = null

  def render(param: String = ""): Boolean = true

  /**
   * Shows the round start dialog.
   * @param existingRounds List of existing rounds in the competition.
   */
  def show(existingRounds: Seq[Round]): Future[Either[AppError, DlgRoundStartResult]] =
    val p = Promise[Either[AppError, DlgRoundStartResult]]()
    val f = p.future

    if isEmpty(eE(LoadId, "span")) then
      setHtml(gE(LoadId), cviews.dialogs.html.DlgRoundStart(existingRounds))
      modal = Modal(gE(ModalId))
    else
      // Update rounds dropdown in case it changed
      setHtml(gE(LoadId), cviews.dialogs.html.DlgRoundStart(existingRounds))
      modal = Modal(gE(ModalId))
    
    setInput(gE(InputNameId), "")
    
    modal.show()

    gE(ApplyId).onclick = { (_: MouseEvent) =>
      val rName = getInput(gE(InputNameId)).trim
      if (rName.length < 2) {
        dom.window.alert("Bitte geben Sie einen gültigen Namen für die Runde ein.")
      } else if (existingRounds.exists(_.name.equalsIgnoreCase(rName))) {
        dom.window.alert("Eine Runde mit diesem Namen existiert bereits in diesem Wettbewerb.")
      } else {
        val prefVal = getInput(gE(InputPrefId)).toInt
        val prefId = if (prefVal == 0) None else Some(RoundId.fromInt(prefVal))
        
        if (!p.isCompleted) p.success(Right(DlgRoundStartResult(rName, prefId)))
        modal.hide()
      }
    }

    val onCancel = { (_: MouseEvent) =>
      if (!p.isCompleted) p.success(Left(AppError("dlg.cancel")))
      modal.hide()
    }
    gE(CancelId).onclick = onCancel
    gE(CloseId).onclick = onCancel

    f
