package comps

import org.scalajs.dom.raw.HTMLElement
import shared.DomTypes.HtmlId
import base.JsWrapper
import base.JsWrapper


object Sidebar extends CompBase with JsWrapper:

  val ToggleSidebarId: HtmlId = HtmlId.fromName(name)
  val SidebarId:       HtmlId = HtmlId.fromName(name)
  val UseCase3Id:      HtmlId = HtmlId.fromName(name)  
  val UseCase4Id:      HtmlId = HtmlId.fromName(name)
  val UseCase5Id:      HtmlId = HtmlId.fromName(name)
  val UseCase51Id:     HtmlId = HtmlId.fromName(name)


  def render(param: String = "") = 
    setHtml(gE3(SidebarId), cviews.comps.html.sidebar())
    true

  def setNavLink(uc: String) =
    val navLinkNodes = gE3(SidebarId).querySelectorAll("[data-usecase]")
    for( i <- 0 to navLinkNodes.length-1)
      val elem = navLinkNodes.item(i).asInstanceOf[HTMLElement]
      changeClass(elem, uc==getData(elem, "usecase", "") , "bg-primary")
    println(s"set navlink: ${uc} ")