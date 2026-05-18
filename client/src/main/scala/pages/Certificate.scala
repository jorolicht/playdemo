package pages

import org.scalajs.dom
import base.*
import shared.model.*

object Certificate extends BasePage with JsWrapper:
  def name = PageNameTyp("Certificate")

  def render(param: String = ""): Boolean = 
    setMain(cviews.pages.html.Certificate(param))
    true
