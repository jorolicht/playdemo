package pages

import shared.DomTypes.HtmlId
import base.JsWrapper


object UseCase2 extends BasePage with JsWrapper:
  
  def render(param: String = ""): Boolean = 
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>${name}</h5></div>""")
    true



