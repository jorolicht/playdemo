package pages

import shared.MainIds.*
import base.JsWrapper

object MainMulti extends BasePage with JsWrapper:
  def name = PageNameTyp("MainMulti") 
  
  def render(param: String = ""): Boolean = 
    setMain(cviews.pages.html.MainMulti())
    true
