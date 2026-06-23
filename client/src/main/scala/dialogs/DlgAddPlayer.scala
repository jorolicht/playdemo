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

object DlgAddPlayer extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgAddPlayer")
  
  val LoadId:         HtmlId = genId(name)
  val ModalId:        HtmlId = genId(name)
  val ApplyId:        HtmlId = genId(name)
  val CancelId:       HtmlId = genId(name)
  val CloseId:        HtmlId = genId(name)

  private var modal: Modal = null

  def render(param: String = ""): Boolean = true

  def show(players: Seq[Player], clubs: Map[Int, String]): Future[Either[AppError, Player]] =
    val p = Promise[Either[AppError, Player]]()
    val f = p.future
   
    if isEmpty(eE(LoadId, "span")) then
      setHtml(gE(LoadId), cviews.dialogs.html.DlgAddPlayer(players, clubs))
      modal = Modal(gE(ModalId))
    else
      setHtml(gE(LoadId), cviews.dialogs.html.DlgAddPlayer(players, clubs))
      modal = Modal(gE(ModalId))

    var selectedPlayerId: Option[PlayerId] = None

    // Attach click listeners to all rows
    val rows = dom.document.querySelectorAll(".player-select-row")
    for (i <- 0 until rows.length) {
      val row = rows.item(i).asInstanceOf[dom.html.TableRow]
      row.onclick = (_: MouseEvent) => {
        // Clear active class from all rows
        for (j <- 0 until rows.length) {
          rows.item(j).asInstanceOf[dom.html.TableRow].classList.remove("table-primary")
        }
        // Add active class to clicked row
        row.classList.add("table-primary")
        selectedPlayerId = Some(PlayerId(row.getAttribute("data-id").toInt))
      }
    }

    modal.show()

    gE(ApplyId).onclick = { (_: MouseEvent) =>
      selectedPlayerId match {
        case Some(pid) =>
          players.find(_.id == pid) match {
            case Some(player) =>
              if (!p.isCompleted) p.success(Right(player))
              modal.hide()
            case None =>
              dom.window.alert("Fehler: Ausgewählter Spieler wurde nicht gefunden.")
          }
        case None =>
          dom.window.alert("Bitte wählen Sie zuerst einen Spieler aus.")
      }
    }

    val onCancel = { (_: MouseEvent) =>
      if (!p.isCompleted) p.success(Left(AppError("dlg.cancel")))
      modal.hide()
    }
    gE(CancelId).onclick = onCancel
    gE(CloseId).onclick = onCancel

    f
