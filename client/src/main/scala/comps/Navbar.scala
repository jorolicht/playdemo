package comps

import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.MainIds.NavbarId
import shared.DomTypes.*
import shared.PageNameTyp.*
import comps.Sidebar.AsideId

object Navbar extends BaseComp with base.JsWrapper with services.ComWrapper:
  def name = PageNameTyp("Navbar")
  val ToggleSidebarId:    HtmlId = genId(name)
  val ConsoleClickId:     HtmlId = genId(name)
  val ShowLoginId:        HtmlId = genId(name)
  val DoLogoutId:         HtmlId = genId(name)
  val ShowQuickCompId:    HtmlId = genId(name)
  val StartDemoId:        HtmlId = genId(name)
  val ExportLocalId:      HtmlId = genId(name)
  val ImportLocalId:      HtmlId = genId(name)
  val ImportCloudId:      HtmlId = genId(name)
  val LoadLocalTourneyId: HtmlId = genId(name)
  /** HTML-ID for dropdown menu item "Letztes Turnier laden" */
  val LoadLastTourneyId:  HtmlId = genId(name)
  val MoveToCloudId:      HtmlId = genId(name)
  val ReloadTourneyId:    HtmlId = genId(name)
  val ExitOfflineId:      HtmlId = genId(name)
  def render(param: String = "") = 
    org.scalajs.dom.window.asInstanceOf[scala.scalajs.js.Dynamic].navbarGoBackToCurrentTourney = () => goBackToCurrentTourney()
    if (!base.Global.fws) {
      setHtml(gE(NavbarId), play.twirl.api.Html(""))
      true
    } else {
      val comps = services.CompetitionDB.competitions.toSeq.filter(c => c != null && !c.deleted)
      val stages = base.Global.currentSelection.competition.map { c =>
        services.StageDB.stages.toSeq.filter(s => s != null && s.coId == c.id && !s.deleted)
      }.getOrElse(Seq.empty)
      setHtml(gE(NavbarId), cviews.comps.html.navbar(base.Global.currentSelection, comps, stages)) 
      true
    }

  override def handleEvent(elem: org.scalajs.dom.raw.HTMLElement, event: org.scalajs.dom.Event) =
    HtmlId(elem.id) match
      case `ToggleSidebarId`    => toggleClass(gE(AsideId), "d-none")
      case `DoLogoutId`         => doLogout()
      case `ShowQuickCompId`    => 
        if (base.Global.user.isDefined || base.Global.isDemoMode || base.Global.isLocalMode) doQuickStart()
        else services.DemoManager.promptDemoMode("template-single", () => doQuickStart(), () => ())
      case `StartDemoId`        => doStartDemo()
      case `ExportLocalId`      => doLocalExport()
      case `ImportLocalId`      => doLocalImport()
      case `ImportCloudId`      => doCloudImport()
      case `MoveToCloudId`      => doMoveToCloud()
      case `LoadLocalTourneyId` => doLoadLocalTourney()
      case `LoadLastTourneyId`  => doReloadTourney()
      case `ReloadTourneyId`    => doReloadTourney()
      case `ExitOfflineId`      => doExitOffline()
      case _                    => debug(s"event -> unknown event for elem:${elem.id} with event:${event.`type`}")

  private def confirmLeave(action: () => Unit): Unit =
    import shared.model.Selection
    
    if (base.Global.currentSelection.tourney.isDefined) {
      val tourney = base.Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)
      dialogs.DlgExportTourney.show(tourney, saveAndLeave = true).map {
        case Some(_) =>
          services.TourneyDB.sync().map { _ =>
            base.Global.currentSelection = Selection()
            base.Global.clearSessionTourneyState()
            comps.ContextHeader.hide()
            action()
          }
        case None =>
          debug("Leave cancelled")
      }
    } else {
      action()
    }


  /**
   * Starts a quick competition workflow by navigating to CompetitionNew.
   */
  def doQuickStart(): Unit =
    pages.loadPage(shared.PageNameTyp("CompetitionNew"), "")

  private def doLogout(): Unit =
    import services.*
    import base.Global

    ajaxPost[String, Map[String, String]]("/wp-json/tourney/v1/auth/logout", List(), "", host = Global.homeUrl).map { res =>
      res match {
        case Right(m) => m.get("nonce").foreach(n => Global.wpNonce = n)
        case _ => // ignore
      }
      Global.resetUser
      Global.currentSelection = shared.model.Selection()
      comps.ContextHeader.hide()
      comps.Navbar.render()
      pages.loadPage(shared.PageNameTyp("Goodbye"), "")
    }

  /**
   * Starts a demo tournament (cloud, local or in the current page, see StartManager).
   */
  def doStartDemo(): Unit =
    services.StartManager.start(services.StartKind.Demo)

  /**
   * Exports the current tournament data to a local JSON file.
   */
  private def doLocalExport(): Unit = {
    import services.TourneyDB
    import base.Global

    val tourney = Global.currentSelection.tourney.getOrElse(TourneyDB.tourney)
    if (tourney.wpId != 0 || tourney.name.nonEmpty) {
      dialogs.DlgExportTourney.show(tourney, saveAndLeave = false).map {
        case Some(filename) if filename.nonEmpty =>
          TourneyDB.triggerExportDownload(filename, tourney)
        case _ =>
          debug("Local export cancelled")
      }
    } else {
      dom.window.alert("Kein aktives Turnier zum Exportieren vorhanden.")
    }
  }

  /**
   * Prompts the user to select a JSON file to import locally into the browser storage.
   */
  private def doLocalImport(): Unit = {
    import services.*
    import base.Global
    import services.DemoManager.DemoPayload
    import shared.basic.Pickle.*
    import shared.model.Tourney
    import shared.model.Selection

    val fileInput = dom.document.getElementById("navbar-import-file-input").asInstanceOf[dom.html.Input]
    if (fileInput != null) {
      fileInput.value = ""
      fileInput.onchange = (e: dom.Event) => {
        if (fileInput.files.length > 0) {
          val file = fileInput.files(0)
          val reader = new dom.FileReader()
          reader.onload = (e: dom.Event) => {
            val jsonString = reader.result.asInstanceOf[String]
            try {
              val tourneyData = try {
                read[DemoPayload](jsonString).tourney.tourney
              } catch {
                case _: Exception => read[Tourney](jsonString)
              }
              
              confirmLeave { () =>
                // Set Local Mode
                Global.isLocalMode = true
                Global.isDemoMode = false
                
                // Restore Tourney
                TourneyDB.tourney = tourneyData
                TourneyDB.tourney.wpId = 999999
                TourneyDB.version = 1
                TourneyDB.addExportedFilename(file.name)
                
                // Force sync to local storage
                TourneyDB.sync()
                ClubDB.sync(TourneyDB.tourney.clubs.toSeq)
                PlayerDB.sync(TourneyDB.tourney.players.toSeq)
                CompetitionDB.sync(TourneyDB.tourney.competitions.filter(_ != null).toSeq)
                StageDB.sync(TourneyDB.tourney.stages.filter(_ != null).toSeq)
                
                dom.window.alert("Import erfolgreich! Das lokale Turnier wird nun geladen.")
                openTourneyView(TourneyDB.tourney)
              }
            } catch {
              case ex: Exception =>
                dom.window.alert(s"Fehler beim Importieren der Datei: ${ex.getMessage}")
            }
          }
          reader.readAsText(file)
        }
      }
      fileInput.click()
    }
  }

  /**
   * Prompts the user to select a JSON file to import to the server / cloud backend.
   * Requires an authenticated user session.
   */
  private def doCloudImport(): Unit = {
    import base.Global
    import services.*

    if (Global.user.isEmpty) {
      dom.window.alert("Für den Cloud-Import ist eine Anmeldung erforderlich. Bitte melden Sie sich an.")
      pages.loadPage(shared.PageNameTyp("UserLogin"), "")
    } else {
      val fileInput = dom.document.getElementById("navbar-import-cloud-input").asInstanceOf[dom.html.Input]
      if (fileInput != null) {
        fileInput.value = ""
        fileInput.onchange = (e: dom.Event) => {
          if (fileInput.files.length > 0) {
            val file = fileInput.files(0)
            val reader = new dom.FileReader()
            reader.onload = (e: dom.Event) => {
              val jsonString = reader.result.asInstanceOf[String]
              confirmLeave { () =>
                TourneyDB.tourney.wpId = 0
                Global.isLocalMode = false
                Global.isDemoMode = false
                AdminManager.importTourney(jsonString).map {
                  case Right(slug) =>
                    Global.isLocalMode = false
                    Global.isDemoMode = false
                    TourneyDB.addExportedFilename(file.name)
                    dom.window.alert("Cloud-Import erfolgreich! Das Turnier wurde erstellt und geladen.")
                    openTourneyView(TourneyDB.tourney)
                  case Left(err) =>
                    dom.window.alert(s"Fehler beim Cloud-Import: ${err.msgCode}")
                }
              }
            }
            reader.readAsText(file)
          }
        }
        fileInput.click()
      }
    }
  }

  /**
   * Moves the currently active local tournament to the cloud (WordPress server).
   * Verifies that the user is authenticated and the tournament is not already in the cloud.
   * Prompts user for confirmation, then creates the tournament via AdminManager.importTourney,
   * updates application state to cloud mode, clears local storage copy, and navigates to TourneyInfo.
   */
  private def doMoveToCloud(): Unit = {
    import base.Global
    import services.*
    import shared.model.Selection
    import shared.basic.Pickle.*
    import shared.model.Tourney

    if (Global.user.isEmpty) {
      dom.window.alert(gM("navbar.reloadLoginRequired"))
      pages.loadPage(shared.PageNameTyp("UserLogin"), "")
      return
    }

    if (!Global.isLocalMode && !Global.isDemoMode) {
      dom.window.alert("Das Turnier befindet sich bereits in der Cloud.")
      return
    }

    val currentTourney = Global.currentSelection.tourney.getOrElse(TourneyDB.tourney)
    if (currentTourney == null || currentTourney.name.trim.isEmpty) {
      dom.window.alert(gM("navbar.moveToCloudNoTourney"))
      return
    }

    val confirmed = dom.window.confirm(gM("navbar.moveToCloudConfirm"))
    if (!confirmed) return

    // Set organizer from logged-in user if not already set
    if (currentTourney.organizer.trim.isEmpty && Global.user.isDefined) {
      val u = Global.user.get
      currentTourney.organizer = if (u.org.nonEmpty) u.org else u.username
    }

    val jsonString = write(currentTourney)

    // Reset wpId and slug so local mode detection recognises the switch to cloud mode
    currentTourney.wpId = 0
    currentTourney.slug = ""
    TourneyDB.tourney.wpId = 0
    Global.currentSelection = Selection(Some(currentTourney))
    Global.isLocalMode = false
    Global.isDemoMode = false

    AdminManager.importTourney(jsonString).map {
      case Right(slug) =>
        Global.isLocalMode = false
        Global.isDemoMode = false
        Global.tourneySlug = slug
        dom.window.localStorage.removeItem("App.demo_tourney")
        if (Global.currentAci != null) {
          TourneyDB.saveAci(TourneyDB.tourney.wpId, Global.currentAci)
        }
        dom.window.alert(gM("navbar.moveToCloudSuccess"))
        openTourneyView(TourneyDB.tourney)

      case Left(err) =>
        // Revert local mode on failure
        currentTourney.wpId = 999999
        TourneyDB.tourney.wpId = 999999
        Global.currentSelection = Selection(Some(currentTourney))
        Global.isLocalMode = true
        comps.Navbar.render()
        dom.window.alert(s"${gM("navbar.moveToCloudError")}: ${err.msgCode}")
    }
  }

  /**
   * Navigates to the appropriate page for the given tournament.
   * If the tournament is a simple competition (ident == "SIMPLE"), the first competition is selected
   * and the page navigates to CompetitionInfo instead of TourneyInfo.
   *
   * @param tourney The tournament to open.
   */
  def openTourneyView(tourney: shared.model.Tourney): Unit = {
    import shared.model.Selection
    if (tourney.ident == "SIMPLE") {
      val comp = services.CompetitionDB.competitions.find(c => c != null && !c.deleted)
        .orElse(tourney.competitions.find(c => c != null && !c.deleted))
        .orElse(services.CompetitionDB.competitions.find(c => c != null))
        .orElse(tourney.competitions.find(c => c != null))
      base.Global.currentSelection = Selection(Some(tourney), comp)
      comps.ContextHeader.render()
      comps.Navbar.render()
      pages.loadPage(shared.PageNameTyp("CompetitionInfo"), "")
    } else {
      base.Global.currentSelection = Selection(Some(tourney))
      comps.ContextHeader.render()
      comps.Navbar.render()
      pages.loadPage(shared.PageNameTyp("TourneyInfo"), "")
    }
  }

  /**
   * Loads a previously saved tournament from browser local storage and opens it in local mode.
   */
  private def doLoadLocalTourney(): Unit = {
    import base.Global
    import services.*

    val jsStr = dom.window.localStorage.getItem("App.demo_tourney")
    if (jsStr == null || jsStr.trim.isEmpty) {
      dom.window.alert("Kein lokal gespeichertes Turnier vorhanden.")
    } else {
      confirmLeave { () =>
        Global.isLocalMode = true
        Global.isDemoMode = false
        TourneyDB.init(999999).map {
          case Right(_) =>
            openTourneyView(TourneyDB.tourney)
          case Left(err) =>
            dom.window.alert(s"Fehler beim Laden des lokalen Turniers: ${err.msgCode}")
        }
      }
    }
  }

  /**
   * Reloads the tournament based on the current storage mode:
   * - If tourney_is_local_mode == true: loads and displays the locally stored tournament in TourneyInfo or CompetitionInfo.
   * - If tourney_is_local_mode == false: loads the cloud tournament with slug tourney_slug.
   *   If user is not logged in or lacks permissions, displays an error message.
   */
  def doReloadTourney(): Unit = {
    import base.Global
    import services.*

    val sessionLocal = org.scalajs.dom.window.sessionStorage.getItem("tourney_is_local_mode") == "true"
    val localLocal = org.scalajs.dom.window.localStorage.getItem("tourney_is_local_mode") == "true"
    val isLocal = sessionLocal || localLocal || Global.isLocalMode

    if (isLocal) {
      val jsStr = dom.window.localStorage.getItem("App.demo_tourney")
      if (jsStr == null || jsStr.trim.isEmpty) {
        dom.window.alert("Kein lokal gespeichertes Turnier vorhanden.")
      } else {
        Global.isLocalMode = true
        Global.isDemoMode = false
        Global.tourneySlug = "local"
        TourneyDB.init(999999).map {
          case Right(_) =>
            openTourneyView(TourneyDB.tourney)
          case Left(err) =>
            dom.window.alert(s"Fehler beim Laden des lokalen Turniers: ${err.msgCode}")
        }
      }
    } else {
      if (Global.user.isEmpty) {
        dom.window.alert(gM("navbar.reloadLoginRequired"))
        pages.loadPage(shared.PageNameTyp("UserLogin"), "")
      } else {
        val s1 = org.scalajs.dom.window.sessionStorage.getItem("tourney_slug")
        val s2 = org.scalajs.dom.window.localStorage.getItem("tourney_slug")
        val slug = if (s1 != null && s1.nonEmpty && s1 != "local") s1
                   else if (s2 != null && s2.nonEmpty && s2 != "local") s2
                   else if (Global.currentSelection.tourney.exists(t => t.slug.nonEmpty && t.slug != "local")) Global.currentSelection.tourney.get.slug
                   else if (TourneyDB.tourney != null && TourneyDB.tourney.slug.nonEmpty && TourneyDB.tourney.slug != "local") TourneyDB.tourney.slug
                   else ""

        if (slug.isEmpty) {
          dom.window.alert(gM("navbar.reloadNoSlug"))
        } else {
          TourneyDB.init(slug).map {
            case Right(_) =>
              val t = TourneyDB.tourney
              if (!Global.hasTourneyAccess(t)) {
                dom.window.alert(gM("navbar.reloadNoPermission"))
              } else {
                Global.isLocalMode = false
                Global.isDemoMode = false
                Global.tourneySlug = t.slug
                openTourneyView(t)
              }
            case Left(err) =>
              if (err.is("forbidden") || err.is("unauthorized") || err.msgCode.toLowerCase.contains("permission")) {
                dom.window.alert(gM("navbar.reloadNoPermission"))
              } else {
                dom.window.alert(s"Fehler beim Laden des Turniers: ${err.msgCode}")
              }
          }
        }
      }
    }
  }

  private def doExitOffline(): Unit = {
    import shared.BoxButton
    import shared.model.Selection
    import base.Global
    
    if (Global.isLocalMode && (Global.currentSelection.tourney.isDefined || services.TourneyDB.tourney.wpId > 0 || services.TourneyDB.tourney.name.nonEmpty)) {
      val tourney = Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)
      dialogs.DlgExportTourney.show(tourney, saveAndLeave = true).map {
        case Some(_) =>
          services.TourneyDB.sync().map { _ =>
            Global.isDemoMode = false
            Global.isLocalMode = false
            Global.currentSelection = Selection()
            Global.clearSessionTourneyState()
            comps.ContextHeader.hide()
            comps.Navbar.render()
            pages.loadPage(pages.MainView.name, "")
          }
        case None =>
          debug("Exit offline cancelled")
      }
    } else {
      dialogs.DlgMsgbox.show(
        "Möchten Sie den lokalen Modus / Demo-Modus wirklich beenden? Alle ungesicherten lokalen Daten gehen verloren.",
        "Modus beenden",
        List(BoxButton.Yes, BoxButton.No)
      ).map {
        case BoxButton.Yes =>
          Global.isDemoMode = false
          Global.isLocalMode = false
          Global.currentSelection = Selection()
          Global.clearSessionTourneyState()
          comps.ContextHeader.hide()
          comps.Navbar.render() // re-render to remove badges/menu
          pages.loadPage(pages.MainView.name, "")
        case _ =>
      }
    }
  }


  def goBackToCurrentTourney(): Unit = {
    import base.Global
    import shared.PageNameTyp
    
    if (Global.currentSelection.tourney.isDefined) {
      if (Global.currentSelection.tourney.get.ident == "SIMPLE" || Global.currentSelection.competition.isDefined) {
        pages.loadPage(PageNameTyp("CompetitionInfo"), "")
      } else {
        pages.loadPage(PageNameTyp("TourneyInfo"), "")
      }
    }
  }
