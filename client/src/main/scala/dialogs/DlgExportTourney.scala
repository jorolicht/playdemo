package dialogs

import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.{ MouseEvent, Event, KeyboardEvent }
import org.scalajs.dom.raw.HTMLElement

import base.*
import base.Bootstrap.*
import base.Messages.gM
import shared.model.*
import shared.DomTypes.HtmlId
import shared.basic.AppError

/**
 * Dialog for exporting local tournaments as JSON files.
 * Used when explicitly exporting via Local-Options or when leaving/saving in local mode.
 */
object DlgExportTourney extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgExportTourney")

  val LoadId:                  HtmlId = genId(name)
  val ModalId:                 HtmlId = genId(name)
  val TitleId:                 HtmlId = genId(name)
  val InputFilenameId:         HtmlId = genId(name)
  val ApplyId:                 HtmlId = genId(name)
  val CancelId:                HtmlId = genId(name)
  val CloseId:                 HtmlId = genId(name)
  val BtnLeaveWithoutExportId: HtmlId = genId(name)

  private var modal: Modal = null

  def render(param: String = ""): Boolean = true

  /**
   * Shows the tournament export dialog.
   *
   * @param tourney The tournament to export.
   * @param saveAndLeave If true, provides leave options as well.
   * @return Future containing Some(filename) to export, Some("") to leave without export, or None if cancelled.
   */
  def show(tourney: Tourney, saveAndLeave: Boolean = false): Future[Option[String]] =
    val p = Promise[Option[String]]()
    val defaultFilename = services.TourneyDB.defaultExportFilename(tourney)

    setHtml(getOrCreateDiv(LoadId), cviews.dialogs.html.DlgExportTourney(defaultFilename, saveAndLeave))
    val modalElem = gE(ModalId)
    modal = Modal(modalElem)

    val inputElem = gE(InputFilenameId).asInstanceOf[dom.html.Input]
    var overwriteConfirmedFor: Option[String] = None

    def resetOverwriteWarning(): Unit = {
      overwriteConfirmedFor = None
      val warnElem = dom.document.getElementById("dlgExportTourneyOverwriteWarning")
      if (warnElem != null) {
        warnElem.classList.add("d-none")
        warnElem.classList.remove("d-block")
      }
      val applyBtn = gE(ApplyId, withWarning = false)
      if (applyBtn != null) {
        applyBtn.classList.remove("btn-danger")
        applyBtn.classList.add("btn-primary")
        val defaultLabel = if (saveAndLeave) gM("dlg.exportTourney.btnLeave") else gM("dlg.exportTourney.btnExport")
        val icon = if (saveAndLeave) "bi-box-arrow-right" else "bi-download"
        setHtml(applyBtn, s"""<i class="bi $icon me-1"></i>$defaultLabel""")
      }
    }

    def doConfirmExport(): Unit = {
      val rawVal = if (inputElem != null) inputElem.value.trim else ""
      if (rawVal.isEmpty) {
        val feedback = dom.document.getElementById("dlgExportTourneyFeedback")
        if (feedback != null) {
          feedback.classList.remove("d-none")
          feedback.classList.add("d-block")
        }
        if (inputElem != null) inputElem.classList.add("is-invalid")
      } else {
        val finalName = if (rawVal.toLowerCase.endsWith(".json")) rawVal else s"$rawVal.json"
        val cleanName = finalName.toLowerCase
        if (services.TourneyDB.fileExists(finalName) && !overwriteConfirmedFor.contains(cleanName)) {
          val warnElem = dom.document.getElementById("dlgExportTourneyOverwriteWarning")
          if (warnElem != null) {
            warnElem.classList.remove("d-none")
            warnElem.classList.add("d-block")
          }
          val applyBtn = gE(ApplyId, withWarning = false)
          if (applyBtn != null) {
            applyBtn.classList.remove("btn-primary")
            applyBtn.classList.add("btn-danger")
            val overwriteLabel = gM("dlg.exportTourney.btnOverwrite")
            setHtml(applyBtn, s"""<i class="bi bi-exclamation-octagon me-1"></i>$overwriteLabel""")
          }
          overwriteConfirmedFor = Some(cleanName)
        } else {
          if (!p.isCompleted) p.trySuccess(Some(finalName))
          modal.hide()
        }
      }
    }

    if (inputElem != null) {
      inputElem.onkeydown = (e: KeyboardEvent) => {
        if (e.key == "Enter") {
          e.preventDefault()
          doConfirmExport()
        }
      }
      inputElem.oninput = (_: Event) => {
        inputElem.classList.remove("is-invalid")
        val feedback = dom.document.getElementById("dlgExportTourneyFeedback")
        if (feedback != null) {
          feedback.classList.add("d-none")
          feedback.classList.remove("d-block")
        }
        resetOverwriteWarning()
      }
    }

    gE(ApplyId).onclick = (e: MouseEvent) => {
      e.preventDefault()
      if (saveAndLeave) {
        if (!p.isCompleted) p.trySuccess(Some(""))
        modal.hide()
      } else {
        doConfirmExport()
      }
    }

    val cancelHandler = (e: MouseEvent) => {
      e.preventDefault()
      if (!p.isCompleted) p.trySuccess(None)
      modal.hide()
    }

    val cancelBtn = gE(CancelId, withWarning = false)
    if (cancelBtn != null) cancelBtn.onclick = cancelHandler

    val closeBtn = gE(CloseId, withWarning = false)
    if (closeBtn != null) closeBtn.onclick = cancelHandler

    modalElem.addEventListener("hidden.bs.modal", (_: Event) => {
      if (!p.isCompleted) p.trySuccess(None)
    })

    modal.show()

    // Focus input field
    dom.window.setTimeout(() => {
      if (inputElem != null) {
        inputElem.focus()
        inputElem.select()
      }
    }, 300)

    p.future

