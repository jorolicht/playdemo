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
 * Optimistic locking is implemented using a version counter.
 */
object CompetitionDB extends ComWrapper with Debouncer:

  val MaxComps = 64
  val competitions: ArrayBuffer[Competition] = ArrayBuffer.fill(MaxComps)(null)
  val pendingEvents: ArrayBuffer[Competition] = ArrayBuffer() 

  private val route = "/wp-json/tourney/v1/competitions-sync"

  case class CompetitionSyncRequest(events: Seq[Competition]) derives ReadWriter
  case class CompetitionSyncResponse(success: Boolean) derives ReadWriter
  case class CompetitionsResponse(competitions: Seq[Competition]) derives ReadWriter

  def triggerSync(): Unit =
    debounce(delay = 800) {
      Logging.debug("Synchronisiere Wettbewerbe mit dem Server...")
      sync()
    }

  /**
   * Synchronizes pending competition events with the WordPress server.
   * If a version mismatch occurs (conflict), it clears pending events and reloads from the server.
   */
  def sync(): Future[Either[AppError, Unit]] = {
    if pendingEvents.isEmpty then
      Future.successful(Right(()))
    else
      val req = CompetitionSyncRequest(pendingEvents.toSeq)
      val params = List("postId" -> Global.pageId.toString)

      ajaxPost[CompetitionSyncRequest, CompetitionSyncResponse](
        route,
        params,
        req
      ).flatMap {
        case Right(res) =>
          pendingEvents.clear()
          Future.successful(Right(()))
        case Left(err) if err.is("version_mismatch") =>
          Logging.error(s"Sync fehlgeschlagen: Version-Mismatch bei Wettbewerben. Lade neu... ${err.msg}")
          pendingEvents.clear()
          load().map(_ => Left(err))
        case Left(err) =>
          Future.successful(Left(err))
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
        println(s"CompetitionDB.load: received ${res.competitions.length} competitions from server")
        competitions.clear()
        for (i <- 0 until MaxComps) competitions += null
        res.competitions.foreach { c =>
           val i = c.id.value - 1
           if (i >= 0 && i < MaxComps) then
             competitions(i) = c
        }
        pendingEvents.clear()
        Logging.debug(s"CompetitionDB.load: loaded ${res.competitions.length} competitions")
        Right(0L)
      case Left(err) => 
        Logging.error(s"CompetitionDB.load: failed to load competitions: ${err.msgCode}")
        Left(err)
    }
  }

  /**
   * Adds a new competition. 
   * Finds the first empty slot or reuses the first deleted slot if all slots are full.
   * Version is initialized to 1.
   */
  def add(name: String, typ: CompTyp, startDate: String): Either[AppError, Competition] =
    val firstNull = competitions.indexOf(null)
    val index = if (firstNull != -1) firstNull else competitions.indexWhere(c => c != null && c.deleted)
    
    if (index != -1) {
      val c = Competition(
        id = CompId(index + 1), 
        name = name, 
        typ = typ, 
        startDate = startDate, 
        status = CompStatus.READY,
        version = 1
      )
      competitions(index) = c
      pendingEvents += c
      triggerSync()
      Right(c)
    } else {
      Left(AppError("max.competitions.reached", "Maximal 64 Wettbewerbe sind erlaubt."))
    }

  /**
   * Deletes a competition (soft delete).
   * Increments the version counter.
   */
  def delete(id: CompId): Either[AppError, Competition] =
    val i = id.value - 1
    if (i < 0 || i >= MaxComps || competitions(i) == null) {
      Left(AppError("competition.notFound"))
    } else {
      val oldComp = competitions(i)
      val c = oldComp.copy(deleted = true, version = oldComp.version + 1)
      competitions(i) = c
      pendingEvents += c
      triggerSync()
      Right(c)
    }

  /**
   * Updates a competition.
   * Increments the version counter.
   */
  def update(comp: Competition): Either[AppError, Competition] =
    val i = comp.id.value - 1
    if (i < 0 || i >= MaxComps || competitions(i) == null) {
      Left(AppError("competition.notFound"))
    } else {
      val updatedComp = comp.copy(version = comp.version + 1)
      competitions(i) = updatedComp
      pendingEvents += updatedComp
      triggerSync()
      Right(updatedComp)
    }
