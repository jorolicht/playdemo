package dialogs

import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

import org.scalajs.dom.MouseEvent
import org.scalajs.dom.Event
import org.scalajs.dom.KeyboardEvent
import org.scalajs.dom.raw.HTMLElement

import base._
import base.Bootstrap.*
import base.Logging.*
import shared.model.AppError
import shared.DomTypes.HtmlId
import shared.BoxValueTypes.BoxValue
import shared.BoxValues.*


object DlgMsgbox extends BaseDialog with JsWrapper:

  val LoadId:  HtmlId = HtmlId.fromName(name)
  val ModalId: HtmlId = HtmlId.fromName(name)
  val TitleId: HtmlId = HtmlId.fromName(name)
  val BodyId:  HtmlId = HtmlId.fromName(name)
  val CloseId: HtmlId = HtmlId.fromName(name)

  var modal: Modal = null
  def render(param: String = ""): Boolean = true     
  
  def show(body: String, title: String, btns: List[BoxValue]): Future[BoxValue] =
    val p = Promise[BoxValue]()
    val f = p.future

    // init modal dialog, always copy
    setHtml(getOrCreateDiv(LoadId), cviews.dialogs.html.DlgMsgbox(title, body, btns))
    modal = Modal(gE(ModalId)) 
    modal.show()

    gE(CloseId).addEventListener("click", (e: MouseEvent) => {
      if (!p.isCompleted) then p success Cancel
      modal.hide()      
    })

    for btn <- btns do
      gE(btn.getId).addEventListener("click", (e: MouseEvent) => {
        if (!p.isCompleted) then p success btn
        modal.hide()      
      })

    f.recover { case e: Exception =>  Cancel }