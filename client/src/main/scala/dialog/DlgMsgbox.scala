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
import shared.Ids._

import cviews.dialog.html._

// enum BtnMsgbox:
//   case Cancel, Ok, Abort, Retry, Ignore, Yes, No, Close
//   def msgCode = "btn." + this.toString.toLowerCase
//   def name    = "DlgMsgbox_" + this.toString

object DlgMsgbox extends UseCase with JsWrapper:

  object Ids:  
    val DlgMsgbox_Modal:String     = nameOf(DlgMsgbox_Modal) 
    val DlgMsgbox_Body:String      = nameOf(DlgMsgbox_Body) 
    val DlgMsgbox_Title:String     = nameOf(DlgMsgbox_Title) 
    val DlgMsgbox_Close:String     = nameOf(DlgMsgbox_Close)

  enum Btn:
    case Cancel, Ok, Abort, Retry, Ignore, Yes, No, Close
    def msgCode = "btn." + this.toString.toLowerCase
    def name    = "DlgMsgbox_" + this.toString

  import Ids._ 
  import Btn._ 

  var modal: Modal = null
  def render(param: String = ""): Boolean = true     
  
  def show(body: String, title: String, btns: List[Btn]): Future[Btn] =
    val p = Promise[Btn]()
    val f = p.future
    // init modal dialog, always copy
    setHtml(gE(DlgMsgbox_Load), cviews.dialog.html.DlgMsgbox(title, body, btns))
    modal = Modal(gE(DlgMsgbox_Modal)) 
    modal.show()

    gE(Btn.Close.name).addEventListener("click", (e: MouseEvent) => {
      if (!p.isCompleted) then p success Btn.Cancel
      modal.hide()      
    })

    for btn <- btns do
      gE(btn.name).addEventListener("click", (e: MouseEvent) => {
        if (!p.isCompleted) then p success btn
        modal.hide()      
      })

    f.recover { case e: Exception =>  Btn.Cancel }