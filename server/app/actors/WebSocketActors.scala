package actors

import org.apache.pekko.actor._
import play.api.Logging
import scala.collection.mutable

/**
 * Manager actor managing active WebSocket connections per tournament slug.
 */
class WebSocketManagerActor extends Actor with Logging {
  import WebSocketManagerActor._

  // Map of slug -> Set of client actor refs
  private val clients = mutable.HashMap[String, mutable.Set[ActorRef]]()

  override def preStart(): Unit = {
    import context.dispatcher
    import scala.concurrent.duration._
    context.system.scheduler.scheduleWithFixedDelay(15.minutes, 15.minutes)(new Runnable {
      override def run(): Unit = {
        logger.info("Running Matchboard and Referee DB cleanup...")
        services.MatchboardStore.cleanup()
      }
    })
  }

  def receive: Receive = {
    case Register(slug, ref) =>
      val set = clients.getOrElseUpdate(slug, mutable.Set.empty[ActorRef])
      set.add(ref)
      logger.info(s"Registered WebSocket client for slug '$slug'. Total clients for this slug: ${set.size}")

      // Push pending referee results if any
      val pending = services.MatchboardStore.getPendingRefereeResults(slug)
      if (pending.nonEmpty) {
        logger.info(s"Pushing ${pending.size} pending referee results to newly registered client for slug '$slug'")
        pending.foreach { resultStr =>
          ref ! WebSocketClientActor.SendToClient(resultStr)
        }
        services.MatchboardStore.clearRefereeResults(slug)
      }

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

    case Relay(slug, msg, from) =>
      clients.get(slug).foreach { set =>
        val others = set.filterNot(_ == from)
        logger.info(s"Relaying change notification for slug '$slug' to ${others.size} other client(s)")
        others.foreach(_ ! WebSocketClientActor.SendToClient(msg))
      }
  }
}

/**
 * Companion object for WebSocketManagerActor.
 */
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

/**
 * Messages for WebSocketManagerActor.
 */
object WebSocketManagerActor {
  def props: Props = Props[WebSocketManagerActor]()

  case class Register(slug: String, ref: ActorRef)
  case class Unregister(slug: String, ref: ActorRef)
  case class SendToSlug(slug: String, msg: String)

  /**
   * Multiuser: Änderungsmeldung eines Clients an alle anderen Clients desselben Turniers weiterreichen.
   *
   * @param slug Kanal (Turnier)
   * @param msg  Meldung im Format `SYNC:<json>`
   * @param from Client-Actor des Absenders (erhält die Meldung nicht zurück)
   */
  case class Relay(slug: String, msg: String, from: ActorRef)
}

/**
 * Client actor handling individual WebSocket client connections.
 * Logs incoming messages and responds to Hallo/Hello greeting messages.
 */
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
    case "ping" =>
      // Keep-alive message

    case msg: String if msg.startsWith(WebSocketClientActor.SyncPrefix) =>
      // Multiuser: Änderungsmeldung (Daten liegen in WordPress, die Empfänger laden selbst nach)
      if (msg.length <= WebSocketClientActor.MaxSyncLength) manager ! Relay(slug, msg, self)
      else logger.warn(s"[Server WebSocket] change notification too long (${msg.length}) for slug '$slug'")

    case msg: String =>
      logger.info(s"[Server WebSocket] message from client ($slug): $msg")

      val trimmed = msg.trim
      if (trimmed.toLowerCase.startsWith("hallo")) {
        out ! "Hallo hier ist der Server"
      } else if (trimmed.toLowerCase.startsWith("hello")) {
        out ! "Hello this is server speaking"
      }

    case SendToClient(msg) =>
      out ! msg
  }
}

/**
 * Companion object for WebSocketClientActor.
 */
object WebSocketClientActor {
  def props(out: ActorRef, manager: ActorRef, slug: String): Props =
    Props(new WebSocketClientActor(out, manager, slug))

  case class SendToClient(msg: String)

  /** Präfix der Multiuser-Änderungsmeldungen (`SYNC:<json>`). */
  val SyncPrefix = "SYNC:"

  /** Maximale Länge einer Änderungsmeldung in Zeichen. */
  val MaxSyncLength = 4096
}
