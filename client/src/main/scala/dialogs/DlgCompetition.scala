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

/**
 * Result structure for DlgCompetition
 */
case class DlgCompResult(
  competition: Competition,
  tourneyTyp:  Option[TourneyTyp] = None
)

object DlgCompetition extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgCompetition")
  
  val LoadId:         HtmlId = genId(name)
  val ModalId:        HtmlId = genId(name)
  val TourneySecId:   HtmlId = genId(name)
  val TourneyTypId:   HtmlId = genId(name)
  val CompNameId:     HtmlId = genId(name)
  val CompTypId:      HtmlId = genId(name)
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
   * @param isOption2 If true, shows the TourneyTyp selection for quick-start.
   */
  def show(isOption2: Boolean = false): Future[Either[AppError, DlgCompResult]] =
    val p = Promise[Either[AppError, DlgCompResult]]()
    val f = p.future

    if getData(gE(LoadId), "loaded", false) == false then
      setData(gE(LoadId), "loaded", true)
      setHtml(gE(LoadId), cviews.dialogs.html.DlgCompetition())
      modal = Modal(gE(ModalId))
    
    // Reset and Configure
    if (isOption2) removeClass(gE(TourneySecId), "d-none")
    else addClass(gE(TourneySecId), "d-none")

    val todayStr = new js.Date().toISOString().split("T")(0)
    setInput(gE(StartDateId), todayStr)
    setInput(gE(CompNameId), "")
    
    modal.show()

    gE(ApplyId).onclick = { (_: MouseEvent) =>
      val name = getInput(gE(CompNameId))
      if (name.length < 3) {
        dom.window.alert("Bitte geben Sie einen gültigen Namen ein.")
      } else {
        val typ = CompTyp.fromString(getInput(gE(CompTypId)))
        val start = getInput(gE(StartDateId))
        val ttrFrom = try Some(getInput(gE(TtrFromId)).toInt) catch { case _:Exception => None }
        val ttrTo   = try Some(getInput(gE(TtrToId)).toInt)   catch { case _:Exception => None }
        
        val comp = Competition.dummy.copy(
          id = CompId(0), // Temporary ID
          name = name,
          typ = typ,
          startDate = start,
          status = CompStatus.CFG,
          lowLevel = ttrFrom,
          upperLevel = ttrTo
        )

        val tTyp = if (isOption2) Some(TourneyTyp.valueOf(getInput(gE(TourneyTypId)))) else None
        
        if (!p.isCompleted) p.success(Right(DlgCompResult(comp, tTyp)))
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
