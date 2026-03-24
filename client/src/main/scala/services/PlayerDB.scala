package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.*

import upickle.default.*
import shared.basic.AppError
import scala.util.control.NonFatal
import scala.collection.mutable.{ ArrayBuffer, Map }
import shared.model.PlayerId
import shared.model.Player
import base.{ Global, Logging }



object PlayerDB extends ComWrapper:

  var syncHandle: Option[SetTimeoutHandle] = None

  def triggerSync(): Unit =
    syncHandle.foreach(clearTimeout)

    syncHandle = Some(setTimeout(500) {
      sync()
      syncHandle = None
    })


  val player: ArrayBuffer[Player] = ArrayBuffer()
  val pendingEvents: ArrayBuffer[Player] = ArrayBuffer() // nur Add/Update Events

  def idx(id: PlayerId): Int = id.toInt - 1
  def validIdx(i: Int): Boolean = i >= 0 && i < players.length
  def nextId(): PlayerId = PlayerId(players.length + 1)

  var timestamp: Long = 0

  private val route = "/wp-json/tourney/v1/players-sync"

  case class PlayerSyncRequest(timestamp: Long, events: Seq[Player]) derives ReadWriter
  case class PlayerSyncResponse(timestamp: Long) derives ReadWriter
  case class PlayerResponse(timestamp: Long, players: Seq[Player]) derives ReadWriter


