package comps

import shared.MainIds.*

object Navbar extends BaseComp with base.JsWrapper:
  def name = PageNameTyp("Navbar")
  val ToggleSidebarId: HtmlId = genId(name)
  val ConsoleClickId: HtmlId = genId(name)
  val ShowLoginId: HtmlId = genId(name)
  val DoLogoutId: HtmlId = genId(name)

  def render(param: String = "") = 
    setHtml(gE(NavbarId), cviews.comps.html.navbar()) 
    true

  override def handleEvent(elem: org.scalajs.dom.raw.HTMLElement, event: org.scalajs.dom.Event) =   
    HtmlId(elem.id) match
      case `ToggleSidebarId` => toggleClass(gE(shared.MainIds.SidebarId), "d-none")
      case _                 => debug(s"event -> unknown event for elem:${elem.id} with event:${event.`type`}")
