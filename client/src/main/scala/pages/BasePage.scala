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
                    ChatExample, Goodbye, RegistrationSuccess,
                    ViewOrganizer, MainView, TourneyNew, CompetitionNew, TourneyInfo, TourneyWelcome, TourneyAdmin, Mockup, CompetitionInfo, CompetitionWelcome,
                    PlayerRegistration, UserRegistration, UserLogin, VerifyAccount, StageAdmin, StageDraw, StageInput, StageResult, StageScoreSheet, StageCertificate, 
                    PlayerList, ResultList, Certificate, MainSearch, Management)
                    .map(pg => pg.name -> pg).toMap              


def loadPage(pageName: PageName, param: String, withSidebar: Boolean = true, async: Boolean = false): Unit =
  try
    var targetPage = pageName
    var targetParam = param

    if (pageName.value == "StageAdmin" && param.nonEmpty) {
      try {
        val rId = shared.model.StageId(param.toInt)
        services.TourneyDB.tourney.stages.find(s => s != null && s.id == rId).foreach { r =>
          base.Global.currentSelection = base.Global.currentSelection.copy(stage = Some(r))
          r.status match {
            case shared.model.StageStatus.CFG =>
              targetPage = shared.PageNameTyp("StageAdmin")
              targetParam = ""
            case shared.model.StageStatus.AUS =>
              targetPage = shared.PageNameTyp("StageDraw")
              targetParam = ""
            case shared.model.StageStatus.EIN =>
              targetPage = shared.PageNameTyp("StageInput")
              targetParam = ""
            case shared.model.StageStatus.FIN =>
              targetPage = shared.PageNameTyp("StageResult")
              targetParam = ""
            case _ =>
          }
        }
      } catch {
        case e: Exception => // ignore parsing exceptions
      }
    }

    val isEditingCurrentTourney = targetPage.value == "TourneyNew" && targetParam == "edit"
    val isLeavingTourney = base.Global.isTourneyPage(base.Global.activePageName) &&
                           !base.Global.isTourneyPage(targetPage.value) &&
                           base.Global.currentSelection.tourney.isDefined &&
                           !isEditingCurrentTourney &&
                           targetPage.value != base.Global.activePageName
    if (isLeavingTourney) {
      val tourney = base.Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)
      dialogs.DlgExportTourney.show(tourney, saveAndLeave = true).map {
        case Some(_) =>
          services.TourneyDB.sync().map { _ =>
            base.Global.currentSelection = shared.model.Selection()
            base.Global.clearSessionTourneyState()
            doLoadPageInternal(targetPage, targetParam, withSidebar, async)
          }
        case None => // User cancelled, stay on current page
      }
    } else {
      doLoadPageInternal(targetPage, targetParam, withSidebar, async)
    }
  catch
    case e: Exception => error(s"loadPage -> page:${pageName} param:${param} not found: ${e.getMessage}")

private def doLoadPageInternal(targetPage: PageName, targetParam: String, withSidebar: Boolean, async: Boolean): Unit =
  debug(s"loadPage -> pageName:${targetPage} param:${targetParam}")
  base.Global.activePageName = targetPage.value
  ContextHeader.hide()
  
  if async then
    pagesMap(targetPage).renderAsync(targetParam).map { success =>
      if success then
        if withSidebar then Sidebar.setNavLink(targetPage.value)
        savePageState(targetPage, targetParam)
        comps.Navbar.render()
      else   
        error(s"loadPage -> page:${targetPage} param:${targetParam}")
    }
  else
    if pagesMap(targetPage).render(targetParam) then
      if withSidebar then Sidebar.setNavLink(targetPage.value)
      savePageState(targetPage, targetParam)
      comps.Navbar.render()
    else   
      error(s"loadPage -> page:${targetPage} param:${targetParam}")

private def savePageState(pageName: PageName, param: String): Unit =
  if (pageName.value == "Goodbye") return
  try
    val storage = org.scalajs.dom.window.sessionStorage
    storage.setItem("tourney_last_page", pageName.value)
    storage.setItem("tourney_last_param", param)
    storage.setItem("tourney_last_wp_page_id", base.Global.hostPageId.toString)
    val selectionJson = shared.basic.Pickle.write(base.Global.currentSelection)
    storage.setItem("tourney_last_selection", selectionJson)
  catch
    case e: Exception => error(s"Failed to save page state to sessionStorage: ${e.getMessage}")


abstract class BasePage extends comps.BaseComp:
  override def handleEvent(elem: HTMLElement, event: Event): Unit = {}
