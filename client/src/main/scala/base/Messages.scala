package base

import org.scalajs.dom
import upickle.default._
import scala.scalajs.js
import scala.concurrent.Future
import scala.collection.mutable.Map
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.*

import shared.model.AppError
import services.ComWrapper

// atou - decode base64 encoded string to utf
def atou(text: String) = js.Dynamic.global.atou(text.asInstanceOf[js.Any]).asInstanceOf[String]
// utob - encode string to base64
def utob(b64: String) = js.Dynamic.global.utob(b64.asInstanceOf[js.Any]).asInstanceOf[String]

object Messages extends JsWrapper with ComWrapper:
  private var messages: Map[String, String] = Map (""->"")

  def initMsg(version: String, lang: String): Future[Boolean] =
    messages = read[Map[String, String]](getLocalStorage("messages", "{}"))
    if (messages.isDefinedAt("app.version") && messages("app.version") == version) then
      debug("Took messages from local storage")
      Future(true)
    else
      loadConfigMsg(s"./data/msgs_${lang}.json").map {
        case Left(err)   => error(s"Loading messages from server: ${err}") ; false
        case Right(msgs) => 
          debug("Load new messages from server")
          setLocalStorage("messages", write[Map[String, String]](msgs)) 
          messages = msgs 
          true
      }

  /** getMsg
    *
    * @param key of message
    * @param args inserts to message
    */
  def getMsg(key: String, args: String*): String = 
    try
      var m = messages(key)
      args.zipWithIndex.foreach{ case(x,i) => m = m.replace(s"{${i}}",x) }
      m
    catch { case _: Throwable => error(s"getMsg -> key:${key} args:${args.mkString(":")} not found"); key }


  /** getErr
    *
    * @param err
    */
  def getErr(err: AppError): String = 
    try messages(err.msgCode).replace(s"{0}", err.in1).replace(s"{1}", err.in2)
    catch { case _: Throwable => error(s"getErr -> key:${err.msgCode} in1:${err.in1} in2:${err.in2} not found"); err.msgCode }     



  def loadConfigMsg(jsFile: String): Future[Either[AppError, Map[String, String]]] = {
    // Die Hauptlogik muss im Future bleiben, um asynchrone Fehler zu fangen
    dom.fetch(jsFile)
      .toFuture
      .flatMap { response =>
        // --- 1. HTTP-Status prüfen ---
        if (!response.ok) {
          // Fehler asynchron zurückgeben (z.B. bei 404 Not Found)
          Future.successful(Left(AppError(s"HTTP-Fehler ${response.status} beim Laden von $jsFile")))
        } else {
          // Response-Body als Text lesen
          response.text().toFuture
        }
      }
      .flatMap {
        case Left(error) => Future.successful(Left(error)) // Fehler aus dem Response-Check weiterleiten
        case jsonString: String =>
          // --- 2. JSON-Parsing mit try-catch ---
          try {
            val configMap = read[Map[String, String]](jsonString)
            Future.successful(Right(configMap))
          } catch {
            // Parsing-Fehler asynchron zurückgeben
            case e: Exception =>
              Future.successful(Left(AppError(s"Parsing-Fehler in $jsFile: ${e.getMessage}")))
          }
      }
      // --- 3. Generische Fehlerbehandlung (z.B. Netzwerkfehler) ---
      .recover {
        case e: Throwable =>
          Left(AppError(s"Unerwarteter Fehler beim Fetch: ${e.getMessage}"))
      }
  }