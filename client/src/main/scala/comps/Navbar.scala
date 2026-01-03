package comps

import base.JsWrapper
import shared.DomTypes.HtmlId
import shared.MainIds.NavbarId


object Navbar extends CompBase with JsWrapper:

  val ToggleSidebarId: HtmlId = HtmlId.fromName(name)
  val ConsoleClickId: HtmlId = HtmlId.fromName(name)
  val ShowLoginId: HtmlId = HtmlId.fromName(name)
  val DoLogoutId: HtmlId = HtmlId.fromName(name)

  def render(param: String = "") = 
    setHtml(gE(NavbarId), cviews.comps.html.navbar()) 
    true
