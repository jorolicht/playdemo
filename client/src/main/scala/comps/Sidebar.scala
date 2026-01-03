package comps

import org.scalajs.dom.raw.HTMLElement
import shared.DomTypes.HtmlId
import shared.MainIds.SidebarId
import base.JsWrapper


object Sidebar extends CompBase with JsWrapper:

  val AsideId:         HtmlId = HtmlId.fromName(name)
  val LoginInfoId:     HtmlId = HtmlId.fromName(name)
  val LoggedInAsId:    HtmlId = HtmlId.fromName(name)
  val UseCase3Id:      HtmlId = HtmlId.fromName(name)  
  val UseCase4Id:      HtmlId = HtmlId.fromName(name)
  val UseCase5Id:      HtmlId = HtmlId.fromName(name)
  val UseCase51Id:     HtmlId = HtmlId.fromName(name)


  def render(param: String = "") = 
    setHtml(gE(SidebarId), cviews.comps.html.sidebar())
    true

  def setNavLink(uc: String) =
    val navLinkNodes = gE(AsideId).querySelectorAll("[data-usecase]")
    for( i <- 0 to navLinkNodes.length-1)
      val elem = navLinkNodes.item(i).asInstanceOf[HTMLElement]
      changeClass(elem, uc==getData(elem, "usecase", "") , "bg-primary")
    println(s"set navlink: ${uc} ")