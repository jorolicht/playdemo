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

case class DlgAddSingleResult(
  firstName: String,
  lastName:  String,
  clubName:  String,
  ttr:       Option[Int],
  year:      Option[Int],
  email:     Option[String],
  whatsApp:  Option[String],
  enroll:    Boolean
)

object DlgAddSingle extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgAddSingle")
  
  val LoadId:         HtmlId = genId(name)
  val ModalId:        HtmlId = genId(name)
  val FormId:         HtmlId = genId(name)
  val InputLastId:    HtmlId = genId(name)
  val InputFirstId:   HtmlId = genId(name)
  val InputClubId:    HtmlId = genId(name)
  val ClubListId:     HtmlId = genId(name)
  val InputTtrId:     HtmlId = genId(name)
  val InputYearId:    HtmlId = genId(name)
  val InputEmailId:   HtmlId = genId(name)
  val InputWhatsappId: HtmlId = genId(name)
  val RadioPlayId:    HtmlId = genId(name)
  val RadioRegId:     HtmlId = genId(name)
  
  val ApplyId:        HtmlId = genId(name)
  val CancelId:       HtmlId = genId(name)
  val CloseId:        HtmlId = genId(name)

  private var modal: Modal = null

  def render(param: String = ""): Boolean = true

  def show(clubs: Seq[Club]): Future[Either[AppError, DlgAddSingleResult]] =
    val p = Promise[Either[AppError, DlgAddSingleResult]]()
    val f = p.future

    val currentYear = 2026 // Could be dynamic
    
    setHtml(eE(LoadId, "span"), cviews.dialogs.html.DlgAddSingle(clubs, currentYear))
    modal = Modal(gE(ModalId))
    
    modal.show()

    gE(ApplyId).onclick = { (_: MouseEvent) =>
      val last  = getInput(gE(InputLastId)).trim
      val first = getInput(gE(InputFirstId)).trim
      val club  = getInput(gE(InputClubId)).trim
      val ttr   = try Some(getInput(gE(InputTtrId)).toInt) catch { case _: Exception => None }
      val yearVal = try getInput(gE(InputYearId)).toInt catch { case _: Exception => 0 }
      val year  = if (yearVal == 0) None else Some(yearVal)
      val emailVal = getInput(gE(InputEmailId)).trim
      val email = if (emailVal.isEmpty) None else Some(emailVal)
      val whatsappVal = getInput(gE(InputWhatsappId)).trim
      val whatsApp = if (whatsappVal.isEmpty) None else Some(whatsappVal)
      val enroll = gE(RadioPlayId).asInstanceOf[dom.html.Input].checked

      if (last.isEmpty || first.isEmpty || club.isEmpty) {
        dom.window.alert(base.Messages.getMsg("DlgAddSingle.err_fill"))
      } else {
        if (!p.isCompleted) p.success(Right(DlgAddSingleResult(first, last, club, ttr, year, email, whatsApp, enroll)))
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
