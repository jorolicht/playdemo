package pages

import shared.PageNameTyp
import base.JsWrapper

object UseCase32 extends BasePage with JsWrapper:
  def name = PageNameTyp("UseCase32")
  
  def render(param: String = ""): Boolean = 
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>${name}</h5></div>""")




