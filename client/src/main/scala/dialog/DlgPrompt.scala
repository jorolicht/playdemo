package dialog

import scala.concurrent.{ Future, Promise }
import scala.collection.mutable.ArrayBuffer
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.*
import org.scalajs.dom.{ MouseEvent, Event, KeyboardEvent }
import org.scalajs.dom.raw.HTMLElement

import upickle.default._

import base._
import base.Bootstrap._
import shared._
import shared.model.AppError
import cviews.dialog.html._






object DlgPrompt extends UseCase with JsWrapper:
  import IdsPrompt.*

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

  @JSExportTopLevel("eventDlgPrompt")  
  override def event(elem: HTMLElement, event: Event) =   
    IdsPrompt.fromId(elem.id) match
      case Some(ToggleId) => collapse.toggle()
      case Some(ClearId)  => set("")
      case _              => error(s"event -> invalid elem/key: ${elem.id}")       
  
  def show(command: String): Future[Either[AppError, String]] =
    val p = Promise[Either[AppError, String]]()
    val f = p.future
    // init modal dialog
    if gE2(LoadId).innerHTML == "" then 
      setHtml(gE2(LoadId), cviews.dialog.html.DlgPrompt())
      initHistory()
      modal    = Modal(gE2(ModalId))
      collapse = Collapse(gE2(ResultId))
      output   = gE2(ResultContentId)
      input    = gE2(InputId)
    
    modal.show()
    if (command == "") setInput(input, getHistory()) else setInput(input, command)

    // Add an event listener to the execute button
    gE2(ExecuteId).addEventListener("click", (e: MouseEvent) => {
      if (!p.isCompleted) then p success Right(getInput(input))
      add2History(getInput(input))
      modal.hide()
    })

    // Add an event listener to the cancel button
    gE2(CancelId).addEventListener("click", (e: MouseEvent) => {
      if (!p.isCompleted) then p success Left(AppError("dlg.cancel"))
      modal.hide()      
    })    

    // Add an event listener to the close button
    gE2(CloseId).addEventListener("click", (e: MouseEvent) => {
      if (!p.isCompleted) then p success Left(AppError("dlg.cancel"))
      modal.hide()      
    })   

    // Check Input for up/down and enter keykey 
    gE2(InputId).onkeydown = {(e: KeyboardEvent) =>
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