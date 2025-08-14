package actors

import play.api.Logging
import org.apache.pekko.actor._

/**
 * Manager Actor triggers clients waiting on messages for clients
 */
class SSEMgmtActor extends Actor with Logging {
  import scala.collection.mutable.Set
  import scala.collection.mutable.HashMap
  import SSEMgmtActor._

  private val aRefMap = new HashMap[String, org.apache.pekko.actor.typed.ActorRef[String]]

  def receive = {
    case Register(id, aRef)       => aRefMap(id) = aRef
    case UnRegister(id)           => aRefMap.remove(id); 
    case SendMessage(id, message) => if (aRefMap.contains(id)) then aRefMap(id) ! message else logger.debug(s"No actor reference for id: ${id}")
  }
}

object SSEMgmtActor {
  def props: Props = Props[SSEMgmtActor]()
  case class SendMessage(id: String, message: String)
  case class Register(id: String, actorRef: org.apache.pekko.actor.typed.ActorRef[String])
  case class UnRegister(id: String)
}