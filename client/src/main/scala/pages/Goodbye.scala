package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import base.*

/**
 * Goodbye page rendered after user logout.
 */
object Goodbye extends BasePage with JsWrapper:
  def name = PageNameTyp("Goodbye")

  def render(param: String = ""): Boolean =
    setMain(cviews.pages.html.Goodbye())
    true
