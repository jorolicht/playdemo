package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.*

import shared.basic.Pickle.*
import shared.basic.AppError
import scala.util.control.NonFatal
import scala.collection.mutable.ArrayBuffer
import shared.model.{Player, PlayerId}
import base.{Global, Logging}

/**
 * PlayerDB provides methods to manage players and synchronize them with the server.
 * It follows the structure of ClubDB.
 */
object PlayerDB extends ComWrapper:

  var syncHandle: Option[SetTimeoutHandle] = None

  /**
   * Triggers a delayed synchronization with the server.
   */
  def triggerSync(): Unit =
    syncHandle.foreach(clearTimeout)

    syncHandle = Some(setTimeout(500) {
      sync()
      syncHandle = None
    })

  val players: ArrayBuffer[Player] = ArrayBuffer()
  val pendingEvents: ArrayBuffer[Player] = ArrayBuffer() // Only Add/Update events

  def idx(id: PlayerId): Int = id.value - 1
  def validIdx(i: Int): Boolean = i >= 0 && i < players.length
  def nextId(): PlayerId = PlayerId(players.length + 1)

  var timestamp: Long = 0

  case class PlayerSyncRequest(timestamp: Long, events: Seq[Player]) derives ReadWriter
  case class PlayerSyncResponse(timestamp: Long) derives ReadWriter
  case class PlayersResponse(timestamp: Long, players: Seq[Player]) derives ReadWriter

  /**
   * Synchronizes pending player events with the WordPress server.
   */
  def sync(): Future[Either[AppError, Unit]] = {
    if pendingEvents.isEmpty then
      Future.successful(Right(()))
    else
      val route = "/wp-json/tourney/v1/players-sync"
      val req = PlayerSyncRequest(timestamp, pendingEvents.toSeq)
      val params = List("postId" -> Global.pageId.toString)

      ajaxPost[PlayerSyncRequest, PlayerSyncResponse](
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
   * Loads players from the WordPress server.
   */
  def load(): Future[Either[AppError, Long]] = {
    if (Global.pageId == 0) {
      Logging.debug("PlayerDB.load: postId is 0, skipping load")
      return Future.successful(Right(0L))
    }

    val params = List("postId" -> Global.pageId.toString)
    ajaxGet[PlayersResponse]("/wp-json/tourney/v1/players", params).map {
      case Right(res) =>
        players.clear()
        players ++= res.players
        timestamp = res.timestamp
        pendingEvents.clear()
        Logging.debug(s"PlayerDB.load: loaded ${players.length} players, timestamp: $timestamp")
        Right(res.timestamp)
      case Left(err) => Left(err)
    }
  }

  /**
   * Adds a new player if no player with the same firstName, lastName, clubId, and birthYear exists.
   */
  def add(
    firstName: String,
    lastName: String,
    clubId: Int,
    birthYear: Option[Int] = None
  ): Either[AppError, Player] =
    val exists = players.exists(p =>
      p.firstName == firstName &&
      p.lastName == lastName &&
      p.clubId == clubId &&
      p.birthYear == birthYear
    )

    if (exists) {
      Left(AppError("player.already.exists", s"$firstName $lastName already exists in club $clubId"))
    } else {
      val player = Player(
        id = nextId(),
        firstName = firstName,
        lastName = lastName,
        clubId = clubId,
        birthYear = birthYear,
        active = true
      )
      players += player
      pendingEvents += player
      triggerSync()
      Right(player)
    }

  /**
   * Deactivates a player (soft delete).
   */
  def delete(id: PlayerId): Either[AppError, Player] =
    val i = idx(id)
    if (!validIdx(i)) {
      Left(AppError("player.notFound"))
    } else {
      val player = players(i)
      if (!player.active) {
        Right(player)
      } else {
        player.active = false
        pendingEvents += player
        triggerSync()
        Right(player)
      }
    }

  /**
   * Merges two players. The mergedId player is deactivated and its merge field points to mainId.
   */
  def merge(mainId: PlayerId, mergedId: PlayerId): Either[AppError, Unit] =
    val mainIdx = idx(mainId)
    val mergedIdx = idx(mergedId)

    if (!validIdx(mainIdx) || !validIdx(mergedIdx)) {
      Left(AppError("player.merge.notFound", "Main or Merged player not found"))
    } else if (mainId == mergedId) {
      Left(AppError("player.merge.sameId", "Cannot merge a player with themselves"))
    } else {
      val mainPlayer = players(mainIdx)
      val mergedPlayer = players(mergedIdx)

      mergedPlayer.active = false
      mergedPlayer.merge = Some(mainId)

      pendingEvents += mergedPlayer
      triggerSync()
      Right(())
    }
