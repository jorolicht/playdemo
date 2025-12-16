package usecases

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js
import cviews.usecases._
import base.*
import shared.*


enum IdConsole extends NamedId:
  case ConsoleId, ShowId, ClickId
  override def name: String = IdConsole.Prefix + "_" + this.toString  

object IdConsole:
  import scala.util.Try
  final val Prefix: String = "IdConsole"
  def fromId(id: String): Option[IdConsole] = 
    if (id.startsWith(Prefix)) then  Try(IdConsole.valueOf(id.stripPrefix(Prefix))).toOption else None  


object Console extends UseCase with JsWrapper:
  import IdConsole.*

  def render(param: String = ""): Boolean = 
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>${name}</h5></div>""")
    setData(gE2(ClickId), "command", param.replaceAll("_", " "))
    gE2(ClickId).click()
    true

  override def event(elem: HTMLElement, event: dom.Event) =   
    IdConsole.fromId(elem.id) match
      case Some(ConsoleId) => gE2(ConsoleId).click()
      case _               => debug(s"event -> unknown event for elem:${elem.id} with event:${event.`type`}") 

   
