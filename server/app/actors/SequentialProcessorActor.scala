package actors

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object SequentialProcessorActor {

  // Define the messages that the actor can handle
  sealed trait Command
  final case class Process(message: String, replyTo: ActorRef[Response]) extends Command

  // Define the responses that the actor can send
  sealed trait Response
  final case class Processed(message: String) extends Response

  def apply(): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case Process(msg, replyTo) =>
          context.log.info(s"Processing message: $msg")
          // Simulate some processing time
          Thread.sleep(1000)
          val result = s"Processed: $msg"
          context.log.info(s"Finished processing message: $msg")
          replyTo ! Processed(result)
          Behaviors.same
      }
    }
}
