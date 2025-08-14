package dialog

import org.scalajs.dom.{ MouseEvent, Event, KeyboardEvent }
import org.scalajs.dom.raw.HTMLElement

import scala.concurrent.{ Future, Promise }
import scala.collection.mutable.ArrayBuffer
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

import upickle.default._

import base._
import base.Bootstrap._
import shared._
import shared.Ids._ 
import shared.model.AppError
import cviews.dialog.html._

object DlgPrompt extends UseCase with JsWrapper with NameOf:

  object Ids:  
    // used id attributes in template
    val DlgPrompt_Modal:String         = nameOf(DlgPrompt_Modal)
    val DlgPrompt_Result:String        = nameOf(DlgPrompt_Result)
    val DlgPrompt_ResultContent:String = nameOf(DlgPrompt_ResultContent)
    val DlgPrompt_Input:String         = nameOf(DlgPrompt_Input)
    val DlgPrompt_close:String         = nameOf(DlgPrompt_close)
    val DlgPrompt_clear:String         = nameOf(DlgPrompt_clear)
    val DlgPrompt_execute:String       = nameOf(DlgPrompt_execute)
    val DlgPrompt_cancel:String        = nameOf(DlgPrompt_cancel)
    val DlgPrompt_toggle:String        = nameOf(DlgPrompt_toggle)

  import Ids._ 

  var modal:        Modal = null
  var collapse:  Collapse = null
  var output: HTMLElement = null
  var input:  HTMLElement = null

  // variable for command history
  var history = new ArrayBuffer[String]()
  val hLength = 50
  var hPos    =  0 

  def render(param: String = ""): Boolean = true

  def initHistory() = 
    try history = read[ArrayBuffer[String]](getLocalStorage("CmdHistory"))
    catch { case _:Exception => info("initHistory -> no local storage info found") }  

  def add2History(cmd: String) =
    history.prepend(cmd)
    if (history.length == hLength) history.remove(hLength-1,1)
    setLocalStorage("CmdHistory", write[ArrayBuffer[String]](history))
    hPos = 0

  def getHistory()  = if (history.isDefinedAt(hPos)) then history(hPos) else ""
  def upHistory()   = { if (hPos < history.length-1) hPos = hPos + 1;  getHistory() }
  def downHistory() = { if (hPos > 1) hPos = hPos - 1; getHistory() }

  override def event(elem: HTMLElement, event: Event) =   
    elem.id match
      case DlgPrompt_toggle => collapse.toggle()
      case DlgPrompt_clear  => set("")
      case _                => error(s"event -> invalid elem/key: ${elem.id}")       
  
  def show(command: String): Future[Either[AppError, String]] =
    val p = Promise[Either[AppError, String]]()
    val f = p.future
    // init modal dialog
    if gE(DlgPrompt_Load).innerHTML == "" then 
      setHtml(gE(DlgPrompt_Load), cviews.dialog.html.DlgPrompt())
      initHistory()
      modal    = Modal(gE(DlgPrompt_Modal))
      collapse = Collapse(gE(DlgPrompt_Result))
      output   = gE(DlgPrompt_ResultContent)
      input    = gE(DlgPrompt_Input)
    
    modal.show()
    if (command == "") setInput(input, getHistory()) else setInput(input, command)

    // Add an event listener to the execute button
    gE(DlgPrompt_execute).addEventListener("click", (e: MouseEvent) => {
      if (!p.isCompleted) then p success Right(getInput(input))
      add2History(getInput(input))
      modal.hide()
    })

    // Add an event listener to the cancel button
    gE(DlgPrompt_cancel).addEventListener("click", (e: MouseEvent) => {
      if (!p.isCompleted) then p success Left(AppError("dlg.cancel"))
      modal.hide()      
    })    

    // Add an event listener to the close button
    gE(DlgPrompt_close).addEventListener("click", (e: MouseEvent) => {
      if (!p.isCompleted) then p success Left(AppError("dlg.cancel"))
      modal.hide()      
    })   

    // Check Input for up/down and enter keykey 
    gE(DlgPrompt_Input).onkeydown = {(e: KeyboardEvent) =>
      // ENTER key pressed
      if (Seq(13).contains(e.keyCode.toInt)) 
        e.preventDefault()
        if (!p.isCompleted) then p success Right(getInput(input))
        add2History(getInput(input))
        modal.hide()
      
      // UP key pressed
      if (Seq(38).contains(e.keyCode.toInt)) { e.preventDefault(); setInput(input, upHistory()) }

      // DOWN key pressed
      if (Seq(40).contains(e.keyCode.toInt)) { e.preventDefault(); setInput(input, downHistory()) }
    }
    
    f.map {
      case Left(err)  => Left(err)
      case Right(res) => Right(res)
    }.recover { case e: Exception =>  Left(AppError(e.getMessage)) }


  def set(msg: String) = setHtml(output, msg)
  def add(content: String) = set(output.innerText + content + "\n")
  
  def getCmd   = getInput(input, "") 
  def clearCmd = setInput(input, "")
  def focusCmd = input.focus() 

  def hide     = modal.hide()