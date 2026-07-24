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

/**
 * Result structure for DlgCompetition
 */
case class DlgCompResult(
  competition: Competition
)

object DlgCompetition extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgCompetition")
  
  val LoadId:         HtmlId = genId(name)
  val ModalId:        HtmlId = genId(name)
  val CompNameId:     HtmlId = genId(name)
  val CompTypId:      HtmlId = genId(name)
  val CompCategoryId: HtmlId = genId(name)
  val StartDateId:    HtmlId = genId(name)
  val TtrFromId:      HtmlId = genId(name)
  val TtrToId:        HtmlId = genId(name)
  val ApplyId:        HtmlId = genId(name)
  val CancelId:       HtmlId = genId(name)
  val CloseId:        HtmlId = genId(name)

  private var modal: Modal = null

  def render(param: String = ""): Boolean = true

  /**
   * Shows the competition creation dialog.
   * @param defaultCategory The default category to select (e.g. from the current tournament)
   */
  def show(defaultCategory: CompCategory = CompCategory.UNKNOWN): Future[Either[AppError, DlgCompResult]] =
    val p = Promise[Either[AppError, DlgCompResult]]()
    val f = p.future
   
    if isEmpty(eE(LoadId, "span")) then
      setHtml(gE(LoadId), cviews.dialogs.html.DlgCompetition())
      modal = Modal(gE(ModalId))

    // Reset and Configure
    setInput(gE(CompCategoryId), defaultCategory.toString)
    setInput(gE(CompTypId), CompTyp.SINGLE.toString)

    val today = new js.Date()
    val datePart = today.toISOString().split("T")(0)
    val timePart = today.toTimeString().split(" ")(0).take(5) // HH:mm
    
    // datetime-local expects yyyy-MM-ddTHH:mm
    setInput(gE(StartDateId), s"${datePart}T$timePart")
    setInput(gE(CompNameId), "")
    
    modal.show()

    gE(ApplyId).onclick = { (_: MouseEvent) =>
      val name = getInput(gE(CompNameId))
      if (name.length < 3) {
        dom.window.alert(gM("dlg.comp.errName"))
      } else {
        val typ = CompTyp.fromString(getInput(gE(CompTypId)))
        val cat = CompCategory.valueOf(getInput(gE(CompCategoryId)))
        
        // Convert yyyy-MM-ddTHH:mm to yyyy-MM-dd HH:mm:ss
        val startRaw = getInput(gE(StartDateId))
        val startFormatted = startRaw.replace("T", " ") + ":00"
        
        val ttrFrom = try Some(getInput(gE(TtrFromId)).toInt) catch { case _:Exception => None }
        val ttrTo   = try Some(getInput(gE(TtrToId)).toInt)   catch { case _:Exception => None }
        
        val comp = Competition(
          id = CompId(0), // Temporary ID
          name = name,
          typ = typ,
          category = cat,
          startDate = startFormatted,
          status = CompStatus.CFG,
          lowLevel = ttrFrom,
          upperLevel = ttrTo
        )
        
        if (!p.isCompleted) p.success(Right(DlgCompResult(comp)))
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
