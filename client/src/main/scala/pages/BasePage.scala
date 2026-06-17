package pages

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.dom.Event
import org.scalajs.dom.raw.HTMLElement
import base.Logging.* 
import comps.{Sidebar, ContextHeader}
import shared.basic.AppError
import shared.PageNameTyp.PageName
import pages.Stage.*

// pagesMap maps page names to page objecpts   
val pagesMap = List(Auth, Console, PgError,
                    ChatExample, 
                    ViewOrganizer, MainView, TourneyNew, CompetitionNew, TourneyInfo, Mockup, CompetitionInfo, PlayerRegistration, UserRegistration, UserLogin, VerifyAccount, StageAdmin, StageDraw, StageInput, StageResult, PlayerList, ResultList, Certificate, MainSearch)
                    .map(pg => pg.name -> pg).toMap              


def loadPage(pageName: PageName, param: String, withSidebar: Boolean = true, async: Boolean = false): Unit =
  try
    debug(s"loadPage -> pageName:${pageName} param:${param}")
    ContextHeader.hide()
    
    if async then
      pagesMap(pageName).renderAsync(param).map { success =>
        if success then
          if withSidebar then Sidebar.setNavLink(pageName.value)
          savePageState(pageName, param)
        else   
          error(s"loadPage -> page:${pageName} param:${param}")
      }
    else
      if pagesMap(pageName).render(param) then
        if withSidebar then Sidebar.setNavLink(pageName.value)
        savePageState(pageName, param)
      else   
        error(s"loadPage -> page:${pageName} param:${param}")
  catch
    case e: Exception => error(s"loadPage -> page:${pageName} param:${param} not found")

private def savePageState(pageName: PageName, param: String): Unit =
  try
    val storage = org.scalajs.dom.window.sessionStorage
    storage.setItem("playdemo_last_page", pageName.value)
    storage.setItem("playdemo_last_param", param)
    storage.setItem("playdemo_last_wp_page_id", base.Global.hostPageId.toString)
    val selectionJson = shared.basic.Pickle.write(base.Global.currentSelection)
    storage.setItem("playdemo_last_selection", selectionJson)
  catch
    case e: Exception => error(s"Failed to save page state to sessionStorage: ${e.getMessage}")


abstract class BasePage extends comps.BaseComp:
  override def handleEvent(elem: HTMLElement, event: Event): Unit = {}
