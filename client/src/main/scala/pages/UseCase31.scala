package pages

import shared.DomTypes.HtmlId
import shared.PageNameTyp
import base.JsWrapper


object UseCase31 extends BasePage with JsWrapper:
  def name = PageNameTyp("UseCase31")
  
  def render(param: String = ""): Boolean = 
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>${name}</h5></div>""")

