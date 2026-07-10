package services

import org.scalajs.dom
import base.Global
import base.Logging.*

object WebSocketService:
  private var ws: Option[dom.WebSocket] = None
  private var currentSlug: Option[String] = None
  
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
    Global.currentSelection.tourney match {
      case Some(t) if t.slug.nonEmpty =>
        // Extracts the slug name after any slash prefix if it contains one
        val slugName = t.slug.split('/').lastOption.getOrElse(t.slug)
        if (slugName.trim.nonEmpty) {
          connect(slugName.trim)
        } else {
          disconnect()
        }
      case _ =>
        disconnect()
    }
  }

  def connect(slug: String): Unit = {
    if (currentSlug.contains(slug) && ws.exists(_.readyState == dom.WebSocket.OPEN)) {
      // Already connected to this slug
      return
    }

    disconnect()
    currentSlug = Some(slug)

    val protocol = if (dom.window.location.protocol == "https:") "wss:" else "ws:"
    val host = dom.window.location.host
    val wsUrl = s"$protocol//$host/ws/$slug"
    
    debug(s"[WebSocket] Connecting to $wsUrl ...")
    
    try {
      val webSocket = new dom.WebSocket(wsUrl)
      ws = Some(webSocket)

      webSocket.onopen = (e: dom.Event) => {
        debug(s"[WebSocket] Connected to server for slug '$slug'")
      }

      webSocket.onmessage = (e: dom.MessageEvent) => {
        val msg = e.data.toString
        debug(s"[WebSocket] Message received: $msg")
        messageListener.foreach(_(msg))
      }

      webSocket.onclose = (e: dom.CloseEvent) => {
        debug(s"[WebSocket] Connection closed for slug '$slug': ${e.reason}")
        if (currentSlug.contains(slug)) {
          // Retry connection after 5 seconds
          dom.window.setTimeout(() => {
            if (currentSlug.contains(slug)) connect(slug)
          }, 5000)
        }
      }

      webSocket.onerror = (e: dom.Event) => {
        debug(s"[WebSocket] Error occurred on connection for slug '$slug'")
      }
    } catch {
      case ex: Exception =>
        error(s"[WebSocket] Failed to instantiate WebSocket: ${ex.getMessage}")
    }
  }

  def disconnect(): Unit = {
    ws.foreach { socket =>
      if (socket.readyState == dom.WebSocket.OPEN || socket.readyState == dom.WebSocket.CONNECTING) {
        socket.close()
      }
    }
    ws = None
    currentSlug = None
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
