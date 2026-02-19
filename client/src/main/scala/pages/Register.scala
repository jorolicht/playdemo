package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js
import cviews.pages.*
import base.*
import shared.*

object Register extends BasePage with JsWrapper:
  def name = PageNameTyp("Register")
  
  val NameId: HtmlId = genId(name)
  val EmailId: HtmlId = genId(name)
  val PasswordId: HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>${name}</h5></div>""")
    true

  override def event(elem: HTMLElement, event: dom.Event) =   
    HtmlId(elem.id) match
      case _           => debug(s"event -> unknown event for elem:${elem.id} with event:${event.`type`}") 

   
