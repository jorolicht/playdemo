package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js
import cviews.pages.*
import base.*
import shared.*
import shared.DomTypes.HtmlId

object Console extends BasePage with JsWrapper:

  val ConsoleId: HtmlId = HtmlId.fromName(name)
  val ClickId: HtmlId = HtmlId.fromName(name)

  def render(param: String = ""): Boolean = 
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>${name}</h5></div>""")
    setData(gE3(ClickId), "command", param.replaceAll("_", " "))
    gE3(ClickId).click()
    true

  override def event(elem: HTMLElement, event: dom.Event) =   
    HtmlId(elem.id) match
      case `ConsoleId` => gE3(ConsoleId).click()
      case _           => debug(s"event -> unknown event for elem:${elem.id} with event:${event.`type`}") 

   
