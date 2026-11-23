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
  /** Eintrag der Liste "Meine Turniere", Suffix = Index in [[services.RecentTourneys.list]]. */
  val RecentId:           HtmlId = genId(name)
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
      case id if id.id.startsWith(RecentId.id + "-") =>
        services.RecentTourneys.list.lift(elem.id.substring(RecentId.id.length + 1).toInt).foreach(openRecent)
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
   * Wechselt zu einem anderen Turnier ohne Rückfrage: ein geöffnetes Turnier wird synchronisiert und geschlossen,
   * danach folgt `action` (z. B. Laden eines Cloud-Turniers bzw. des lokalen Turniers).
   *
   * @param action Laden des neuen Turniers
   */
  private def switchTourney(action: () => Unit): Unit =
    import shared.model.Selection
    val closed =
      if (base.Global.currentSelection.tourney.isDefined) services.TourneyDB.sync().map(_ => ())
      else Future.successful(())
    closed.foreach { _ =>
      base.Global.currentSelection = Selection()
      base.Global.clearSessionTourneyState()
      comps.ContextHeader.hide()
      action()
    }

  /**
   * Lädt ein Turnier, das das lokal im Browser gespeicherte Turnier ersetzt (Datei öffnen bzw. Datei aus "Meine
   * Turniere"). Gibt es ein lokales Turnier, fragt der Dialog "Turnier laden", ob es vorher in eine Datei exportiert
   * werden soll (Abbrechen / Ja, vorher speichern / Nein, überschreiben).
   *
   * @param action Laden des neuen Turniers
   */
  def confirmOverwriteLocal(action: () => Unit): Unit =
    import shared.BoxButton
    services.RecentTourneys.localTourneyName match
      case None => switchTourney(action)
      case Some(localName) =>
        dialogs.DlgMsgbox.show(
          gM("navbar.loadOverwritePrompt", localName),
          gM("navbar.loadOverwriteTitle"),
          List(BoxButton.Cancel, BoxButton.Yes, BoxButton.No),
          labels = Map(
            BoxButton.Cancel -> gM("navbar.loadOverwriteCancel"),
            BoxButton.Yes    -> gM("navbar.loadOverwriteSave"),
            BoxButton.No     -> gM("navbar.loadOverwriteReplace"))
        ).map {
          case BoxButton.Yes => exportLocalTourney(() => switchTourney(action))
          case BoxButton.No  => switchTourney(action)
          case _             => debug("Load tourney cancelled")
        }

  /**
   * Exportiert das lokale Turnier in eine Datei (Dialog mit Dateiname) und fährt danach mit `next` fort; bricht der
   * Benutzer den Export ab, wird nichts geladen. Ist das lokale Turnier nicht geöffnet, wird es zuvor aus dem Browser
   * gelesen (ein geöffnetes Cloud-Turnier wird dazu synchronisiert und geschlossen).
   *
   * @param next Fortsetzung nach dem Export
   */
  private def exportLocalTourney(next: () => Unit): Unit =
    import base.Global
    import services.TourneyDB
    def doExport(t: shared.model.Tourney): Unit =
      dialogs.DlgExportTourney.show(t, saveAndLeave = false).map {
        case Some(fileName) if fileName.nonEmpty =>
          TourneyDB.triggerExportDownload(fileName, t)
          next()
        case _ => debug("Export of local tourney cancelled, nothing loaded")
      }
    if (Global.isLocalMode && Global.currentSelection.tourney.isDefined) doExport(Global.currentSelection.tourney.get)
    else switchTourney { () =>
      Global.isLocalMode = true
      Global.isDemoMode = false
      TourneyDB.init(999999).map {
        case Right(_)  => doExport(TourneyDB.tourney)
        case Left(err) => dialogs.DlgMsgbox.showError(err, "Fehler beim Laden des lokalen Turniers")
      }
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
   * Turnier, das über "Meine Turniere → Turnier in Datei exportieren" gespeichert werden kann.
   *
   * @param tourney Turnier
   * @param version Version, die mit dem Turnier gespeichert wird
   * @param local   true = lokal im Browser gespeichertes Turnier, false = geöffnetes Cloud-Turnier
   */
  case class ExportCandidate(tourney: shared.model.Tourney, version: Int, local: Boolean)

  /**
   * Turniere, die in eine Datei exportiert werden können: das lokale Turnier (geöffnet oder im Browser gespeichert)
   * und das geöffnete Cloud-Turnier, sofern der Benutzer es bearbeiten darf.
   *
   * @return 0–2 Kandidaten, lokales Turnier zuerst
   */
  def exportCandidates: List[ExportCandidate] =
    import base.Global
    import services.TourneyDB
    val opened  = Global.currentSelection.tourney
    val inLocal = Global.isLocalMode || Global.isDemoMode
    val local =
      if (inLocal) opened.map(t => ExportCandidate(t, TourneyDB.version, true))
      else
        Option(dom.window.localStorage.getItem("App.demo_tourney")).filter(_.trim.nonEmpty).flatMap { raw =>
          try
            val req = shared.basic.Pickle.read[TourneyDB.TourneySyncRequest](raw)
            Option(req.tourney).filter(_.name.nonEmpty).map(t => ExportCandidate(t, req.version, true))
          catch case _: Throwable => None
        }
    val cloud =
      if (inLocal) None
      else opened.filter(t => t.wpId > 0 && t.wpId != 999999 && Global.hasTourneyAccess(t)).map(t => ExportCandidate(t, TourneyDB.version, false))
    local.toList ++ cloud.toList

  /**
   * "Meine Turniere → Turnier in Datei exportieren": Gibt es ein lokales und ein Cloud-Turnier, wird zuerst gefragt,
   * welches gespeichert werden soll; danach Dialog mit Dateiname und Download.
   */
  private def doLocalExport(): Unit = {
    import shared.BoxButton

    def exportTo(c: ExportCandidate): Unit =
      dialogs.DlgExportTourney.show(c.tourney, saveAndLeave = false).map {
        case Some(filename) if filename.nonEmpty =>
          services.TourneyDB.triggerExportDownload(filename, c.tourney, c.version)
          render()   // neue Datei in "Meine Turniere"
        case _ =>
          debug("Export cancelled")
      }

    exportCandidates match
      case Nil         => debug("Export: no tournament available")
      case c :: Nil    => exportTo(c)
      case candidates  =>
        val local = candidates.find(_.local).get
        val cloud = candidates.find(!_.local).get
        dialogs.DlgMsgbox.show(
          gM("navbar.exportChoosePrompt"),
          gM("navbar.exportChooseTitle"),
          List(BoxButton.Cancel, BoxButton.Yes, BoxButton.No),
          labels = Map(
            BoxButton.Cancel -> gM("navbar.loadOverwriteCancel"),
            BoxButton.Yes    -> gM("navbar.exportChooseLocal", local.tourney.name),
            BoxButton.No     -> gM("navbar.exportChooseCloud", cloud.tourney.name))
        ).map {
          case BoxButton.Yes => exportTo(local)
          case BoxButton.No  => exportTo(cloud)
          case _             => debug("Export cancelled")
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
          reader.onload = (e: dom.Event) => importTourneyJson(reader.result.asInstanceOf[String], file.name)
          reader.readAsText(file)
        }
      }
      fileInput.click()
    }
  }

  /**
   * Lädt ein Turnier aus dem Inhalt einer JSON-Datei als lokales Turnier in den Browser (nach Rückfrage zum Verlassen
   * des aktuellen Turniers) und vermerkt die Datei in "Meine Turniere".
   *
   * @param jsonString Inhalt der Datei (Format des Exports bzw. reines Turnier)
   * @param fileName   Dateiname
   */
  private def importTourneyJson(jsonString: String, fileName: String): Unit = {
    import services.*
    import base.Global
    import services.DemoManager.DemoPayload
    import shared.basic.Pickle.*
    import shared.model.Tourney

    try {
      val tourneyData = try {
        read[DemoPayload](jsonString).tourney.tourney
      } catch {
        case _: Exception => read[Tourney](jsonString)
      }

      confirmOverwriteLocal { () =>
        // Set Local Mode
        Global.isLocalMode = true
        Global.isDemoMode = false

        // Restore Tourney
        TourneyDB.tourney = tourneyData
        TourneyDB.tourney.wpId = 999999
        TourneyDB.version = 1
        TourneyDB.addExportedFilename(fileName)
        RecentTourneys.touchFile(fileName, tourneyData.name, jsonString)

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

  /**
   * Öffnet einen Eintrag aus "Meine Turniere": das lokale Turnier, die Kopie einer Datei (fehlt sie, folgt die
   * Dateiauswahl) bzw. ein Cloud-Turnier.
   *
   * @param entry Eintrag
   */
  private def openRecent(entry: services.RecentTourney): Unit =
    import services.RecentTourneys
    entry.kind match
      case RecentTourneys.Local => doLoadLocalTourney()
      case RecentTourneys.Cloud => switchTourney(() => loadCloudTourney(entry.key))
      case _ =>
        RecentTourneys.FileCache.get(entry.key).foreach {
          case Some(json) => importTourneyJson(json, entry.key)
          case None =>
            dom.window.alert(gM("navbar.recentFileMissing", entry.key))
            doLocalImport()
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
                    dialogs.DlgMsgbox.showError(err, "Fehler beim Cloud-Import")
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
        dialogs.DlgMsgbox.showError(err, gM("navbar.moveToCloudError"))
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
    import services.RecentTourneys
    // "Meine Turniere": lokales bzw. Cloud-Turnier vermerken (Demo nicht)
    if (!base.Global.isDemoMode) {
      if (base.Global.isLocalMode) RecentTourneys.touch(RecentTourneys.Local, "local", tourney.name)
      else if (tourney.slug.nonEmpty && tourney.slug != "local") RecentTourneys.touch(RecentTourneys.Cloud, tourney.slug, tourney.name)
    }
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
      switchTourney { () =>
        Global.isLocalMode = true
        Global.isDemoMode = false
        TourneyDB.init(999999).map {
          case Right(_) =>
            openTourneyView(TourneyDB.tourney)
          case Left(err) =>
            dialogs.DlgMsgbox.showError(err, "Fehler beim Laden des lokalen Turniers")
        }
      }
    }
  }

  /**
   * Lädt ein Cloud-Turnier und öffnet es; ohne Anmeldung bzw. Berechtigung erscheint ein Hinweis.
   *
   * @param slug Slug des Turniers
   */
  private def loadCloudTourney(slug: String): Unit = {
    import base.Global
    import services.*

    if (Global.user.isEmpty) {
      dom.window.alert(gM("navbar.reloadLoginRequired"))
      pages.loadPage(shared.PageNameTyp("UserLogin"), "")
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
            dialogs.DlgMsgbox.showError(err, "Fehler beim Laden des Turniers")
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
    // Dieselbe Erkennung wie auf der Startseite (Slug "local" oder gespeichertes lokales Turnier ohne Cloud-Slug),
    // sonst landet ein dort angebotenes lokales Turnier ohne gesetztes Flag im Cloud-Zweig (Anmeldung erforderlich).
    val lastIsLocal = pages.MainView.getLastTourneyInfo().exists(_.isLocal)
    val isLocal = sessionLocal || localLocal || Global.isLocalMode || lastIsLocal

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
            dialogs.DlgMsgbox.showError(err, "Fehler beim Laden des lokalen Turniers")
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
          loadCloudTourney(slug)
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
