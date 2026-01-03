package comps

import shared.DomTypes.HtmlId
import shared.MainIds.*
import base.JsWrapper


object Footer extends CompBase with JsWrapper:

  def render(param: String = "") = 
    setHtml(gE(NavbarId), cviews.comps.html.navbar()) 
    true
