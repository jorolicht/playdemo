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
 * It follows the structure of ClubDB with global version-based locking.
 */
object PlayerDB extends ComWrapper with Debouncer:

  /**
   * Triggers a delayed synchronization with the server.
   */
  def triggerSync(): Unit =
    debounce(delay = 800) {
      sync()
    }

  val players: ArrayBuffer[Player] = ArrayBuffer()
  val pendingEvents: ArrayBuffer[Player] = ArrayBuffer() // Only Add/Update events

  def idx(id: PlayerId): Int = id.value - 1
  def validIdx(i: Int): Boolean = i >= 0 && i < players.length
  def nextId(): PlayerId = PlayerId(players.length + 1)

  var version: Int = 0

  case class PlayerSyncRequest(version: Int, players: Seq[Player]) derives ReadWriter
  case class PlayerSyncResponse(version: Int) derives ReadWriter
  case class PlayersResponse(version: Int, players: Seq[Player]) derives ReadWriter

  /**
   * Synchronizes pending player events with the WordPress server.
   */
  def sync(): Future[Either[AppError, Unit]] = {
    if pendingEvents.isEmpty then
      Future.successful(Right(()))
    else
      val route = "/wp-json/tourney/v1/players-sync"
      // Da alle Spieler in einem Meta-Feld liegen, senden wir den gesamten Stand.
      val req = PlayerSyncRequest(version, players.toSeq)
      val params = List("postId" -> Global.pageId.toString)

      ajaxPost[PlayerSyncRequest, PlayerSyncResponse](
        route,
        params,
        req
      ).flatMap {
        case Right(res) =>
          version = res.version
          pendingEvents.clear()
          Future.successful(Right(()))
        case Left(err) if err.is("version_mismatch") =>
          Logging.error(s"Sync fehlgeschlagen: Version-Mismatch bei Spielern. Lade neu... ${err.msg}")
          pendingEvents.clear()
          load().map(_ => Left(err))
        case Left(err) =>
          Future.successful(Left(err))
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
        version = res.version
        pendingEvents.clear()
        Logging.debug(s"PlayerDB.load: loaded ${players.length} players, version: $version")
        Right(version.toLong)
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
   * Updates an existing player.
   */
  def update(p: Player): Either[AppError, Player] =
    val i = idx(p.id)
    if (!validIdx(i)) {
      Left(AppError("player.notFound"))
    } else {
      players.update(i, p)
      pendingEvents += p
      triggerSync()
      Right(p)
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
      val mergedPlayer = players(mergedIdx)
      val updatedMerged = mergedPlayer.copy(active = false, merge = Some(mainId))
      players.update(mergedIdx, updatedMerged)
      pendingEvents += updatedMerged
      triggerSync()
      Right(())
    }
