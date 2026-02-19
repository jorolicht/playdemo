package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import scala.scalajs.js

import shared.model._
import shared.DomTypes.HtmlId
import shared.PageNameTyp
import base.* 


object PgError extends BasePage with JsWrapper:
  def name = PageNameTyp("PgError")

  def render(param: String = ""): Boolean = 
    val err = parseError(atou(param), name.value)
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>Error: ${err.toString}</h5></div>""")

  def render(err: AppError): Boolean = 
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>Error: ${err.toString}</h5></div>""")


