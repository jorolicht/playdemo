package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import upickle.default.*
import shared.basic.AppError
import scala.util.control.NonFatal
import scala.collection.mutable.{ ArrayBuffer, Map }
import shared.model.ClubId
import shared.model.Club

object ClubDB extends ComWrapper:
  val clubs: ArrayBuffer[Club] = ArrayBuffer()
  val pendingEvents: ArrayBuffer[Club] = ArrayBuffer() // nur Add/Update Events

  def idx(id: ClubId): Int = id.toInt - 1
  def validIdx(i: Int): Boolean = i >= 0 && i < clubs.length
  def nextId(): ClubId = ClubId(clubs.length + 1)

  var timestamp: Long = 0
  var postId: Int = 0
  var metaField: String = "Clubs"

  private val route = "/wp-json/tourney/v1/clubs-sync"

  case class ClubSyncRequest(timestamp: Long, events: Seq[Club]) derives ReadWriter
  case class ClubSyncResponse(timestamp: Long) derives ReadWriter
  case class ClubsResponse(timestamp: Long, clubs: Seq[Club]) derives ReadWriter

  def sync(): Future[Either[AppError, Unit]] = {
    if pendingEvents.isEmpty then
      Future.successful(Right(()))
    else
      val req = ClubSyncRequest(timestamp, pendingEvents.toSeq)
      val params = List(
        "postId" -> postId.toString,
        "metafield-name" -> metaField
      )

      ajaxPost[ClubSyncRequest, ClubSyncResponse](
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

  def load(): Future[Either[AppError, Unit]] = {
    val params = List(
      "postId" -> postId.toString,
      "metafield-name" -> metaField
    )
    ajaxGet[ClubsResponse](s"/wp-json/tourney/v1/clubs", params).map {
      case Right(res) =>
        clubs.clear()
        clubs ++= res.clubs
        timestamp = res.timestamp
        pendingEvents.clear()
        Right(())
      case Left(err) => Left(err)
    }
  }


  def add(name: String): Either[AppError, Club] =
    try
      val normalized = Club.normalize(name)
      Club.findSimilar(name, clubs) match

        case Some((existingId, _)) =>
          val i = idx(existingId)

          if !validIdx(i) then
            Left(AppError("club.index.corrupt"))
          else
            val existing = clubs(i)

            if !existing.active then
              val updated = existing.copy(active = true)
              clubs.update(i, updated)
              pendingEvents += updated

            Right(clubs(i))

        case None =>
          val id = nextId()

          val club =
            Club(
              id = id,
              name = name.trim,
              normalizedName = normalized,
              active = true
            )

          clubs += club
          pendingEvents += club
          Right(club)

    catch
      case NonFatal(e) =>
        Left(AppError(s"club.add.failed: ${e.getMessage}"))  


// def deleteClub(id: ClubId): Unit = {
//   val index = id.value - 1
//   if index >= 0 && index < clubs.length then
//     val c = clubs(index)
//     val deleted = c.copy(active = false)    // soft delete
//     clubs(index) = deleted
//     pendingEvents += deleted                // Event für sync
// }

  def deleteClub(id: ClubId): Either[AppError, Club] =
    val i = idx(id)

    if !validIdx(i) then
      Left(AppError("club.notFound"))
    else
      val club = clubs(i)

      if !club.active then
        Right(club)
      else
        val updated = club.copy(active = false)
        clubs.update(i, updated)
        pendingEvents += updated
        Right(updated)


  def merge(
    sourceId: ClubId,
    targetId: ClubId
  ): Either[String, Club] =

    if sourceId == targetId then
      return Left("Source und Target sind identisch.")

    val sourceIdx = idx(sourceId)
    val targetIdx = idx(targetId)

    if !validIdx(sourceIdx) || !validIdx(targetIdx) then
      return Left("Source oder Target Club existiert nicht.")

    val source = clubs(sourceIdx)
    val target = clubs(targetIdx)

    if !source.active then
      return Left(s"Source-Club '${source.name}' ist bereits deaktiviert.")

    // Spieler umhängen
    PlayerDB.players.indices.foreach { i =>
      if PlayerDB.players(i).clubId == sourceId.toInt then
        PlayerDB.players.update(i, PlayerDB.players(i).copy(clubId = targetId.toInt))
    }

    // CTT zusammenführen
    val mergedCtt = target.ctt.orElse(source.ctt)
    val updatedTarget = target.copy(ctt = mergedCtt)

    clubs.update(targetIdx, updatedTarget)

    // Source deaktivieren
    clubs.update(sourceIdx, source.copy(active = false))

    Right(updatedTarget)
