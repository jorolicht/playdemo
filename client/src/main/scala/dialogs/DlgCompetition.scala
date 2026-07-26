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
  val TitleId:        HtmlId = genId(name)
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
   * @param existingComp Optional existing competition to edit
   */
  def show(
    defaultCategory: CompCategory = CompCategory.UNKNOWN,
    existingComp: Option[Competition] = None,
    validate: Option[Competition => Future[Either[AppError, Unit]]] = None
  ): Future[Either[AppError, DlgCompResult]] =
    val p = Promise[Either[AppError, DlgCompResult]]()
    val f = p.future
   
    if isEmpty(eE(LoadId, "span")) then
      setHtml(gE(LoadId), cviews.dialogs.html.DlgCompetition())
      modal = Modal(gE(ModalId))

    existingComp match {
      case Some(comp) =>
        setHtml(gE(TitleId), gM("dlg.comp.titleEdit"))
        setHtml(gE(ApplyId), gM("btn.save"))
        setInput(gE(CompCategoryId), comp.category.toString)
        setInput(gE(CompTypId), comp.typ.toString)

        val formattedDate = if (comp.startDate.contains(" ")) {
          comp.startDate.replace(" ", "T").take(16)
        } else if (comp.startDate.length == 8) {
          val y = comp.startDate.take(4)
          val m = comp.startDate.substring(4, 6)
          val d = comp.startDate.substring(6, 8)
          s"$y-$m-${d}T12:00"
        } else {
          val today = new js.Date()
          val datePart = today.toISOString().split("T")(0)
          val timePart = today.toTimeString().split(" ")(0).take(5)
          s"${datePart}T$timePart"
        }

        setInput(gE(StartDateId), formattedDate)
        setInput(gE(CompNameId), comp.name)
        setInput(gE(TtrFromId), comp.lowLevel.map(_.toString).getOrElse(""))
        setInput(gE(TtrToId), comp.upperLevel.map(_.toString).getOrElse(""))

      case None =>
        setHtml(gE(TitleId), gM("dlg.comp.title"))
        setHtml(gE(ApplyId), gM("dlg.comp.btnCreate"))
        setInput(gE(CompCategoryId), defaultCategory.toString)
        setInput(gE(CompTypId), CompTyp.SINGLE.toString)

        val today = new js.Date()
        val datePart = today.toISOString().split("T")(0)
        val timePart = today.toTimeString().split(" ")(0).take(5) // HH:mm
        
        setInput(gE(StartDateId), s"${datePart}T$timePart")
        setInput(gE(CompNameId), "")
        setInput(gE(TtrFromId), "")
        setInput(gE(TtrToId), "")
    }
    
    modal.show()

    gE(ApplyId).onclick = { (_: MouseEvent) =>
      val name = getInput(gE(CompNameId))
      if (name.length < 3) {
        dom.window.alert(gM("dlg.comp.errName"))
      } else {
        val typ = CompTyp.fromString(getInput(gE(CompTypId)))
        val cat = CompCategory.valueOf(getInput(gE(CompCategoryId)))
        
        val startRaw = getInput(gE(StartDateId))
        val startFormatted = startRaw.replace("T", " ") + ":00"
        
        val ttrFrom = try Some(getInput(gE(TtrFromId)).toInt) catch { case _:Exception => None }
        val ttrTo   = try Some(getInput(gE(TtrToId)).toInt)   catch { case _:Exception => None }
        
        val comp = existingComp.map(_.copy(
          name = name,
          typ = typ,
          category = cat,
          startDate = startFormatted,
          lowLevel = ttrFrom,
          upperLevel = ttrTo
        )).getOrElse(Competition(
          id = CompId(0), // Temporary ID
          name = name,
          typ = typ,
          category = cat,
          startDate = startFormatted,
          status = CompStatus.CFG,
          lowLevel = ttrFrom,
          upperLevel = ttrTo
        ))
        
        validate match {
          case Some(valFn) =>
            gE(ApplyId).asInstanceOf[dom.html.Button].disabled = true
            valFn(comp).map {
              case Right(_) =>
                gE(ApplyId).asInstanceOf[dom.html.Button].disabled = false
                if (!p.isCompleted) p.success(Right(DlgCompResult(comp)))
                modal.hide()
              case Left(err) if err.is("tourney_already_exists") =>
                gE(ApplyId).asInstanceOf[dom.html.Button].disabled = false
                val inputEl = gE(CompNameId).asInstanceOf[dom.html.Input]
                inputEl.classList.add("is-invalid")
                inputEl.oninput = { (_: dom.Event) =>
                  inputEl.classList.remove("is-invalid")
                }
                dom.window.alert(base.Messages.gM("error.competition_already_exists"))
              case Left(err) =>
                gE(ApplyId).asInstanceOf[dom.html.Button].disabled = false
                dom.window.alert(s"Fehler: ${err.msgCode}")
            }
          case None =>
            if (!p.isCompleted) p.success(Right(DlgCompResult(comp)))
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
