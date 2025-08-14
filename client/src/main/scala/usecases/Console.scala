package usecases

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js
import cviews.usecases._
import shared.Ids._
import base._

object Console extends UseCase with JsWrapper:
  
  def render(param: String = ""): Boolean = 
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>${name}</h5></div>""")
    setData(gE(Console_click), "command", param.replaceAll("_", " "))
    gE(Console_click).click()
    true

  override def event(elem: HTMLElement, event: dom.Event) =   
    elem.id match
      case Console_show => gE(Console_click).click()

   
