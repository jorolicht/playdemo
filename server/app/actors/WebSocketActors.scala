package actors

import org.apache.pekko.actor._
import play.api.Logging
import scala.collection.mutable

class WebSocketManagerActor extends Actor with Logging {
  import WebSocketManagerActor._

  // Map of slug -> Set of client actor refs
  private val clients = mutable.HashMap[String, mutable.Set[ActorRef]]()

  def receive: Receive = {
    case Register(slug, ref) =>
      val set = clients.getOrElseUpdate(slug, mutable.Set.empty[ActorRef])
      set.add(ref)
      logger.info(s"Registered WebSocket client for slug '$slug'. Total clients for this slug: ${set.size}")

    case Unregister(slug, ref) =>
      clients.get(slug).foreach { set =>
        set.remove(ref)
        if (set.isEmpty) {
          clients.remove(slug)
        }
        logger.info(s"Unregistered WebSocket client for slug '$slug'. Remaining clients: ${set.size}")
      }

    case SendToSlug(slug, msg) =>
      logger.info(s"Broadcasting message to slug '$slug': $msg")
      clients.get(slug) match {
        case Some(set) if set.nonEmpty =>
          set.foreach { clientRef =>
            clientRef ! WebSocketClientActor.SendToClient(msg)
          }
          sender() ! true
        case _ =>
          sender() ! false
      }
  }
}

object WebSocketManager {
  private var _manager: Option[ActorRef] = None

  def get(implicit system: ActorSystem): ActorRef = synchronized {
    _manager.getOrElse {
      val ref = system.actorOf(WebSocketManagerActor.props, "WebSocketManagerActor")
      _manager = Some(ref)
      ref
    }
  }
}

object WebSocketManagerActor {
  def props: Props = Props[WebSocketManagerActor]()

  case class Register(slug: String, ref: ActorRef)
  case class Unregister(slug: String, ref: ActorRef)
  case class SendToSlug(slug: String, msg: String)
}

class WebSocketClientActor(out: ActorRef, manager: ActorRef, slug: String) extends Actor with Logging {
  import WebSocketManagerActor._
  import WebSocketClientActor._

  override def preStart(): Unit = {
    manager ! Register(slug, self)
  }

  override def postStop(): Unit = {
    manager ! Unregister(slug, self)
  }

  def receive: Receive = {
    case msg: String =>
      // Console logging requirement:
      logger.info(s"[Server WebSocket] message from client ($slug): $msg")

    case SendToClient(msg) =>
      out ! msg
  }
}

object WebSocketClientActor {
  def props(out: ActorRef, manager: ActorRef, slug: String): Props =
    Props(new WebSocketClientActor(out, manager, slug))

  case class SendToClient(msg: String)
}
