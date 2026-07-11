package controllers

import javax.inject._
import org.apache.pekko.actor._
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout
import play.api.mvc._
import play.api.libs.json._
import play.api.Logging
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

@Singleton
class RefereeController @Inject()(
  cc: ControllerComponents
)(implicit system: ActorSystem, ec: ExecutionContext) extends AbstractController(cc) with Logging {

  private val manager = actors.WebSocketManager.get
  private implicit val timeout: Timeout = Timeout(3.seconds)

  def input(
    slug: String,
    tourneyName: String,
    competition: String,
    stage: String,
    roundInfo: String,
    players: String
  ): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.RefereeInput(slug, tourneyName, competition, stage, roundInfo, players))
  }

  case class SubmitPayload(slug: String, players: String, roundInfo: String, sets: String)
  private implicit val payloadReads: Reads[SubmitPayload] = Json.reads[SubmitPayload]

  def submit(): Action[JsValue] = Action.async(parse.json) { request =>
    request.body.validate[SubmitPayload] match {
      case JsSuccess(payload, _) =>
        val message = s"Ergebnis für ${payload.players} (${payload.roundInfo}): ${payload.sets}"
        logger.info(s"Referee submitted result for slug '${payload.slug}': $message")
        
        (manager ? actors.WebSocketManagerActor.SendToSlug(payload.slug, message)).mapTo[Boolean].map {
          case true =>
            Ok(Json.obj("success" -> true))
          case false =>
            logger.warn(s"WebSocket client not connected for slug '${payload.slug}'")
            BadRequest(Json.obj("error" -> "Turnierleitung z.Z. nicht aktiv"))
        }
      case JsError(errors) =>
        Future.successful(BadRequest(Json.obj("error" -> "Invalid JSON payload", "details" -> JsError.toJson(errors))))
    }
  }
}
