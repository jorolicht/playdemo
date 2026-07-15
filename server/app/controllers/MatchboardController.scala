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
  implicit val stageIdFormat: Format[StageId] = new Format[StageId] {
    def reads(json: JsValue): JsResult[StageId] = json.validate[Int].map(StageId.apply)
    def writes(id: StageId): JsValue = JsNumber(id.value)
  }
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
          val act = if (entry.entryType.nonEmpty) entry.entryType else payload.action
          act match {
            case "start" | "set" =>
              // falls in MatchboardDB bereits Eintrag mit gleichem court, dann den Eintrag löschen (falls court nicht leer)
              entry.court.filter(_.trim.nonEmpty).foreach { courtNum =>
                val idx = data.matchboardDB.indexWhere(e => e.court.contains(courtNum))
                if (idx >= 0) {
                  data.matchboardDB.remove(idx)
                  logger.info(s"Deleted existing entry with same court '$courtNum'")
                }
              }
              // falls in MatchboardDB bereits Eintrag mit gleicher stageId und gameNo, dann den Eintrag löschen
              for {
                sId <- entry.stageId
                gNo <- entry.gameNo
              } {
                val idx = data.matchboardDB.indexWhere(e => e.stageId.contains(sId) && e.gameNo.contains(gNo))
                if (idx >= 0) {
                  data.matchboardDB.remove(idx)
                  logger.info(s"Deleted existing entry with same stageId '$sId' and gameNo '$gNo'")
                }
              }
              // neuen Eintrag in MatchboardDB mit den Werten aus MatchboardEntry erstellen (falls court nicht leer)
              if (entry.court.exists(_.trim.nonEmpty)) {
                data.matchboardDB.append(entry)
                logger.info(s"Created new entry: id=${entry.id}, court=${entry.court}, gameNo=${entry.gameNo}")
              }
              changed = true

            case "finish" | "ended" =>
              // nur falls Eintrag mit gleicher stageId und gameNo existiert, diesen Eintrag in der MatchboardDB löschen
              for {
                sId <- entry.stageId
                gNo <- entry.gameNo
              } {
                val idx = data.matchboardDB.indexWhere(e => e.stageId.contains(sId) && e.gameNo.contains(gNo))
                if (idx >= 0) {
                  data.matchboardDB.remove(idx)
                  logger.info(s"Deleted finished/reset entry with stageId '$sId' and gameNo '$gNo'")
                  changed = true
                }
              }

            case "info" =>
              val idx = data.matchboardDB.indexWhere(_.id == entry.id)
              if (idx >= 0) {
                data.matchboardDB.update(idx, entry)
                logger.info(s"Replaced info entry '${entry.id}'")
              } else {
                data.matchboardDB.append(entry)
                logger.info(s"Added new info entry '${entry.id}'")
              }
              changed = true

            case "delete" =>
              val idx = data.matchboardDB.indexWhere(_.id == entry.id)
              if (idx >= 0) {
                data.matchboardDB.remove(idx)
                logger.info(s"Deleted entry '${entry.id}' by id")
                changed = true
              }

            case other =>
              logger.warn(s"Unknown action/entryType '$other' in matchboard/set request")
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
