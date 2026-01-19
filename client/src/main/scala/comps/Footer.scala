package comps

import shared.DomTypes.HtmlId
import shared.DomTypes.genId
import shared.MainIds.NavbarId
import base.JsWrapper


object Footer extends CompBase with JsWrapper:

  val ConsoleClickId: HtmlId = genId(name)

  def render(param: String = "") = 
    setHtml(gE(NavbarId), cviews.comps.html.navbar()) 
    true
