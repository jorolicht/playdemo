package pages

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.dom.Event
import org.scalajs.dom.raw.HTMLElement
import base.Logging.* 
import comps.{Sidebar, ContextHeader}
import shared.basic.AppError
import shared.PageNameTyp.PageName

// pagesMap maps page names to page objecpts   
val pagesMap = List(pages.Home, Auth, Console, PgError,
                    ChatExample, 
                    ViewOrganizer, MainMulti, TourneyNew, CompetitionNew, TourneyInfo, Mockup, CompetitionInfo, PlayerRegistration, UserRegistration, UserLogin, VerifyAccount, RoundAdmin, RoundDraw, RoundInput, RoundResult, PlayerList, ResultList, Certificate, MainSearch)
                    .map(pg => pg.name -> pg).toMap              


def loadPage(pageName: PageName, param: String, withSidebar: Boolean = true, async: Boolean = false): Unit =
  //debug(s"loadPage -> ${pagesMap.mkString(":")}")
  try
    debug(s"loadPage -> pageName:${pageName} param:${param}")
    ContextHeader.hide()
    
    if async then
      pagesMap(pageName).renderAsync(param).map { success =>
        if success then
          if withSidebar then Sidebar.setNavLink(pageName.value)
        else   
          error(s"loadPage -> page:${pageName} param:${param}")
      }
    else
      if pagesMap(pageName).render(param) then
        if withSidebar then Sidebar.setNavLink(pageName.value)
      else   
        error(s"loadPage -> page:${pageName} param:${param}")
  catch
    case e: Exception => error(s"loadPage -> page:${pageName} param:${param} not found")


abstract class BasePage extends comps.BaseComp:
  override def handleEvent(elem: HTMLElement, event: Event): Unit = {}
