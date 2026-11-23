package dialogs

import scala.concurrent.{ Future, Promise }
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.MouseEvent

import base.*
import base.Bootstrap.*
import base.Messages.gM
import shared.DomTypes.HtmlId
import shared.model.{ Tourney, TourneyTemplate }

/**
 * Dialog „Turnier kopieren“: Name und Startdatum des neuen Turniers. Vorschlag: gleicher Wochentag ein Jahr später
 * (+364 Tage), eine Jahreszahl im Namen wird entsprechend angepasst.
 */
object DlgCopyTourney extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgCopyTourney")

  val LoadId:      HtmlId = genId(name)
  val ModalId:     HtmlId = genId(name)
  val TitleId:     HtmlId = genId(name)
  val InputNameId: HtmlId = genId(name)
  val InputDateId: HtmlId = genId(name)
  val ErrorId:     HtmlId = genId(name)
  val ApplyId:     HtmlId = genId(name)
  val CancelId:    HtmlId = genId(name)
  val CloseId:     HtmlId = genId(name)

  private var modal: Modal = null

  def render(param: String = ""): Boolean = true

  /**
   * Vorschlag für Name und Startdatum der Kopie.
   *
   * @param t Quellturnier
   * @return (Name, Startdatum JJJJMMTT)
   */
  def suggestion(t: Tourney): (String, Int) =
    if !TourneyTemplate.validYmd(t.startDate) then (t.name, t.startDate)
    else
      val date = TourneyTemplate.fromDays(TourneyTemplate.toDays(t.startDate) + 364)
      val oldYear = (t.startDate / 10000).toString
      val newYear = (date / 10000).toString
      (if t.name.contains(oldYear) then t.name.replace(oldYear, newYear) else t.name, date)

  /** JJJJMMTT → "JJJJ-MM-TT" für das Datumsfeld. */
  private def toInput(ymd: Int): String = f"${ymd / 10000}%04d-${(ymd / 100) % 100}%02d-${ymd % 100}%02d"

  /**
   * Zeigt den Dialog.
   *
   * @param t Quellturnier
   * @return Some((Name, Startdatum JJJJMMTT)) oder None bei Abbruch
   */
  def show(t: Tourney): Future[Option[(String, Int)]] =
    val p = Promise[Option[(String, Int)]]()
    setHtml(getOrCreateDiv(LoadId), cviews.dialogs.html.DlgCopyTourney(t))
    modal = Modal(gE(ModalId))
    val (sName, sDate) = suggestion(t)
    val nameInput = gE(InputNameId).asInstanceOf[dom.html.Input]
    val dateInput = gE(InputDateId).asInstanceOf[dom.html.Input]
    nameInput.value = sName
    dateInput.value = if TourneyTemplate.validYmd(sDate) then toInput(sDate) else ""
    val err = gE(ErrorId)

    gE(ApplyId).onclick = (_: MouseEvent) => {
      val n = nameInput.value.trim
      val d = dateInput.value.replace("-", "").toIntOption.getOrElse(0)
      if n.length < 3 then { err.textContent = gM("dlg.copyTourney.errName"); err.classList.remove("d-none") }
      else if !TourneyTemplate.validYmd(d) then { err.textContent = gM("dlg.copyTourney.errDate"); err.classList.remove("d-none") }
      else
        if !p.isCompleted then p.trySuccess(Some((n, d)))
        modal.hide()
    }
    val cancel = (_: MouseEvent) => { if !p.isCompleted then p.trySuccess(None); modal.hide() }
    gE(CancelId).onclick = cancel
    gE(CloseId).onclick = cancel
    modal.show()
    p.future
