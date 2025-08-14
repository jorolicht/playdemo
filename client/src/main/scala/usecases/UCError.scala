package usecases

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import scala.scalajs.js

import cviews.usecases._
import shared.model._
import base._

object UCError extends UseCase with JsWrapper:
  def render(param: String = ""): Boolean = 
    val err = parseError(atou(param), name)
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>Error: ${err.toString}</h5></div>""")

  def render(err: AppError): Boolean = 
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>Error: ${err.toString}</h5></div>""")


