package comps

import shared.MainIds.*

object Wordpress extends CompBase with base.JsWrapper:
  def name = PageNameTyp("Wordpress")
  def render(param: String = ""): Boolean = 
    setHtml(gE(WordpressId), cviews.comps.html.wordpress())
    true

