package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.*

import shared.basic.Pickle.*
import shared.basic.AppError
import scala.util.control.NonFatal
import scala.collection.mutable.ArrayBuffer
import shared.model.{Competition, CompId, CompTyp, CompStatus}
import base.{Global, Logging}

/**
 * CompetitionDB provides methods to manage competitions and synchronize them with the server.
 * It supports up to 64 competitions.
 */
object CompetitionDB extends ComWrapper with Debouncer:

  val MaxComps = 64
  val competitions: ArrayBuffer[Competition] = ArrayBuffer.fill(MaxComps)(null)
  val pendingEvents: ArrayBuffer[Competition] = ArrayBuffer() 

  var timestamp: Long = 0

  private val route = "/wp-json/tourney/v1/competitions-sync"

  case class CompetitionSyncRequest(timestamp: Long, events: Seq[Competition]) derives ReadWriter
  case class CompetitionSyncResponse(timestamp: Long) derives ReadWriter
  case class CompetitionsResponse(competitions: Seq[Competition]) derives ReadWriter

  def triggerSync(): Unit =
    debounce(delay = 800) {
      Logging.debug("Synchronisiere Wettbewerbe mit dem Server...")
      sync()
    }

  /**
   * Synchronizes pending competition events with the WordPress server.
   */
  def sync(): Future[Either[AppError, Unit]] = {
    if pendingEvents.isEmpty then
      Future.successful(Right(()))
    else
      val req = CompetitionSyncRequest(timestamp, pendingEvents.toSeq)
      val params = List("postId" -> Global.pageId.toString)

      ajaxPost[CompetitionSyncRequest, CompetitionSyncResponse](
        route,
        params,
        req
      ).map {
        case Right(res) =>
          timestamp = res.timestamp
          pendingEvents.clear()
          Right(())
        case Left(err) =>
          Left(err)
      }
  }

  /**
   * Loads competitions from the WordPress server.
   */
  def load(): Future[Either[AppError, Long]] = {
    if (Global.pageId == 0) {
      Logging.debug("CompetitionDB.load: postId is 0, skipping load")
      return Future.successful(Right(0L))
    }

    val params = List("postId" -> Global.pageId.toString)
    ajaxGet[CompetitionsResponse]("/wp-json/tourney/v1/competitions", params).map {
      case Right(res) =>
        for (i <- 0 until MaxComps) competitions(i) = null
        res.competitions.foreach { c =>
           val i = c.id.value - 1
           if (i >= 0 && i < MaxComps) then
             competitions(i) = c
        }
        pendingEvents.clear()
        Logging.debug(s"CompetitionDB.load: loaded ${res.competitions.length} competitions")
        Right(0L)
      case Left(err) => Left(err)
    }
  }

  /**
   * Adds a new competition. 
   * Finds the first empty slot or reuses the first deleted slot if all slots are full.
   */
  def add(name: String, typ: CompTyp, startDate: String): Either[AppError, Competition] =
    val firstNull = competitions.indexOf(null)
    if (firstNull != -1) {
      val c = Competition(CompId(firstNull + 1), name, typ, startDate, CompStatus.READY)
      competitions(firstNull) = c
      pendingEvents += c
      triggerSync()
      Right(c)
    } else {
      val firstDeleted = competitions.indexWhere(c => c != null && c.deleted)
      if (firstDeleted != -1) {
        val c = Competition(CompId(firstDeleted + 1), name, typ, startDate, CompStatus.READY)
        competitions(firstDeleted) = c
        pendingEvents += c
        triggerSync()
        Right(c)
      } else {
        Left(AppError("max.competitions.reached", "Maximal 64 Wettbewerbe sind erlaubt."))
      }
    }

  /**
   * Deletes a competition (soft delete).
   */
  def delete(id: CompId): Either[AppError, Competition] =
    val i = id.value - 1
    if (i < 0 || i >= MaxComps || competitions(i) == null) {
      Left(AppError("competition.notFound"))
    } else {
      val c = competitions(i).copy(deleted = true)
      competitions(i) = c
      pendingEvents += c
      triggerSync()
      Right(c)
    }
