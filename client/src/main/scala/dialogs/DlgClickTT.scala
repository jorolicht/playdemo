package dialogs

import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import org.scalajs.dom.{ MouseEvent, Event, FileReader }
import org.scalajs.dom.raw.HTMLElement

import base.*
import base.Bootstrap.*
import services.ClickTTParser
import shared.model.{CttTournament, Tourney}

/**
 * Dialog for importing ClickTT XML data.
 * Returns a CttTournament object on success.
 */
object DlgClickTT extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgClickTT")
  
  val LoadId:         HtmlId = genId(name)
  val LabelId:        HtmlId = genId(name)
  val ModalId:        HtmlId = genId(name)
  val FileInputId:    HtmlId = genId(name)
  val ResultId:       HtmlId = genId(name)
  val ApplyId:        HtmlId = genId(name)
  val CancelId:       HtmlId = genId(name)
  val CloseId:        HtmlId = genId(name)

  private var modal: Modal = null
  private var parsedTournament: Option[CttTournament] = None

  def render(param: String = ""): Boolean = true

  /**
   * Shows the ClickTT import dialog.
   * @return A Future containing Either an AppError or the mapped Tourney.
   */
  def show(): Future[Either[AppError, Tourney]] =
    val p = Promise[Either[AppError, Tourney]]()
    val f = p.future

    // Initialize modal dialog if not already loaded
    if isEmpty(eE(LoadId,"span")) then
      setHtml(gE(LoadId), cviews.dialogs.html.DlgClickTT())
      modal = Modal(gE(ModalId))
    
    // Reset state
    parsedTournament = None
    gE(ApplyId).classList.add("d-none")
    setHtml(gE(ResultId), "")
    val input = gE(FileInputId).asInstanceOf[org.scalajs.dom.html.Input]
    input.value = ""
    
    modal.show()

    // File selection handling
    input.onchange = { (_: Event) =>
      if (input.files.length > 0) {
        val file = input.files.item(0)
        val reader = new FileReader()

        reader.onload = { (_: Event) =>
          val xmlString = reader.result.asInstanceOf[String]
          ClickTTParser.parse(xmlString) match {
            case Right(tournament) =>
              parsedTournament = Some(tournament)
              gE(ApplyId).classList.remove("d-none")
              setHtml(gE(ResultId), s"""
                <div class='alert alert-success'>
                  <strong>Erfolg!</strong> '${tournament.name}' eingelesen.<br>
                  ${tournament.competitions.length} Wettbewerbe gefunden.
                </div>
              """)
            case Left(err) =>
              parsedTournament = None
              gE(ApplyId).classList.add("d-none")
              setHtml(gE(ResultId), s"""
                <div class='alert alert-danger'>
                  <strong>Fehler:</strong> $err
                </div>
              """)
          }
        }
        reader.readAsText(file)
      }
    }

    // Apply button
    gE(ApplyId).onclick = { (_: MouseEvent) =>
      parsedTournament match
        case Some(ctt) => 
          services.ClickTTMapper.mapAndImport(ctt) match {
            case Right(t) => 
              if (!p.isCompleted) p.success(Right(t))
              modal.hide()
            case Left(err) => 
              setHtml(gE(ResultId), s"<div class='alert alert-danger'>Mapping fehlgeschlagen: ${err.msgCode}</div>")
          }
        case None => 
          debug("Apply clicked but no tournament parsed")
    }

    // Cancel/Close handling
    val onCancel = { (_: MouseEvent) =>
      if (!p.isCompleted) p.success(Left(AppError("dlg.cancel")))
      modal.hide()
    }
    gE(CancelId).onclick = onCancel
    gE(CloseId).onclick = onCancel

    f
