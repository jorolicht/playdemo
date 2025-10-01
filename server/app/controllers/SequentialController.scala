package controllers

import javax.inject._
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.util.Timeout
import play.api.mvc._
import actors.SequentialProcessorActor

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class SequentialController @Inject()(val controllerComponents: ControllerComponents, system: ActorSystem[_])(implicit ec: ExecutionContext) extends BaseController {

  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler: org.apache.pekko.actor.typed.Scheduler = system.scheduler

  val processorActor: ActorRef[SequentialProcessorActor.Command] = system.systemActorOf(SequentialProcessorActor(), "sequentialProcessor")

  def process(message: String) = Action.async { implicit request: Request[AnyContent] =>
    val result = processorActor.ask(ref => SequentialProcessorActor.Process(message, ref))
    result.map {
      case response: SequentialProcessorActor.Processed => Ok(response.message)
      case _ => InternalServerError("Unexpected response from actor")
    }
  }
}
