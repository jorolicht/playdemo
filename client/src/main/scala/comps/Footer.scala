package comps

import shared.MainIds.FooterId

object Footer extends BaseComp with base.JsWrapper:
  def name = PageNameTyp("Footer")
  val ConsoleClickId: HtmlId = genId(name)

  def render(param: String = "") = 
    if (!base.Global.fws) {
      setHtml(gE(FooterId), play.twirl.api.Html(""))
    } else {
      setHtml(gE(FooterId), cviews.comps.html.footer())
    }
    true
