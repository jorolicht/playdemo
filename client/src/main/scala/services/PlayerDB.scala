package services

import upickle.default.*
import shared.basic.AppError
import shared.basic.*
import scala.util.control.NonFatal
import scala.collection.mutable.{ ArrayBuffer, Map }
import shared.model.PlayerId
import shared.model.Player


object PlayerDB:
  val players: ArrayBuffer[Player] = ArrayBuffer()
  var playerIdxName    : Map[(String,String,Int), ArrayBuffer[PlayerId]] = Map.empty
  var playerIdxLicence : Map[String, PlayerId] = Map.empty
  var playerIdxEmail   : Map[String, PlayerId] = Map.empty
  var timestamp: Int = 0
  
  private def keyOf(p: Player): (String, String, Int) =
    (
      p.lastName.trim.toLowerCase,
      p.firstName.trim.toLowerCase,
      p.clubId
    )


  /**
   * Add player if no identical player exists.
   * Unique key: firstName + lastName + clubId + birthYear
   */
  def add(player: Player): Either[AppError, PlayerId] =
    val key = keyOf(player)

    playerIdxName.get(key) match
      case Some(ids) =>
        val duplicate =
          ids.exists { id =>
            val p = players(id.idx)
            p.birthYear == player.birthYear && p.active
          }

        if duplicate then
          Left(AppError("Player already exists"))
        else
          insert(player)

      case None =>
        insert(player)


  private def insert(player: Player): Either[AppError, PlayerId] =
    val newId = PlayerId(players.length + 1)

    val newPlayer = player.copy(id = newId)

    players += newPlayer

    val key = keyOf(newPlayer)
    val list = playerIdxName.getOrElseUpdate(key, ArrayBuffer[PlayerId]())
    list += newId

    Right(newId)


  /**
   * Logical delete
   */
  def delete(id: PlayerId): Either[AppError, Unit] =
    
    if id.value >= players.length then
      Left(AppError("Player not found"))
    else
      val p = players(id.idx)
      p.active = false
      Right(())


  /**
   * Merge two players
   * mergedId -> mainId
   */
  def merge(mainId: PlayerId, mergedId: PlayerId): Either[AppError, Unit] =
    if !mainId.isValid(players.length) then
      Left(AppError("Main player not found"))

    if !mergedId.isValid(players.length) then
      return Left(AppError("Merged player not found"))

    if mainId == mergedId then
      return Left(AppError("Cannot merge identical players"))

    val mainPlayer = players(mainId.idx)
    val mergedPlayer = players(mergedId.idx)

    mergedPlayer.merge = Some(mainId)
    mergedPlayer.active = false

    Right(())
