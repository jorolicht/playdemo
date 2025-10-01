package base

import upickle.default._
import scala.scalajs.js
import scala.concurrent.Future
import scala.collection.mutable.Map
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue


import shared.model.AppError
import shared.Ids._
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
      debug("Take messages from local storage")
      Future(true)
    else
      fetchMsg(lang)

  /** fetchMsg - update local messages files from server
   */
  def fetchMsg(lang: String): Future[Boolean] = 
    ajaxGet[Map[String, String]]("/helper/getMessages", List(("lang", lang))).map {
      case Left(err)   => println(s"${err}"); false
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
