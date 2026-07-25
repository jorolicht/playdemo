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

  var modal: Modal = null
  def render(param: String = ""): Boolean = true     
  
  def show(body: String, title: String, btns: List[BoxButton]): Future[BoxButton] =
    val p = Promise[BoxButton]()
    val f = p.future

    // init modal dialog, always copy and replace literal \n with real newlines
    val formattedBody = body.replace("\\n", "\n")
    setHtml(getOrCreateDiv(LoadId), cviews.dialogs.html.DlgMsgbox(title, formattedBody, btns))
    modal = Modal(gE(ModalId)) 
    modal.show()

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