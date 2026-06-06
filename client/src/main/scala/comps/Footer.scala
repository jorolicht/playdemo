package comps

import shared.MainIds.FooterId

object Footer extends BaseComp with base.JsWrapper:
  def name = PageNameTyp("Footer")
  val ConsoleClickId: HtmlId = genId(name)

  def render(param: String = "") = 
    setHtml(gE(FooterId), cviews.comps.html.footer()) 
    true
