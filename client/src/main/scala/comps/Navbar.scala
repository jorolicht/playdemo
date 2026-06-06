package comps

import shared.MainIds.NavbarId
import comps.Sidebar.AsideId

object Navbar extends BaseComp with base.JsWrapper with services.ComWrapper:
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
      case `ToggleSidebarId` => toggleClass(gE(AsideId), "d-none")
      case `DoLogoutId`      => doLogout()
      case _                 => debug(s"event -> unknown event for elem:${elem.id} with event:${event.`type`}")

  private def doLogout(): Unit =
    import services.*
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    import org.scalajs.dom
    import base.Global

    ajaxPost[String, String]("/wp-json/playdemo/v1/auth/logout", List(), "", host = Global.homeUrl).map { _ =>
      Global.resetUser
      dom.window.location.href = Global.homeUrl
    }
