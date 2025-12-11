package usecases

import base._
import services._
import shared._
import org.scalajs.dom.{ Event }
import org.scalajs.dom.raw.{ HTMLElement, HTMLTextAreaElement }
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

enum IdsChatExample extends NamedId:
  case SendId, RcvMsgsId, ReceiverId, MessageId 
  override def name: String = IdsChatExample.Prefix + this.toString

object IdsChatExample:
  import scala.util.Try
  final val Prefix: String = "IdsChatExample"
  def fromId(id: String): Option[IdsChatExample] = 
    if (id.startsWith(Prefix)) then Try(IdsChatExample.valueOf(id.stripPrefix(Prefix))).toOption else None


object ChatExample extends UseCase with JsWrapper with NameOf with ComWrapper:
  import IdsChatExample._
  
  def render(param: String = ""): Boolean = 
    import cviews.usecases._
    setMain(html.ChatExample(Global.user))


  override def event(elem: HTMLElement, event: Event) =
    IdsChatExample.fromId(elem.id) match
      //case ChatExample_Send => sendChatMsg( getInput(gE2(ChatExample_Receiver),""), getInput(gE2(ChatExample_Message),"") )
      case Some(SendId) => 
        sendChatMsg(Global.user.uuid, getInput(gE2(ReceiverId),""), getInput(gE2(MessageId),"") ).map {
          case Left(err)  => error(s"sendChatMsg -> ${err}") 
          case Right(res) => info(s"sendChatMsg -> ${res}")   
        }
      case _                => error(s"event -> invalid elem/key: ${elem.id}")     

  def sendChatMsg(from: String, to: String, msg: String) = 
    ajaxGet[String]("/helper/send2sse", List(("from",from), ("to",to), ("msg",msg)))  

  def receiveMsg(msg: String) =
    val textarea = gE2(RcvMsgsId).asInstanceOf[HTMLTextAreaElement]
    textarea.value = if textarea.value != "" then textarea.value + "\n" + msg else msg