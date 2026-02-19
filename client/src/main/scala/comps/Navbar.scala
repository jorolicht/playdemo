package comps

import base.JsWrapper
import shared.DomTypes.HtmlId
import shared.DomTypes.genId
import shared.MainIds.NavbarId
import shared.PageNameTyp


object Navbar extends CompBase with JsWrapper:
  def name = PageNameTyp("Navbar")
  val ToggleSidebarId: HtmlId = genId(name)
  val ConsoleClickId: HtmlId = genId(name)
  val ShowLoginId: HtmlId = genId(name)
  val DoLogoutId: HtmlId = genId(name)

  def render(param: String = "") = 
    setHtml(gE(NavbarId), cviews.comps.html.navbar()) 
    true
