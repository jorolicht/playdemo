package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import shared.basic.Pickle.*
import shared.BoxButton
import shared.PageNameTyp
import base.{Global, Logging}

object DemoManager extends ComWrapper {

  case class SearchResult(id: Int, name: String, organizer: String, startDate: Int, status: String, slug: String) derives ReadWriter

  def promptDemoMode(templateQuery: String, proceedAction: () => Unit, fallbackAction: () => Unit): Unit = {
    dialogs.DlgMsgbox.show(
      "Du bist nicht angemeldet. Um Turniere dauerhaft auf dem Server zu speichern, logge dich bitte ein. Möchtest du stattdessen ein Demo-Turnier ausprobieren? (Die Daten werden nur in deinem Browser gespeichert)",
      "Demo-Modus starten?",
      List(BoxButton.Yes, BoxButton.No, BoxButton.Cancel)
    ).map {
      case BoxButton.Yes =>
        loadDemoTemplate(templateQuery).map { success =>
          if (success) {
            pages.loadPage(PageNameTyp("TourneyInfo"), "")
          } else {
            dom.window.alert(s"Konnte kein Demo-Template ('$templateQuery') auf dem Server finden. Ein leeres Turnier wird gestartet.")
            startEmptyDemo(proceedAction)
          }
        }
      case BoxButton.No =>
        pages.loadPage(PageNameTyp("UserLogin"), "")
      case _ => Logging.debug("Demo prompt cancelled")
    }
  }

  private def startEmptyDemo(proceedAction: () => Unit): Unit = {
    Global.isDemoMode = true
    TourneyDB.tourney = shared.model.Tourney.default.copy(wpId = 999999, name = "Demo Turnier")
    TourneyDB.version = 1
    TourneyDB.tourney.clubs.clear()
    TourneyDB.tourney.players.clear()
    TourneyDB.tourney.competitions.clear()
    TourneyDB.tourney.stages.clear()
    proceedAction()
  }

  private def loadDemoTemplate(query: String): Future[Boolean] = {
    // Search for the template
    ajaxGet[Seq[SearchResult]](s"/wp-json/tourney/v1/search?q=$query", List(), host = Global.homeUrl).flatMap {
      case Right(results) if results.nonEmpty =>
        val templateId = results.head.id
        Logging.debug(s"Found demo template '$query' with ID $templateId")
        
        // Fetch the template with Demo Mode OFF
        Global.isDemoMode = false
        TourneyDB.load(templateId).map {
          case Right(_) =>
            // Template loaded successfully. Now switch to Demo Mode.
            Global.isDemoMode = true
            
            // Decouple from server
            TourneyDB.tourney.wpId = 999999
            TourneyDB.tourney.name = "Demo: " + TourneyDB.tourney.name
            TourneyDB.version = 1
            
            // Force sync to local storage
            TourneyDB.sync()
            ClubDB.sync(TourneyDB.tourney.clubs.toSeq)
            PlayerDB.sync(TourneyDB.tourney.players.toSeq)
            CompetitionDB.sync(TourneyDB.tourney.competitions.filter(_ != null).toSeq)
            StageDB.sync(TourneyDB.tourney.stages.filter(_ != null).toSeq)
            
            true
          case Left(err) =>
            Logging.error(s"Failed to load template $templateId: ${err.msgCode}")
            false
        }
      case Right(_) =>
        Logging.debug(s"No template found for query '$query'")
        Future.successful(false)
      case Left(err) =>
        Logging.error(s"Failed to search template: ${err.msgCode}")
        Future.successful(false)
    }
  }
}