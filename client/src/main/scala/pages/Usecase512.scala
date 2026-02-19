package pages

import base.JsWrapper
import shared.PageNameTyp

object UseCase512 extends BasePage with JsWrapper:
  def name = PageNameTyp("UseCase512")
  def render(param: String = ""): Boolean = 
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>${name}</h5></div>""")
