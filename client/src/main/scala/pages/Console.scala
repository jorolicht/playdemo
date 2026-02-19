package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js
import cviews.pages.*
import base.*
import shared.*


object Console extends BasePage with JsWrapper:
  def name = PageNameTyp("Console")
  val ConsoleId: HtmlId = genId(name)
  val ClickId: HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>${name}</h5></div>""")
    setData(gE(ClickId), "command", param.replaceAll("_", " "))
    gE(ClickId).click()
    true

  override def event(elem: HTMLElement, event: dom.Event) =   
    HtmlId(elem.id) match
      case `ConsoleId` => gE(ConsoleId).click()
      case _           => debug(s"event -> unknown event for elem:${elem.id} with event:${event.`type`}") 

   
