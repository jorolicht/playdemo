package usecases

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js
import cviews.usecases._
import base._
import shared.Ids._

object Home extends UseCase with JsWrapper:
  def render(param: String = ""): Boolean =
    param.toLowerCase match 
      case "goodbye"  => setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>GOODBYE</h5></div>""")
      case "welcome"  => setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>WELCOME</h5></div>""")
      case "start"    => setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>START</h5></div>""")
      case "verified" => setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>VERIFIED</h5></div>""")
      case _          => setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>${param}</h5></div>""")


  override def event(elem: HTMLElement, event: dom.Event) =   
    elem.id match
      case Home_toggleSidebar => toggleClass(gE(Home_Sidebar), "d-none")

      case _ => debug(s"event -> unknown event for elem:${elem.id} with event:${event.`type`}")
