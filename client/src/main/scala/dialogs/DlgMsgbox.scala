package dialogs

import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

import org.scalajs.dom.MouseEvent
import org.scalajs.dom.Event
import org.scalajs.dom.KeyboardEvent
import org.scalajs.dom.raw.HTMLElement

import base.*
import base.Bootstrap.*
import shared.BoxButton


object DlgMsgbox extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgMsgbox")
  val LoadId:  HtmlId = genId(name)
  val ModalId: HtmlId = genId(name)
  val TitleId: HtmlId = genId(name)
  val BodyId:  HtmlId = genId(name)
  val CloseId: HtmlId = genId(name)
  val DetailsBtnId: HtmlId = genId(name)
  val DetailsId:    HtmlId = genId(name)

  var modal: Modal = null
  def render(param: String = ""): Boolean = true

  /**
   * Zeigt einen Fehler: nur den Fehlertext (Übersetzung von msgCode mit den Inserts in1/in2,
   * ohne Übersetzung die Inserts selbst). Code und CallStack erscheinen über den Button "Details".
   *
   * @param err     Der anzuzeigende Fehler.
   * @param context Optionale Einleitung, z. B. "Fehler beim Speichern".
   * @return Future mit dem gedrückten Button.
   */
  def showError(err: shared.basic.AppError, context: String = ""): Future[BoxButton] =
    val translated = Messages.getErr(err)
    val text =
      if (translated != err.msgCode) translated
      else Seq(err.in1, err.in2).filter(_.nonEmpty).mkString("\n") match
        case "" => err.msgCode
        case inserts => inserts
    val body = if (context.nonEmpty) s"$context:\n$text" else text
    show(body, gM("msgbox.errorTitle"), List(BoxButton.Close), Some((err.msgCode, err.callStack)))

  def show(body: String, title: String, btns: List[BoxButton], details: Option[(String, String)] = None): Future[BoxButton] =
    val p = Promise[BoxButton]()
    val f = p.future

    // init modal dialog, always copy and replace literal \n with real newlines
    val formattedBody = body.replace("\\n", "\n")
    setHtml(getOrCreateDiv(LoadId), cviews.dialogs.html.DlgMsgbox(title, formattedBody, btns, details))
    modal = Modal(gE(ModalId))
    modal.show()

    // Details (Code, CallStack) ein-/ausblenden
    details.foreach { _ =>
      gE(DetailsBtnId).addEventListener("click", (e: MouseEvent) => gE(DetailsId).classList.toggle("d-none"))
    }

    gE(CloseId).addEventListener("click", (e: MouseEvent) => {
      if (!p.isCompleted) then p success BoxButton.Cancel
      modal.hide()      
    })

    for btn <- btns do
      gE(btn.getId).addEventListener("click", (e: MouseEvent) => {
        if (!p.isCompleted) then p success btn
        modal.hide()      
      })

    f.recover { case e: Exception =>  BoxButton.Cancel }