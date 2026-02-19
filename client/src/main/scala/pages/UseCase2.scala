package pages

import base.JsWrapper


object UseCase2 extends BasePage with JsWrapper:
  def name = shared.PageNameTyp("UseCase2")
  def render(param: String = ""): Boolean = 
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>${name}</h5></div>""")
    true



