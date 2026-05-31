package pages

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.dom.Event
import org.scalajs.dom.raw.HTMLElement
import base.Logging.* 
import comps.Sidebar
import shared.basic.AppError
import shared.PageNameTyp.PageName
import javax.swing.text.View

// pagesMap maps page names to page objecpts   
val pagesMap = List(pages.Home, Auth, Console, PgError,
                    ChatExample, 
                    UseCase2, UseCase31, UseCase32, UseCase41, UseCase42,
                    UseCase511, UseCase512, UseCase53, ViewOrganizer, MainMulti, TourneyNew, TourneyInfo, Mockup, CompetitionInfo, RoundAdmin, RoundDraw, RoundInput, RoundResult, PlayerList, ResultList, Certificate, MainSearch)
                    .map(pg => pg.name -> pg).toMap              


def loadPage(pageName: PageName, param: String, withSidebar: Boolean = true, async: Boolean = false): Unit =
  //debug(s"loadPage -> ${pagesMap.mkString(":")}")
  try
    debug(s"loadPage -> pageName:${pageName} param:${param}")
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
