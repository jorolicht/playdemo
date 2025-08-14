package usecases

import base._
import services._
import shared._
import org.scalajs.dom.{ Event }
import org.scalajs.dom.raw.{ HTMLElement, HTMLTextAreaElement }
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue


object ChatExample extends UseCase with JsWrapper with NameOf with ComWrapper:
  import Ids._

  object Ids:  
    // used id attributes in template
    val ChatExample_Send:String         = nameOf(ChatExample_Send)
    val ChatExample_RcvMsgs:String      = nameOf(ChatExample_RcvMsgs)
    val ChatExample_Receiver:String     = nameOf(ChatExample_Receiver)
    val ChatExample_Message:String      = nameOf(ChatExample_Message)
  
  def render(param: String = ""): Boolean = 
    import cviews.usecases._
    setMain(html.ChatExample(Global.user))


  override def event(elem: HTMLElement, event: Event) =
    elem.id match
      //case ChatExample_Send => sendChatMsg( getInput(gE(ChatExample_Receiver),""), getInput(gE(ChatExample_Message),"") )
      case ChatExample_Send => 
        sendChatMsg(Global.user.uuid, getInput(gE(ChatExample_Receiver),""), getInput(gE(ChatExample_Message),"") ).map {
          case Left(err)  => error(s"sendChatMsg -> ${err}") 
          case Right(res) => info(s"sendChatMsg -> ${res}")   
        }
      case _                => error(s"event -> invalid elem/key: ${elem.id}")     

  def sendChatMsg(from: String, to: String, msg: String) = 
    ajaxGet[String]("/helper/send2sse", List(("from",from), ("to",to), ("msg",msg)))  

  def receiveMsg(msg: String) =
    val textarea = gE(ChatExample_RcvMsgs).asInstanceOf[HTMLTextAreaElement]
    textarea.value = if textarea.value != "" then textarea.value + "\n" + msg else msg