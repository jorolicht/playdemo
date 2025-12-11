package base

import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.scalajs.js.typedarray._
import org.scalajs.dom

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global



@js.native
@JSGlobal("TextEncoder")
class TextEncoder() extends js.Object {
  def encode(input: String): js.typedarray.Uint8Array = js.native
}

object CryptoUtils {

  /** SHA-256 Hash als Hex */
  def sha256(str: String): Future[String] = {
    val encoder = new TextEncoder()
    val data: Uint8Array = encoder.encode(str)

    dom.crypto.subtle.digest("SHA-256", data).toFuture.map { buffer =>
      // ← wichtig!
      val arrBuffer = buffer.asInstanceOf[ArrayBuffer]
      val bytes = new Uint8Array(arrBuffer)

      val hex = (0 until bytes.length)
        .map(i => f"${bytes(i)}%02x")
        .mkString

      hex
    }
  }

  /** Base64 → UTF-8 */
  def atou(b64: String): String = {
    val decoded = dom.window.atob(b64)
    decodeURIComponent(escape(decoded))
  }

  /** UTF-8 → Base64 */
  def utob(str: String): String = {
    val escaped = unescape(encodeURIComponent(str))
    dom.window.btoa(escaped)
  }

  private def escape(str: String): String =
    js.Dynamic.global.escape(str).asInstanceOf[String]

  private def unescape(str: String): String =
    js.Dynamic.global.unescape(str).asInstanceOf[String]

  private def encodeURIComponent(str: String): String =
    js.URIUtils.encodeURIComponent(str)

  private def decodeURIComponent(str: String): String =
    js.URIUtils.decodeURIComponent(str)
}
