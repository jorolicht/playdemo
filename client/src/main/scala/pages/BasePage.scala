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
                    ViewOrganizer, MainView, TourneyNew, CompetitionNew, TourneyInfo, TourneyAdmin, Mockup, CompetitionInfo, PlayerRegistration, UserRegistration, UserLogin, VerifyAccount, StageAdmin, StageDraw, StageInput, StageResult, StageScoreSheet, StageCertificate, PlayerList, ResultList, Certificate, MainSearch)
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

    debug(s"loadPage -> pageName:${targetPage} param:${targetParam}")
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
  catch
    case e: Exception => error(s"loadPage -> page:${pageName} param:${param} not found: ${e.getMessage}")

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

  def triggerTurnstile(): Unit =
    import scala.scalajs.js
    try {
      // 1. Programmatisch den Turnstile-Script-Tag injizieren, da innerHTML Skripte nicht ausführt
      val existingScript = org.scalajs.dom.document.getElementById("cloudflare-turnstile-script")
      if (existingScript == null) {
        val script = org.scalajs.dom.document.createElement("script").asInstanceOf[org.scalajs.dom.html.Script]
        script.id = "cloudflare-turnstile-script"
        script.src = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"
        script.async = true
        script.defer = true
        org.scalajs.dom.document.body.appendChild(script)
      }
      
      // 2. Explizites Rendern nach kurzem Timeout (damit das Script laden und das DOM stehen kann)
      org.scalajs.dom.window.setTimeout(() => {
        try {
          val ts = js.Dynamic.global.turnstile
          if (!js.isUndefined(ts) && ts != null) {
            val elements = org.scalajs.dom.document.getElementsByClassName("cf-turnstile")
            for (i <- 0 until elements.length) {
              val el = elements(i).asInstanceOf[org.scalajs.dom.html.Div]
              if (el.childNodes.length == 0) { // Nur rendern, falls noch nicht gerendert
                val sitekey = el.getAttribute("data-sitekey")
                if (sitekey != null && sitekey.nonEmpty) {
                  ts.render(el, js.Dynamic.literal(
                    sitekey = sitekey
                  ))
                }
              }
            }
          }
        } catch {
          case e: Throwable => error(s"Turnstile explicit render error: ${e.getMessage}")
        }
      }, 300)
    } catch {
      case e: Throwable => error(s"Turnstile script injection error: ${e.getMessage}")
    }
