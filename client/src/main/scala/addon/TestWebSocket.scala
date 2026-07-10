package addon

import scala.concurrent.Future
import shared.basic.AppError
import services.WebSocketService

object TestWebSocket:
  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val msg = if (param.nonEmpty) param else "Hallo vom Client Addon!"
    WebSocketService.send(msg)
    println(s"[TestWebSocket] Sent message to server: $msg")
    Future.successful(Right(s"WebSocket-Nachricht gesendet: $msg"))
