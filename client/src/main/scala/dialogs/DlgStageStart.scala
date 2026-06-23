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
 * Result structure for DlgStageStart.
 *
 * @param name The name of the new stage.
 * @param prefId The optional predecessor stage ID.
 */
case class DlgStageStartResult(
  name:   String,
  prefId: Option[StageId]
)

/**
 * Dialog for starting a new stage in a competition.
 * Displays a "Startstage" notice for the first stage and forces a predecessor
 * selection for any subsequent stages.
 */
object DlgStageStart extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgStageStart")
  
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
   * Shows the stage start dialog.
   *
   * @param existingStages List of existing stages in the competition.
   * @return A Future that completes with either an AppError or a DlgStageStartResult.
   */
  def show(existingStages: Seq[Stage], preSelectedPrefId: Option[StageId] = None): Future[Either[AppError, DlgStageStartResult]] =
    val p = Promise[Either[AppError, DlgStageStartResult]]()
    val f = p.future

    if isEmpty(eE(LoadId, "span")) then
      setHtml(gE(LoadId), cviews.dialogs.html.DlgStageStart(existingStages))
      modal = Modal(gE(ModalId))
    else
      // Update stages dropdown in case it changed
      setHtml(gE(LoadId), cviews.dialogs.html.DlgStageStart(existingStages))
      modal = Modal(gE(ModalId))
    
    setInput(gE(InputNameId), "")
    preSelectedPrefId.foreach { prefId =>
      val el = gE(InputPrefId)
      if (el != null) el.asInstanceOf[js.Dynamic].value = prefId.value.toString
    }
    
    modal.show()

    gE(ApplyId).onclick = { (_: MouseEvent) =>
      val rName = getInput(gE(InputNameId)).trim
      if (rName.length < 2) {
        dom.window.alert("Bitte geben Sie einen gültigen Namen für die Stage ein.")
      } else if (existingStages.exists(_.name.equalsIgnoreCase(rName))) {
        dom.window.alert("Eine Stage mit diesem Namen existiert bereits in diesem Wettbewerb.")
      } else {
        val prefVal = getInput(gE(InputPrefId)).toInt
        if (existingStages.nonEmpty && prefVal == 0) {
          dom.window.alert("Bitte wählen Sie eine Vorgänger-Stage aus.")
        } else {
          val prefId = if (prefVal == 0) None else Some(StageId.fromInt(prefVal))
          if (!p.isCompleted) p.success(Right(DlgStageStartResult(rName, prefId)))
          modal.hide()
        }
      }
    }

    val onCancel = { (_: MouseEvent) =>
      if (!p.isCompleted) p.success(Left(AppError("dlg.cancel")))
      modal.hide()
    }
    gE(CancelId).onclick = onCancel
    gE(CloseId).onclick = onCancel

    f
