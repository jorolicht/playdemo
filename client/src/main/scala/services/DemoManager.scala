package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import shared.basic.Pickle.*
import shared.BoxButton
import shared.PageNameTyp
import base.{Global, Logging}
import base.Messages.gM
import shared.basic.AppError

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

  /**
   * Loads the pre-populated demo tournament and saves it on the server: with Full Web Site as a
   * new cloud tournament, embedded (fws=false) into the current WordPress page.
   * Sets isLocalMode and isDemoMode to false and synchronizes all demo data
   * (basic, clubs, players, competitions, stages) to the server.
   *
   * @param overwrite True to overwrite an existing cloud demo tournament with the same slug.
   * @return A Future with Right(()) when saved, or Left(AppError), e.g. 'tourney_already_exists'.
   */
  def startDemoOnServer(overwrite: Boolean = false): Future[Either[AppError, Unit]] = {
    val filename = "demo_tourney.json"
    val url = s"${Global.dataUrl}$filename"
    val timestamp = new scala.scalajs.js.Date().getTime().toLong
    val cacheBustedUrl = if (url.contains("?")) s"$url&t=$timestamp" else s"$url?t=$timestamp"

    val fetchOptions = new dom.RequestInit {
      method = dom.HttpMethod.GET
      cache = dom.RequestCache.reload
    }

    Global.isLocalMode = false
    Global.isDemoMode = false

    dom.fetch(cacheBustedUrl, fetchOptions).toFuture.flatMap { response =>
      if (response.ok) {
        response.text().toFuture.flatMap { content =>
          try {
            val payload = read[DemoPayload](content)
            refreshRunningMatches(payload.tourney.tourney)
            val prefix = gM("demo.namePrefix")
            if (!payload.tourney.tourney.name.startsWith(prefix)) {
              payload.tourney.tourney.name = prefix + payload.tourney.tourney.name
            }
            AdminManager.importTourney(write(payload), overwrite).map {
              case Right(slug) =>
                Global.isLocalMode = false
                Global.isDemoMode = false
                Global.tourneySlug = slug
                Global.pageId = TourneyDB.tourney.wpId
                Global.currentSelection = shared.model.Selection(Some(TourneyDB.tourney))
                Right(())
              case Left(err) =>
                Logging.error(s"Failed to save demo tournament on server: ${err.msgCode}")
                Left(err)
            }
          } catch {
            case ex: Exception =>
              Logging.error(s"Failed to parse demo payload: ${ex.getMessage}")
              Future.successful(Left(AppError("demo.load.failed", ex.getMessage)))
          }
        }
      } else {
        Logging.error(s"Failed to fetch demo data: ${response.statusText}")
        Future.successful(Left(AppError("demo.load.failed", response.statusText)))
      }
    }.recover {
      case ex: Exception =>
        Logging.error(s"Error starting demo on server: ${ex.getMessage}")
        Left(AppError("demo.load.failed", ex.getMessage))
    }
  }

  /**
   * Setzt die Startzeit laufender Spiele (Status RUN) auf „vor einigen Minuten“. Die Demo-Daten wurden zu einem festen
   * Zeitpunkt aufgezeichnet; ohne Anpassung zeigte die Ergebniseingabe sonst die gesamte Zeit seitdem als Spielzeit
   * (z. B. „55030:50“).
   *
   * @param t Demo-Turnier
   * @param minutesAgo wie lange die Spiele schon laufen
   */
  def refreshRunningMatches(t: shared.model.Tourney, minutesAgo: Int = 4): Unit =
    val d = new scala.scalajs.js.Date(new scala.scalajs.js.Date().getTime() - minutesAgo * 60000.0)
    val ts = f"${d.getFullYear().toInt}%04d${d.getMonth().toInt + 1}%02d${d.getDate().toInt}%02d" +
             f"${d.getHours().toInt}%02d${d.getMinutes().toInt}%02d${d.getSeconds().toInt}%02d"
    for
      stage <- t.stages if stage != null
      m     <- stage.matches if m.status == shared.model.MEntry.MS_RUN
    do m.startTime = ts

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
            refreshRunningMatches(TourneyDB.tourney)
            
            // Decouple from server
            TourneyDB.tourney.wpId = 999999
            val prefix = gM("demo.namePrefix")
            if (!TourneyDB.tourney.name.startsWith(prefix)) {
              TourneyDB.tourney.name = prefix + TourneyDB.tourney.name
            }
            TourneyDB.version = 1
            
            while (TourneyDB.tourney.stages.length < 128) TourneyDB.tourney.stages += null
            while (TourneyDB.tourney.competitions.length < 64) TourneyDB.tourney.competitions += null

            ClubDB.initHandler()
            PlayerDB.initHandler()
            CompetitionDB.initHandler()
            StageDB.initHandler()

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