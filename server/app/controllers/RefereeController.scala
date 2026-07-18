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
import shared.model._

object TTR {
  private val K = 16.0 // Standard Entwicklungskoeffizient

  /** Erwartete Gewinnwahrscheinlichkeit */
  def expectedScore(playerTTR: Int, opponentTTR: Int): Double =
    1.0 / (1.0 + math.pow(10.0, (opponentTTR - playerTTR) / 150.0))

  /**
   * TTR-Änderung
   *
   * @param playerTTR TTR des Spielers
   * @param opponentTTR TTR des Gegners
   * @param won true = Sieg, false = Niederlage
   * @return TTR-Veränderung (positiv oder negativ)
   */
  def delta(playerTTR: Int, opponentTTR: Int, won: Boolean): Double = {
    val result = if (won) 1.0 else 0.0
    K * (result - expectedScore(playerTTR, opponentTTR))
  }

  /** Neuer TTR-Wert (gerundet) */
  def newTTR(playerTTR: Int, opponentTTR: Int, won: Boolean): Int =
    math.round(playerTTR + delta(playerTTR, opponentTTR, won)).toInt
}

@Singleton
class RefereeController @Inject()(
  cc: ControllerComponents
)(implicit system: ActorSystem, ec: ExecutionContext) extends AbstractController(cc) with Logging {

  private val manager = actors.WebSocketManager.get
  private implicit val timeout: Timeout = Timeout(3.seconds)

  // Define play-json formatters for shared models
  implicit val stageIdFormat: Format[StageId] = new Format[StageId] {
    def reads(json: JsValue): JsResult[StageId] = json.validate[Int].map(StageId.apply)
    def writes(id: StageId): JsValue = JsNumber(id.value)
  }
  implicit val matchboardEntryFormat: Format[MatchboardEntry] = Json.format[MatchboardEntry]
  implicit val matchboardFormat: Format[Matchboard] = Json.format[Matchboard]

  def input(
    slug: String,
    tourneyName: String,
    competition: String,
    stage: String,
    roundInfo: String,
    players: String,
    winSets: Int
  ): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.RefereeInput(slug, tourneyName, competition, stage, roundInfo, players, winSets))
  }

  case class SubmitPayload(
    slug: String,
    players: String,
    roundInfo: String,
    sets: String,
    stageId: Option[Int],
    gameNo: Option[Int],
    ttrA: Option[Int],
    ttrB: Option[Int]
  )
  private implicit val payloadReads: Reads[SubmitPayload] = Json.reads[SubmitPayload]

  private def parseMatchResult(setsStr: String): Option[(Boolean, Boolean)] = {
    try {
      val sets = setsStr.split(",").map(_.trim).filter(_.nonEmpty)
      var winA = 0
      var winB = 0
      for (set <- sets) {
        val pts = set.split(":").map(_.trim.toInt)
        if (pts.length == 2) {
          if (pts(0) > pts(1)) winA += 1
          else if (pts(1) > pts(0)) winB += 1
        }
      }
      if (winA > winB) Some((true, false))
      else if (winB > winA) Some((false, true))
      else None
    } catch {
      case _: Throwable => None
    }
  }

  def submit(): Action[JsValue] = Action.async(parse.json) { request =>
    request.body.validate[SubmitPayload] match {
      case JsSuccess(payload, _) =>
        val message = s"Ergebnis für ${payload.players} (${payload.roundInfo}): ${payload.sets}"
        logger.info(s"Referee submitted result for slug '${payload.slug}': $message")

        // 1. In der MatchboardDB im Playserver wird der Eintrag mit gleicher stageId und gameNo gelöscht
        for {
          sId <- payload.stageId
          gNo <- payload.gameNo
        } {
          val data = services.MatchboardStore.getOrCreate(payload.slug)
          val stageIdObj = StageId(sId)
          val idx = data.matchboardDB.indexWhere(e => e.stageId.contains(stageIdObj) && e.gameNo.contains(gNo))
          if (idx >= 0) {
            data.matchboardDB.remove(idx)
            logger.info(s"Referee submit: Deleted matching matchboard entry for stageId=$sId, gameNo=$gNo in slug='${payload.slug}'")

            // Broadcast updated database to all WebSocket clients
            val updatedMatchboard = Matchboard(data.tourneyName, data.matchboardDB.toSeq)
            val jsonStr = Json.toJson(updatedMatchboard).toString()
            manager ! actors.WebSocketManagerActor.SendToSlug(payload.slug, s"MATCHBOARD_UPDATE:$jsonStr")
          }
        }

        // 2. TTR-Werteveränderung berechnen
        val ttrAVal = payload.ttrA.getOrElse(0)
        val ttrBVal = payload.ttrB.getOrElse(0)
        val ttrChanges = for {
          (wonA, wonB) <- parseMatchResult(payload.sets)
          if ttrAVal > 0 && ttrBVal > 0
        } yield {
          val deltaA = TTR.delta(ttrAVal, ttrBVal, wonA)
          val deltaB = TTR.delta(ttrBVal, ttrAVal, wonB)
          def formatDelta(d: Double): String = {
            val rounded = math.round(d).toInt
            if (rounded >= 0) s"+$rounded" else s"$rounded"
          }
          (formatDelta(deltaA), formatDelta(deltaB))
        }

        val setsClean = payload.sets.replace("·", ", ")
        val wsPayload = shared.model.RefereeSubmitWsMsg(
          stageId = payload.stageId,
          gameNo = payload.gameNo,
          sets = setsClean,
          players = payload.players,
          roundInfo = payload.roundInfo
        )
        val broadcastMsg = s"REFEREE_SUBMIT:${shared.basic.Pickle.write(wsPayload)}"

        // Unconditionally buffer the message in RefereeDB so the client can retrieve it if they reconnect/were offline
        services.MatchboardStore.addRefereeResult(payload.slug, broadcastMsg)

        (manager ? actors.WebSocketManagerActor.SendToSlug(payload.slug, broadcastMsg)).mapTo[Boolean].map {
          case isSent =>
            ttrChanges match {
              case Some((deltaA, deltaB)) =>
                Ok(Json.obj(
                  "success" -> true,
                  "buffered" -> true,
                  "ttrCalculated" -> true,
                  "deltaA" -> deltaA,
                  "deltaB" -> deltaB
                ))
              case None =>
                Ok(Json.obj("success" -> true, "buffered" -> true))
            }
        }

      case JsError(errors) =>
        Future.successful(BadRequest(Json.obj("error" -> "Invalid JSON payload", "details" -> JsError.toJson(errors))))
    }
  }
}
