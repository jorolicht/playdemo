package comps

import shared.MainIds.NavbarId

object Footer extends CompBase with base.JsWrapper:
  def name = PageNameTyp("Footer")
  val ConsoleClickId: HtmlId = genId(name)

  def render(param: String = "") = 
    setHtml(gE(NavbarId), cviews.comps.html.navbar()) 
    true
