package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import base.*
import services.ComWrapper

object RegistrationSuccess extends BasePage with JsWrapper with ComWrapper:
  def name = PageNameTyp("RegistrationSuccess")

  def render(param: String = ""): Boolean = 
    setMain(cviews.pages.html.RegistrationSuccess())
    true
