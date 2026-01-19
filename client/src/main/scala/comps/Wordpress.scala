package comps

import base.JsWrapper
import shared.MainIds.*


object Wordpress extends CompBase with JsWrapper:

  def render(param: String = ""): Boolean = 
    setHtml(gE(WordpressId), cviews.comps.html.wordpress())
    true

