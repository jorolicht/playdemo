package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import shared.MainIds.*
import shared.model.*
import base.*
import dialogs.*

object MainMulti extends BasePage with JsWrapper:
  def name = PageNameTyp("MainMulti") 

  val BtnOption1: HtmlId = genId(name)
  val BtnOption2: HtmlId = genId(name)
  val BtnSearch:  HtmlId = genId(name)
  
  def render(param: String = ""): Boolean = 
    setMain(cviews.pages.html.MainMulti())
    true

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnOption1` => loadPage(TourneyNew.name, "")
      case `BtnOption2` => 
        comps.Navbar.doQuickStart()
      case `BtnSearch` => loadPage(MainSearch.name, "")
      case _ => debug(s"MainMulti handleEvent: ${elem.id}")
