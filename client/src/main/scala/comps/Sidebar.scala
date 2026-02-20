package comps

import org.scalajs.dom.raw.HTMLElement
import shared.MainIds.SidebarId


object Sidebar extends CompBase with base.JsWrapper:
  def name = PageNameTyp("Sidebar")
  val AsideId:         HtmlId = genId(name)
  val LoginInfoId:     HtmlId = genId(name)
  val LoggedInAsId:    HtmlId = genId(name)
  val UseCase3Id:      HtmlId = genId(name)  
  val UseCase4Id:      HtmlId = genId(name)
  val UseCase5Id:      HtmlId = genId(name)
  val UseCase51Id:     HtmlId = genId(name)

  def render(param: String = "") = 
    setHtml(gE(SidebarId), cviews.comps.html.sidebar())
    true

  def setNavLink(uc: String) =
    val navLinkNodes = gE(AsideId).querySelectorAll("[data-usecase]")
    for( i <- 0 to navLinkNodes.length-1)
      val elem = navLinkNodes.item(i).asInstanceOf[HTMLElement]
      changeClass(elem, uc==getData(elem, "usecase", "") , "bg-primary")
    println(s"set navlink: ${uc} ")