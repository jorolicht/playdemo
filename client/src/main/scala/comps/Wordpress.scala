package comps

import base.JsWrapper
import shared.MainIds.*
import shared.PageNameTyp


object Wordpress extends CompBase with JsWrapper:
  def name = PageNameTyp("Wordpress")
  def render(param: String = ""): Boolean = 
    setHtml(gE(WordpressId), cviews.comps.html.wordpress())
    true

