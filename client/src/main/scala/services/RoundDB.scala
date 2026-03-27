package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.*

import shared.basic.Pickle.*
import shared.basic.AppError
import scala.util.control.NonFatal
import scala.collection.mutable.ArrayBuffer
import shared.model.{Round, RoundId, CompId, RoundCfg, RoundStatus}
import base.{Global, Logging}

/**
 * RoundDB provides methods to manage rounds and synchronize them with the WordPress server.
 * It supports up to 128 rounds.
 * Rounds are stored in custom post type meta fields round001 to round128.
 */
object RoundDB extends ComWrapper with Debouncer:

  val MaxRounds = 128
  val rounds: ArrayBuffer[Round] = ArrayBuffer.fill(MaxRounds)(null)
  val pendingEvents: ArrayBuffer[Round] = ArrayBuffer() 

  var timestamp: Long = 0

  private val route = "/wp-json/tourney/v1/rounds-sync"

  case class RoundSyncRequest(timestamp: Long, events: Seq[Round]) derives ReadWriter
  case class RoundSyncResponse(timestamp: Long) derives ReadWriter
  case class RoundsResponse(rounds: Seq[Round]) derives ReadWriter

  def triggerSync(): Unit =
    debounce(delay = 800) {
      Logging.debug("Synchronisiere Runden mit dem Server...")
      sync()
    }

  /**
   * Synchronizes pending round events with the WordPress server.
   */
  def sync(): Future[Either[AppError, Unit]] = {
    if pendingEvents.isEmpty then
      Future.successful(Right(()))
    else
      val req = RoundSyncRequest(timestamp, pendingEvents.toSeq)
      val params = List("postId" -> Global.pageId.toString)

      ajaxPost[RoundSyncRequest, RoundSyncResponse](
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
   * Loads rounds from the WordPress server.
   */
  def load(): Future[Either[AppError, Long]] = {
    if (Global.pageId == 0) {
      Logging.debug("RoundDB.load: postId is 0, skipping load")
      return Future.successful(Right(0L))
    }

    val params = List("postId" -> Global.pageId.toString)
    ajaxGet[RoundsResponse]("/wp-json/tourney/v1/rounds", params).map {
      case Right(res) =>
        for (i <- 0 until MaxRounds) rounds(i) = null
        res.rounds.foreach { r =>
           val i = r.id.value - 1
           if (i >= 0 && i < MaxRounds) then
             rounds(i) = r
        }
        pendingEvents.clear()
        Logging.debug(s"RoundDB.load: loaded ${res.rounds.length} rounds")
        Right(0L)
      case Left(err) => Left(err)
    }
  }

  /**
   * Adds a new round. 
   * Finds the first empty slot or reuses the first deleted slot if all slots are full.
   * If prefId is None, sets this round as startRound in the competition.
   * If prefId is provided, adds this round to the nextIds of the predecessor.
   */
  def addRound(coId: CompId, prefId: Option[RoundId], name: String, rndCfg: RoundCfg, size: Int, noPlayers: Int): Either[AppError, Round] =
    val firstNull = rounds.indexOf(null)
    val index = if (firstNull != -1) {
      firstNull
    } else {
      rounds.indexWhere(r => r != null && r.deleted)
    }

    if (index != -1) {
      val id = RoundId(index + 1)
      val now = (System.currentTimeMillis() / 1000).toLong
      val r = Round(
        id = id,
        coId = coId,
        name = name,
        rndCfg = rndCfg,
        status = RoundStatus.CFG,
        demo = false,
        size = size,
        noPlayers = noPlayers,
        prefId = prefId,
        nextIds = List(),
        timestamp = now
      )
      rounds(index) = r
      pendingEvents += r

      // Update predecessor
      prefId.foreach { pid =>
        val pIdx = pid.value - 1
        if (pIdx >= 0 && pIdx < MaxRounds && rounds(pIdx) != null) {
          val pref = rounds(pIdx)
          val updatedPref = pref.copy(nextIds = pref.nextIds :+ id, timestamp = now)
          rounds(pIdx) = updatedPref
          pendingEvents += updatedPref
        }
      }

      // Update Competition if no predecessor (sets as startRound)
      if (prefId.isEmpty) {
        val cIdx = coId.value - 1
        if (cIdx >= 0 && cIdx < CompetitionDB.MaxComps && CompetitionDB.competitions(cIdx) != null) {
          val comp = CompetitionDB.competitions(cIdx)
          if (comp.startRound.isEmpty) {
            val updatedComp = comp.copy(startRound = Some(id), timestamp = now)
            CompetitionDB.competitions(cIdx) = updatedComp
            CompetitionDB.pendingEvents += updatedComp
            CompetitionDB.triggerSync()
          }
        }
      }

      triggerSync()
      Right(r)
    } else {
      Left(AppError("max.rounds.reached", "Maximal 128 Runden sind erlaubt."))
    }

  /**
   * Deletes a round and its successors (soft delete).
   * If the round was the startRound of its competition, sets startRound to None.
   */
  def deleteRound(id: RoundId): Either[AppError, Unit] =
    val i = id.value - 1
    if (i < 0 || i >= MaxRounds || rounds(i) == null) {
      Left(AppError("round.notFound"))
    } else {
      val r = rounds(i)
      val now = (System.currentTimeMillis() / 1000).toLong
      
      // Delete successors recursively
      r.nextIds.foreach(deleteRound)

      // Soft delete current round
      val updatedR = rounds(i).copy(deleted = true, timestamp = now)
      rounds(i) = updatedR
      pendingEvents += updatedR

      // Remove from predecessor's nextIds
      r.prefId.foreach { pid =>
        val pIdx = pid.value - 1
        if (pIdx >= 0 && pIdx < MaxRounds && rounds(pIdx) != null) {
          val pref = rounds(pIdx)
          val updatedPref = pref.copy(nextIds = pref.nextIds.filterNot(_ == id), timestamp = now)
          rounds(pIdx) = updatedPref
          pendingEvents += updatedPref
        }
      }

      // Update Competition if it was startRound
      val cIdx = r.coId.value - 1
      if (cIdx >= 0 && cIdx < CompetitionDB.MaxComps && CompetitionDB.competitions(cIdx) != null) {
        val comp = CompetitionDB.competitions(cIdx)
        if (comp.startRound.contains(id)) {
          val updatedComp = comp.copy(startRound = None, timestamp = now)
          CompetitionDB.competitions(cIdx) = updatedComp
          CompetitionDB.pendingEvents += updatedComp
          CompetitionDB.triggerSync()
        }
      }

      triggerSync()
      Right(())
    }
