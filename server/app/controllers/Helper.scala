package controllers

import org.apache.pekko
import pekko.actor._
import pekko.dispatch._
import pekko.stream._
import pekko.stream.typed.scaladsl.ActorSource

import javax.inject._
import upickle.default._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.quoted.*
import play.api.mvc._
import play.api.Logging
import play.api.i18n.{ I18nSupport, Messages, Langs, Lang }
import play.api.http.ContentTypes
import play.api.libs.EventSource
import play.api.libs.json.{JsValue, Json }

import actors.SSEMgmtActor
import models._
import shared._
import shared.model._

def encMsgs(msgs: Map[String,String]) =
  import java.util.Base64
  import java.nio.charset.StandardCharsets
  val msgsJson = write[Map[String,String]](msgs) 
  Base64.getEncoder.encodeToString(msgsJson.getBytes(StandardCharsets.UTF_8))


@Singleton
class Helper @Inject()(system: ActorSystem, cc: ControllerComponents, userRepo: UserRepository)(implicit ec: ExecutionContext) 
  extends AbstractController(cc) with I18nSupport with Logging  {

  val sseManager = system.actorOf(SSEMgmtActor.props, name = "SSEMgmtActor")

  /**
    *  generate messages in Json format
    */
  def getMessages(lang: String=""): Action[AnyContent] = Action { implicit request =>
    
    val messages: Messages = messagesApi.preferred(request)
    val effLang = if (lang == "") messages.lang.code else lang 
    val msgs = messagesApi.messages(effLang)

    logger.trace(s"getMessages -> lang=${effLang}")
    Ok(Json.toJson(msgs))
  }

  /**
    *  get message with inserts
    */
  def getMsg(msgCode: String, in1: String, in2: String, in3: String): Action[AnyContent] = Action { implicit request =>
    
    val messages: Messages = messagesApi.preferred(request)

    logger.trace(s"getMsg -> msgCode=${msgCode} in1=${in1} in2=${in2} in3=${in3}")
    Ok(messages(msgCode,in1,in2,in3))
  }


  /**
   *  send2sse - sends a message through server send event mechanism to the client
   */  
  def send2sse(id: String, msg: String):Action[AnyContent] = Action { implicit request =>
    import actors.SSEMgmtActor._

    logger.trace(s"send2sse -> id=${id} msg=${msg} ")

    sseManager ! SendMessage(id, msg)
    Ok(s"Send message: ${msg} to: ${id}")
  }  


  /**
   *  sse - sends a message through server send event mechanism to the client
   */  
  def sse(id: String):Action[AnyContent] = Action { implicit request =>
    import actors.SSEMgmtActor._

    logger.trace(s"sse -> id=${id}")

    val source  = ActorSource.actorRef[String](
      completionMatcher = { case "end" => CompletionStrategy.draining },
      failureMatcher = PartialFunction.empty,
      bufferSize=32, 
      OverflowStrategy.dropHead
    ).watchTermination() { 
        case (actorRef, terminate) =>  sseManager ! Register(id, actorRef) 
                                       terminate.onComplete(_ => sseManager ! UnRegister(id) )
                                       logger.trace(s"sse -> watchTermination id=${id}")
                                       actorRef
    }

    // EventSource(.flow) see https://www.playframework.com/documentation/3.0.x/api/scala/play/api/libs/EventSource$.html
    // This class provides an easy way to use Server Sent Events (SSE) as a chunked encoding, using an Akka/Pekko Source.
    // It is a pre-built flow operator provided to ransform a stream of any type of data into a stream of ServerSentEvent 
    // objects, suitable for sending data as SSE.

    Ok.chunked(source via EventSource.flow).as(ContentTypes.EVENT_STREAM)
  } 


}
