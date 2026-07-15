package controllers

import javax.inject._
import org.apache.pekko.actor._
import play.api.mvc._
import play.api.libs.json._
import play.api.Logging
import scala.concurrent.ExecutionContext
import shared.model._
import services.MatchboardStore

@Singleton
class MatchboardController @Inject()(
  cc: ControllerComponents
)(implicit system: ActorSystem, ec: ExecutionContext) extends AbstractController(cc) with Logging {

  private val manager = actors.WebSocketManager.get

  // Define play-json formatters for shared models
  implicit val matchboardEntryFormat: Format[MatchboardEntry] = Json.format[MatchboardEntry]
  implicit val matchboardFormat: Format[Matchboard] = Json.format[Matchboard]
  implicit val requestReads: Reads[MatchboardSetRequest] = Json.reads[MatchboardSetRequest]

  /**
   * GET  matchboard/view/:slug
   * Renders the premium real-time scoreboard view page.
   */
  def view(slug: String): Action[AnyContent] = Action { implicit request =>
    val data = MatchboardStore.getOrCreate(slug)
    val mb = Matchboard(data.tourneyName, data.matchboardDB.toSeq)
    val jsonStr = Json.toJson(mb).toString()
    Ok(views.html.MatchboardView(slug, mb, jsonStr))
  }

  /**
   * POST matchboard/get
   * Returns the entire Matchboard database for the slug.
   */
  def get(slug: String): Action[AnyContent] = Action { implicit request =>
    val data = MatchboardStore.getOrCreate(slug)
    Ok(Json.toJson(Matchboard(data.tourneyName, data.matchboardDB.toSeq)))
  }

  /**
   * POST matchboard/set
   * Sets/adds, ends, or deletes an entry in the Matchboard database,
   * and broadcasts the updated state to all connected websocket clients.
   */
  def set(slug: String): Action[JsValue] = Action(parse.json) { request =>
    logger.debug(s"matchboard/set Action invoked with: ${request.body}")
    request.body.validate[MatchboardSetRequest] match {
      case JsSuccess(payload, _) =>
        val data = MatchboardStore.getOrCreate(slug)
        var changed = false

        // Update tourney name if specified
        payload.tourneyName.foreach { name =>
          if (data.tourneyName != name) {
            data.tourneyName = name
            changed = true
          }
        }

        payload.entry.foreach { entry =>
          payload.action match {
            case "set" =>
              if (entry.entryType == "match") {
                entry.court.foreach { courtNum =>
                  val idx = data.matchboardDB.indexWhere(e => e.entryType == "match" && e.court.contains(courtNum))
                  if (idx >= 0) {
                    data.matchboardDB.update(idx, entry)
                    logger.info(s"Replaced match entry on court '$courtNum' for slug '$slug'")
                  } else {
                    data.matchboardDB.append(entry)
                    logger.info(s"Added new match entry on court '$courtNum' for slug '$slug'")
                  }
                  changed = true
                }
              } else if (entry.entryType == "info") {
                val idx = data.matchboardDB.indexWhere(_.id == entry.id)
                if (idx >= 0) {
                  data.matchboardDB.update(idx, entry)
                  logger.info(s"Replaced info entry '${entry.id}' for slug '$slug'")
                } else {
                  data.matchboardDB.append(entry)
                  logger.info(s"Added new info entry '${entry.id}' for slug '$slug'")
                }
                changed = true
              }

            case "ended" =>
              if (entry.entryType == "match") {
                entry.court.foreach { courtNum =>
                  val idx = data.matchboardDB.indexWhere(e => e.entryType == "match" && e.court.contains(courtNum))
                  if (idx >= 0) {
                    val existing = data.matchboardDB(idx)
                    // Only delete if both stageId and compId match
                    if (existing.stageId == entry.stageId && existing.compId == entry.compId) {
                      data.matchboardDB.remove(idx)
                      logger.info(s"Deleted match entry on court '$courtNum' (stageId and compId matched) for slug '$slug'")
                      changed = true
                    } else {
                      logger.warn(s"Skipped deleting match entry on court '$courtNum' for slug '$slug' because stageId or compId did not match.")
                    }
                  }
                }
              }

            case "delete" =>
              val idx = data.matchboardDB.indexWhere(_.id == entry.id)
              if (idx >= 0) {
                data.matchboardDB.remove(idx)
                logger.info(s"Deleted entry '${entry.id}' by id for slug '$slug'")
                changed = true
              }

            case other =>
              logger.warn(s"Unknown action '$other' in matchboard/set request for slug '$slug'")
          }
        }

        if (changed) {
          // Broadcast updated database to all WebSocket clients
          val updatedMatchboard = Matchboard(data.tourneyName, data.matchboardDB.toSeq)
          val jsonStr = Json.toJson(updatedMatchboard).toString()
          manager ! actors.WebSocketManagerActor.SendToSlug(slug, s"MATCHBOARD_UPDATE:$jsonStr")
        }

        Ok(Json.obj("success" -> true))

      case JsError(errors) =>
        BadRequest(Json.obj("error" -> "Invalid JSON payload", "details" -> JsError.toJson(errors)))
    }
  }
}
