package pages

import base.* 
import services._
import shared._
import org.scalajs.dom.{ Event }
import org.scalajs.dom.raw.{ HTMLElement, HTMLTextAreaElement }
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import shared.model.User
import shared.DomTypes.HtmlId


object ChatExample extends BasePage with JsWrapper with ComWrapper:
  
  private inline val P = "ChatExample"
  private inline val MP = "chatexample"

  val sendId: HtmlId = HtmlId.fromName(name)
  val rcvMsgsId: HtmlId = HtmlId.fromName(name)
  val receiverId: HtmlId = HtmlId.fromName(name)
  val messageId: HtmlId = HtmlId.fromName(name)

  def render(param: String = ""): Boolean = 
    import cviews.pages.*
    Global.user match
      case None      => setMain("ERROR: no user logged in for ChatExample usecase")
      case Some(usr) => setMain(html.ChatExample(usr))
      

  override def event(elem: HTMLElement, event: Event) =
    HtmlId(elem.id) match
      //case ChatExample_Send => sendChatMsg( getInput(gE3(ChatExample_Receiver),""), getInput(gE3(ChatExample_Message),"") )
      case `sendId` => 
        Global.user match
          case None      => error(s"sendChatMsg -> no user logged in") 
          case Some(usr) => sendChatMsg(usr.id.toString, getInput(gE3(receiverId),""), getInput(gE3(messageId),"") ).map {
            case Left(err)  => error(s"sendChatMsg -> ${err}") 
            case Right(res) => info(s"sendChatMsg -> ${res}")   
          }
      case _                => error(s"event -> invalid elem/key: ${elem.id}")     

  def sendChatMsg(from: String, to: String, msg: String) = 
    ajaxGet[String]("/helper/send2sse", List(("from",from), ("to",to), ("msg",msg)))  

  def receiveMsg(msg: String) =
    val textarea = gE3(rcvMsgsId).asInstanceOf[HTMLTextAreaElement]
    textarea.value = if textarea.value != "" then textarea.value + "\n" + msg else msg