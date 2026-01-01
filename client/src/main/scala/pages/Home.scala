package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js
import cviews.pages.*
import base._
import shared.DomTypes.HtmlId


object Home extends BasePage with JsWrapper:

  val ToggleSidebarId: HtmlId = HtmlId.fromName(name)
  val SidebarId: HtmlId = HtmlId.fromName(name)

  def render(param: String = ""): Boolean =
    param.toLowerCase match 
      case "goodbye"  => setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>GOODBYE</h5></div>""")
      case "welcome"  => setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>WELCOME</h5></div>""")
      case "start"    => setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>START</h5></div>""")
      case "verified" => setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>VERIFIED</h5></div>""")
      case _          => setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>${param}</h5></div>""")


  override def event(elem: HTMLElement, event: dom.Event) =   
    HtmlId(elem.id) match
      case `ToggleSidebarId` => toggleClass(gE3(SidebarId), "d-none")

      case _ => debug(s"event -> unknown event for elem:${elem.id} with event:${event.`type`}")
