package dialogs

import scala.concurrent.{ Future, Promise }
import scala.collection.mutable.ArrayBuffer
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.*
import org.scalajs.dom.{ MouseEvent, Event, KeyboardEvent }
import org.scalajs.dom.raw.HTMLElement

import shared.basic.Pickle._

import base.*
import base.Bootstrap.*

object DlgPrompt extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgPrompt")
  val LoadId:           HtmlId = genId(name)
  val ModalId:          HtmlId = genId(name)
  val HeaderId:         HtmlId = genId(name)
  val ResultId:         HtmlId = genId(name)
  val ResultContentId:  HtmlId = genId(name)
  val InputId:          HtmlId = genId(name)
  val CloseId:          HtmlId = genId(name)
  val ClearId:          HtmlId = genId(name)
  val ExecuteId:        HtmlId = genId(name)
  val CancelId:         HtmlId = genId(name) 
  val ToggleId:         HtmlId = genId(name)

  private var isDragging: Boolean = false
  private var startX: Double = 0.0
  private var startY: Double = 0.0
  private var initialLeft: Double = 0.0
  private var initialTop: Double = 0.0

  var output: HTMLElement = null
  var input:  HTMLElement = null

  private var currentPromise: Option[Promise[Either[AppError, String]]] = None

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
  
  def show(command: String): Future[Either[AppError, String]] =
    val p = Promise[Either[AppError, String]]()
    val f = p.future
    currentPromise = Some(p)

    // Initialize modal dialog if not already loaded
    if isEmpty(eE(LoadId,"span")) then
      setHtml(gE(LoadId), cviews.dialogs.html.DlgPrompt())
      initHistory()
      output   = gE(ResultContentId)
      input    = gE(InputId)

      // Mouse dragging logic for modeless dialog
      val header = gE(HeaderId)
      val card   = gE(ModalId)

      header.addEventListener("mousedown", (e: MouseEvent) => {
        if (e.button == 0) { // Left mouse button
          isDragging = true
          startX = e.clientX
          startY = e.clientY
          val rect = card.getBoundingClientRect()
          initialLeft = rect.left
          initialTop = rect.top
          card.style.right = "auto"
          e.preventDefault()
        }
      })

      org.scalajs.dom.document.addEventListener("mousemove", (e: MouseEvent) => {
        if (isDragging) {
          val dx = e.clientX - startX
          val dy = e.clientY - startY
          card.style.left = s"${initialLeft + dx}px"
          card.style.top = s"${initialTop + dy}px"
        }
      })

      org.scalajs.dom.document.addEventListener("mouseup", (e: MouseEvent) => {
        isDragging = false
      })

      // Add event listeners once during initialization
      gE(ClearId).addEventListener("click", (e: MouseEvent) => {
        set("")
      })

      // Add an event listener to the execute button
      gE(ExecuteId).addEventListener("click", (e: MouseEvent) => {
        currentPromise.foreach { cp =>
          if (!cp.isCompleted) then cp success Right(getInput(input))
        }
        add2History(getInput(input))
      })

      // Add an event listener to the cancel button
      gE(CancelId).addEventListener("click", (e: MouseEvent) => {
        currentPromise.foreach { cp =>
          if (!cp.isCompleted) then cp success Left(AppError("dlg.cancel"))
        }
        setVisible(gE(ModalId), false)      
      })    

      // Add an event listener to the close button
      gE(CloseId).addEventListener("click", (e: MouseEvent) => {
        currentPromise.foreach { cp =>
          if (!cp.isCompleted) then cp success Left(AppError("dlg.cancel"))
        }
        setVisible(gE(ModalId), false)      
      })   

      // Check Input for up/down and enter keykey 
      gE(InputId).onkeydown = {(e: KeyboardEvent) =>
        // ENTER key pressed
        if (Seq(13).contains(e.keyCode.toInt)) 
          e.preventDefault()
          currentPromise.foreach { cp =>
            if (!cp.isCompleted) then cp success Right(getInput(input))
          }
          add2History(getInput(input))
        
        // UP key pressed
        if (Seq(38).contains(e.keyCode.toInt)) { e.preventDefault(); setInput(input, upHistory()) }

        // DOWN key pressed
        if (Seq(40).contains(e.keyCode.toInt)) { e.preventDefault(); setInput(input, downHistory()) }
      }
    
    setVisible(gE(ModalId), true)
    if (command == "") setInput(input, getHistory()) else setInput(input, command)
    focusCmd
    
    f.map {
      case Left(err)  => Left(err)
      case Right(res) => Right(res)
    }.recover { case e: Exception =>  Left(AppError(e.getMessage)) }


  def set(msg: String) = setHtml(output, msg)
  def add(content: String) = set(output.innerText + content + "\n")
  
  def getCmd   = getInput(input, "") 
  def clearCmd = setInput(input, "")
  def focusCmd = input.focus() 

  def hide     = setVisible(gE(ModalId), false)