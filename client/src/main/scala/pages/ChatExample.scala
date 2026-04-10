package pages

import base.* 
import services.*
import org.scalajs.dom.{ Event }
import org.scalajs.dom.raw.{ HTMLElement, HTMLTextAreaElement }
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import shared.model.User


object ChatExample extends BasePage with JsWrapper with ComWrapper:
  def name = PageNameTyp("ChatExample")
  val sendId: HtmlId = genId(name)
  val rcvMsgsId: HtmlId = genId(name)
  val receiverId: HtmlId = genId(name)
  val messageId: HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    import cviews.pages.*
    Global.user match
      case None      => setMain("ERROR: no user logged in for ChatExample usecase")
      case Some(usr) => setMain(html.ChatExample(usr))
      

  override def handleEvent(elem: HTMLElement, event: Event) =   
    HtmlId(elem.id) match

      //case ChatExample_Send => sendChatMsg( getInput(gE(ChatExample_Receiver),""), getInput(gE(ChatExample_Message),"") )
      case `sendId` => 
        Global.user match
          case None      => error(s"sendChatMsg -> no user logged in") 
          case Some(usr) => sendChatMsg(usr.id.toString, getInput(gE(receiverId),""), getInput(gE(messageId),"") ).map {
            case Left(err)  => error(s"sendChatMsg -> ${err}") 
            case Right(res) => info(s"sendChatMsg -> ${res}")   
          }
      case _                => error(s"event -> invalid elem/key: ${elem.id}")     

  def sendChatMsg(from: String, to: String, msg: String) = 
    ajaxGet[String]("/helper/send2sse", List(("from",from), ("to",to), ("msg",msg)))  

  def receiveMsg(msg: String) =
    val textarea = gE(rcvMsgsId).asInstanceOf[HTMLTextAreaElement]
    textarea.value = if textarea.value != "" then textarea.value + "\n" + msg else msg