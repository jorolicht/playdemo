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

case class DlgAddDoubleResult(
  player1: Player,
  player2: Player,
  enroll:  Boolean
)

object DlgAddDouble extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgAddDouble")
  
  val LoadId:         HtmlId = genId(name)
  val ModalId:        HtmlId = genId(name)
  val InputP1Id:      HtmlId = genId(name)
  val InputP2Id:      HtmlId = genId(name)
  val RadioPlayId:    HtmlId = genId(name)
  
  val ApplyId:        HtmlId = genId(name)
  val CancelId:       HtmlId = genId(name)
  val CloseId:        HtmlId = genId(name)

  private var modal: Modal = null

  def render(param: String = ""): Boolean = true

  def show(players: Seq[Player], clubs: Seq[Club]): Future[Either[AppError, DlgAddDoubleResult]] =
    val p = Promise[Either[AppError, DlgAddDoubleResult]]()
    val f = p.future

    setHtml(gE(LoadId), cviews.dialogs.html.DlgAddDouble(players, clubs))
    modal = Modal(gE(ModalId))
    
    modal.show()

    gE(ApplyId).onclick = { (_: MouseEvent) =>
      val val1 = getInput(gE(InputP1Id)).trim
      val val2 = getInput(gE(InputP2Id)).trim
      val enroll = gE(RadioPlayId).asInstanceOf[dom.html.Input].checked

      // Try to find the players by their display string (simplified for now)
      val p1Opt = players.find(p => s"${p.lastName}, ${p.firstName}".contains(val1.takeWhile(_ != '[').trim))
      val p2Opt = players.find(p => s"${p.lastName}, ${p.firstName}".contains(val2.takeWhile(_ != '[').trim))

      (p1Opt, p2Opt) match
        case (Some(p1), Some(p2)) if p1.id != p2.id =>
          if (!p.isCompleted) p.success(Right(DlgAddDoubleResult(p1, p2, enroll)))
          modal.hide()
        case (Some(_), Some(_)) =>
          dom.window.alert("Ein Spieler kann nicht mit sich selbst im Doppel spielen.")
        case _ =>
          dom.window.alert("Bitte wählen Sie zwei gültige Spieler aus der Liste aus.")
    }

    val onCancel = { (_: MouseEvent) =>
      if (!p.isCompleted) p.success(Left(AppError("dlg.cancel")))
      modal.hide()
    }
    gE(CancelId).onclick = onCancel
    gE(CloseId).onclick = onCancel

    f
