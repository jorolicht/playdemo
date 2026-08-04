package services

import org.scalajs.dom
import base.Global
import base.Logging.*

object WebSocketService:
  private var ws: Option[dom.WebSocket] = None
  private var currentSlug: Option[String] = None
  private var heartbeatInterval: Option[Int] = None
  private var hasEverOpened: Boolean = false
  
  // Callback when a message is received
  private var messageListener: Option[String => Unit] = None

  def setListener(listener: String => Unit): Unit = {
    messageListener = Some(listener)
  }

  def clearListener(): Unit = {
    messageListener = None
  }

  def getConnectedSlug: Option[String] = currentSlug

  def init(): Unit = {
    if (base.Global.isDemoMode || base.Global.isLocalMode || base.Global.isViewMode) {
      disconnect()
      return
    }
    Global.currentSelection.tourney match {
      case Some(t) if t.slug.nonEmpty =>
        // Extracts the slug name after any slash prefix if it contains one
        val slugName = t.slug.split('/').lastOption.getOrElse(t.slug)
        if (slugName.trim.nonEmpty) {
          connect(s"${slugName.trim}-${t.wpId}")
        } else {
          disconnect()
        }
      case _ =>
        disconnect()
    }
  }

  private def startHeartbeat(): Unit = {
    stopHeartbeat()
    heartbeatInterval = Some(dom.window.setInterval(() => {
      ws.foreach { socket =>
        if (socket.readyState == dom.WebSocket.OPEN) {
          socket.send("ping")
        }
      }
    }, 30000))
  }

  private def stopHeartbeat(): Unit = {
    heartbeatInterval.foreach(dom.window.clearInterval)
    heartbeatInterval = None
  }

  def connect(slug: String): Unit = {
    if (base.Global.isDemoMode || base.Global.isLocalMode || base.Global.isViewMode) {
      disconnect()
      return
    }
    // If already connected or connecting to the exact same slug, skip reconnecting
    if (currentSlug.contains(slug) && ws.exists(s => s.readyState == dom.WebSocket.OPEN || s.readyState == dom.WebSocket.CONNECTING)) {
      return
    }

    disconnect()
    currentSlug = Some(slug)
    hasEverOpened = false

    val wsUrl = if (Global.playUrl.nonEmpty) {
      val wsProtocol = if (Global.playUrl.startsWith("https:")) "wss:" else "ws:"
      val cleanPlayUrl = Global.playUrl.replace("https://", "").replace("http://", "")
      s"$wsProtocol//$cleanPlayUrl/ws/$slug"
    } else {
      val isLocalhost = dom.window.location.hostname == "localhost" || dom.window.location.hostname == "127.0.0.1"
      if (isLocalhost) {
        val protocol = if (dom.window.location.protocol == "https:") "wss:" else "ws:"
        val host = dom.window.location.host
        s"$protocol//$host/srv/ws/$slug"
      } else {
        s"ws://backend:9500/ws/$slug"
      }
    }
    
    debug(s"[WebSocket] Connecting to $wsUrl ...")
    
    try {
      val webSocket = new dom.WebSocket(wsUrl)
      ws = Some(webSocket)

      webSocket.onopen = (_: dom.Event) => {
        hasEverOpened = true
        debug(s"[WebSocket] Connected to server for slug '$slug'")
        startHeartbeat()
      }

      webSocket.onmessage = (e: dom.MessageEvent) => {
        val msg = e.data.toString
        debug(s"[WebSocket] Message received: $msg")
        if (msg.startsWith("REFEREE_SUBMIT:")) {
          val jsonStr = msg.substring(15)
          try {
            val payload = shared.basic.Pickle.read[shared.model.RefereeSubmitWsMsg](jsonStr)
            val displayMsg = s"Ergebnis für ${payload.players} (${payload.roundInfo}): ${payload.sets}"
            
            // 1. Add to TourneyAdmin message log
            pages.TourneyAdmin.addMessage(s"[Empfangen] $displayMsg")
            
            // 2. Update local Tourney DB MatchEntry info with prefix "Result "
            for {
              sId <- payload.stageId
              gNo <- payload.gameNo
            } {
              val stageIdObj = shared.model.StageId(sId)
              services.TourneyDB.tourney.stages.find(_.id == stageIdObj).foreach { stage =>
                stage.matches.find(_.gameNo == gNo).foreach { m =>
                  m.setInfo(s"Result ${payload.sets}")
                }
                services.TourneyDB.tourney.updateStage(stage) match {
                  case Right(updatedStage) =>
                    if (Global.currentSelection.stage.exists(_.id == stageIdObj)) {
                      Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
                    }
                  case Left(err) =>
                    debug(s"[WebSocket] Failed to update stage in TourneyDB: ${err.msgCode}")
                }
              }
              services.TourneyDB.sync()

              // If we are currently on StageInput page, force a redraw to show the updated info!
              val isStageInputActive = try {
                val storage = org.scalajs.dom.window.sessionStorage
                storage != null && storage.getItem("tourney_last_page") == "StageInput"
              } catch {
                case _: Throwable => false
              }
              if (isStageInputActive) {
                pages.Stage.StageInput.render()
              }
            }
          } catch {
            case ex: Throwable =>
              debug(s"[WebSocket] Error parsing REFEREE_SUBMIT message: ${ex.getMessage}")
          }
        } else {
          messageListener.foreach(_(msg))
        }
      }

      webSocket.onclose = (e: dom.CloseEvent) => {
        stopHeartbeat()
        if (currentSlug.contains(slug) && hasEverOpened) {
          debug(s"[WebSocket] Connection closed for slug '$slug': ${e.reason}")
          // Retry connection after 5 seconds if connection was previously established
          dom.window.setTimeout(() => {
            if (currentSlug.contains(slug)) connect(slug)
          }, 5000)
        }
      }

      webSocket.onerror = (_: dom.Event) => {
        debug(s"[WebSocket] Notice: WebSocket endpoint unavailable for slug '$slug'")
      }
    } catch {
      case ex: Exception =>
        error(s"[WebSocket] Failed to instantiate WebSocket: ${ex.getMessage}")
    }
  }

  def disconnect(): Unit = {
    stopHeartbeat()
    currentSlug = None
    ws.foreach { socket =>
      try {
        if (socket.readyState == dom.WebSocket.CONNECTING) {
          // If socket is in CONNECTING state, close gracefully upon opening to prevent browser error logs
          socket.onopen = (_: dom.Event) => {
            try { socket.close() } catch { case _: Throwable => () }
          }
          socket.onerror = (_: dom.Event) => ()
          socket.onclose = (_: dom.CloseEvent) => ()
        } else if (socket.readyState == dom.WebSocket.OPEN) {
          socket.onopen = (_: dom.Event) => ()
          socket.onmessage = (_: dom.MessageEvent) => ()
          socket.onerror = (_: dom.Event) => ()
          socket.onclose = (_: dom.CloseEvent) => ()
          socket.close()
        }
      } catch {
        case _: Exception => ()
      }
    }
    ws = None
  }

  def send(msg: String): Unit = {
    ws.foreach { socket =>
      if (socket.readyState == dom.WebSocket.OPEN) {
        socket.send(msg)
      } else {
        debug(s"[WebSocket] Cannot send message, socket state: ${socket.readyState}")
      }
    }
  }
