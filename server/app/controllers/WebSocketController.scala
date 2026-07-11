package controllers

import javax.inject._
import org.apache.pekko.actor._
import org.apache.pekko.stream.Materializer
import play.api.mvc._
import play.api.libs.streams.ActorFlow
import play.api.Logging

@Singleton
class WebSocketController @Inject()(
  cc: ControllerComponents
)(implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) with Logging {

  private val manager = actors.WebSocketManager.get

  def socket(slug: String): WebSocket = WebSocket.accept[String, String] { request =>
    logger.info(s"WebSocket connection request for slug: $slug")
    ActorFlow.actorRef { out =>
      actors.WebSocketClientActor.props(out, manager, slug)
    }
  }

  def sendmsg(slug: String, msg: String): Action[AnyContent] = Action { request =>
    logger.info(s"Sending WebSocket message to slug '$slug': $msg")
    manager ! actors.WebSocketManagerActor.SendToSlug(slug, msg)
    Ok(s"Message sent to slug '$slug'")
  }
}
