package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import shared.basic.Pickle.*
import shared.BoxButton
import shared.PageNameTyp
import base.{Global, Logging}
import base.Messages.gM

object DemoManager extends ComWrapper {

  case class DemoPayload(tourney: TourneyDB.TourneySyncRequest) derives ReadWriter

  def promptDemoMode(templateQuery: String, proceedAction: () => Unit, fallbackAction: () => Unit): Unit = {
    dialogs.DlgMsgbox.show(
      gM("demo.promptMessage"),
      gM("demo.promptTitle"),
      List(BoxButton.DemoMode, BoxButton.LocalMode, BoxButton.LoginMode, BoxButton.Cancel)
    ).map {
      case BoxButton.DemoMode =>
        loadDemoTemplate(templateQuery).map { success =>
          if (success) {
            pages.loadPage(PageNameTyp("TourneyInfo"), "")
          } else {
            dom.window.alert(gM("demo.templateNotFound", templateQuery))
            startEmptyDemo(proceedAction)
          }
        }
      case BoxButton.LocalMode =>
        startLocalMode(proceedAction)
      case BoxButton.LoginMode =>
        pages.loadPage(PageNameTyp("UserLogin"), "")
      case _ => Logging.debug("Prompt cancelled")
    }
  }

  private def startEmptyDemo(proceedAction: () => Unit): Unit = {
    Global.isDemoMode = true
    TourneyDB.tourney = shared.model.Tourney.default.copy(wpId = 999999, name = gM("demo.tourneyName"))
    TourneyDB.version = 1
    TourneyDB.tourney.clubs.clear()
    TourneyDB.tourney.players.clear()
    TourneyDB.tourney.competitions.clear()
    TourneyDB.tourney.stages.clear()
    proceedAction()
  }

  def startLocalMode(proceedAction: () => Unit): Unit = {
    Global.isLocalMode = true
    Global.isDemoMode = false
    TourneyDB.tourney = shared.model.Tourney.default.copy(wpId = 999999, name = "Lokales Turnier")
    TourneyDB.version = 1
    TourneyDB.tourney.clubs.clear()
    TourneyDB.tourney.players.clear()
    TourneyDB.tourney.competitions.clear()
    TourneyDB.tourney.stages.clear()
    
    // Force sync to local storage
    TourneyDB.sync()
    ClubDB.sync(Seq())
    PlayerDB.sync(Seq())
    CompetitionDB.sync(Seq())
    StageDB.sync(Seq())
    
    proceedAction()
  }

  /**
   * Starts a pre-populated demo tournament directly in local mode.
   * Loads the demo template, sets isLocalMode to true and isDemoMode to false,
   * syncs data to local storage, and invokes proceedAction.
   *
   * @param proceedAction Callback invoked after the demo tournament is loaded.
   * @return A Future containing true if the template was loaded, false otherwise.
   */
  def startDemoInLocalMode(proceedAction: () => Unit = () => ()): Future[Boolean] = {
    loadDemoTemplate("template-full").map { success =>
      Global.isLocalMode = true
      Global.isDemoMode = false
      if (success) {
        proceedAction()
        true
      } else {
        startLocalMode(() => {
          proceedAction()
        })
        false
      }
    }
  }

  def loadDemoTemplate(query: String): Future[Boolean] = {
    val filename = if (query.contains("single") || query.contains("competition")) {
      "demo_competition.json"
    } else {
      "demo_tourney.json"
    }

    val url = s"${Global.dataUrl}$filename"
    Logging.debug(s"Loading local demo data from $url...")

    val timestamp = new scala.scalajs.js.Date().getTime().toLong
    val cacheBustedUrl = if (url.contains("?")) s"$url&t=$timestamp" else s"$url?t=$timestamp"

    val fetchOptions = new dom.RequestInit {
      method = dom.HttpMethod.GET
      cache = dom.RequestCache.reload 
    }

    dom.fetch(cacheBustedUrl, fetchOptions).toFuture.flatMap { response =>
      if (response.ok) {
        response.text().toFuture.map { content =>
          try {
            val payload = read[DemoPayload](content)
            
            // Populate TourneyDB
            TourneyDB.tourney = payload.tourney.tourney
            TourneyDB.version = payload.tourney.version
            
            // Decouple from server
            TourneyDB.tourney.wpId = 999999
            val prefix = gM("demo.namePrefix")
            if (!TourneyDB.tourney.name.startsWith(prefix)) {
              TourneyDB.tourney.name = prefix + TourneyDB.tourney.name
            }
            TourneyDB.version = 1
            
            // Force sync to local storage
            TourneyDB.sync()
            ClubDB.sync(TourneyDB.tourney.clubs.toSeq)
            PlayerDB.sync(TourneyDB.tourney.players.toSeq)
            CompetitionDB.sync(TourneyDB.tourney.competitions.filter(_ != null).toSeq)
            StageDB.sync(TourneyDB.tourney.stages.filter(_ != null).toSeq)
            
            Logging.debug(s"Demo data '$filename' successfully loaded and decoupled.")
            true
          } catch {
            case ex: Exception =>
              Logging.error(s"Failed to parse demo payload: ${ex.getMessage}")
              false
          }
        }
      } else {
        Logging.error(s"Failed to fetch demo data: ${response.statusText}")
        Future.successful(false)
      }
    }.recover {
      case ex: Exception =>
        Logging.error(s"Error loading demo template: ${ex.getMessage}")
        false
    }
  }
}