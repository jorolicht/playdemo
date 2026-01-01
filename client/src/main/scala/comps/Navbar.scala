package comps

import shared.DomTypes.HtmlId
import base.JsWrapper


object Navbar extends CompBase with JsWrapper:

  val NavbarId:    HtmlId = HtmlId.fromName(name)
  val ShowLoginId: HtmlId = HtmlId.fromName(name)
  val DoLogoutId: HtmlId = HtmlId.fromName(name)
  val ConsoleClickId: HtmlId = HtmlId.fromName(name)

  def render(param: String = "") = 
    setHtml(gE3(NavbarId), cviews.comps.html.navbar()) 
    true


