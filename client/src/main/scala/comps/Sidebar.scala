package comps

import org.scalajs.dom.raw.HTMLElement
import shared.MainIds.SidebarId
import comps.Navbar.DoLogoutId
import comps.Navbar.ShowLoginId
import base.Global


object Sidebar extends BaseComp with base.JsWrapper:
  def name = PageNameTyp("Sidebar")
  val AsideId:         HtmlId = genId(name)
  val LoginInfoId:     HtmlId = genId(name)
  val LoggedInAsId:    HtmlId = genId(name)

  def updateUserInfo = 
    val validUser = Global.user != None
    // changeClass(gE(ShowLoginId), validUser, "disabled")
    // changeClass(gE(DoLogoutId), !validUser, "disabled")
    changeClass(gE(LoginInfoId), !validUser, "d-none")
    setHtml(gE(LoggedInAsId), Global.user.map(u => u.name).getOrElse(""))

  def render(param: String = "") = 
    setHtml(gE(SidebarId), cviews.comps.html.sidebar())
    true

  def setNavLink(uc: String) =
    if (idExists(AsideId)) then
      val navLinkNodes = gE(AsideId).querySelectorAll("[data-usecase]")
      for( i <- 0 to navLinkNodes.length-1)
        val elem = navLinkNodes.item(i).asInstanceOf[HTMLElement]
        changeClass(elem, uc==getData(elem, "usecase", "") , "bg-primary")
      println(s"set navlink: ${uc} ")