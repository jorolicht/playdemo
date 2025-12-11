package dialog

import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

import org.scalajs.dom.MouseEvent
import org.scalajs.dom.Event
import org.scalajs.dom.KeyboardEvent
import org.scalajs.dom.raw.HTMLElement

import base._
import base.Bootstrap._
import shared.model.AppError
import shared._


import cviews.dialog.html._



object DlgMsgbox extends UseCase with JsWrapper:
  import IdsMsgbox.* 
  import BtnMsgbox.*

  var modal: Modal = null
  def render(param: String = ""): Boolean = true     
  
  def show(body: String, title: String, btns: List[BtnMsgbox]): Future[BtnMsgbox] =
    val p = Promise[BtnMsgbox]()
    val f = p.future
    // init modal dialog, always copy
    setHtml(gE2(LoadId), cviews.dialog.html.DlgMsgbox(title, body, btns))
    modal = Modal(gE2(ModalId)) 
    modal.show()

    gE2(Close).addEventListener("click", (e: MouseEvent) => {
      if (!p.isCompleted) then p success Cancel
      modal.hide()      
    })

    for btn <- btns do
      gE2(btn).addEventListener("click", (e: MouseEvent) => {
        if (!p.isCompleted) then p success btn
        modal.hide()      
      })

    f.recover { case e: Exception =>  Cancel }