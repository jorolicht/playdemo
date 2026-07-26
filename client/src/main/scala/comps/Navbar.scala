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
  val ToggleSidebarId: HtmlId = genId(name)
  val ConsoleClickId: HtmlId = genId(name)
  val ShowLoginId: HtmlId = genId(name)
  val DoLogoutId: HtmlId = genId(name)
  val ShowQuickCompId: HtmlId = genId(name)
  val StartFullId: HtmlId = genId(name)
  val StartSimpleId: HtmlId = genId(name)
  val ExportLocalId: HtmlId = genId(name)
  val ImportLocalId: HtmlId = genId(name)
  val ExitOfflineId: HtmlId = genId(name)
  def render(param: String = "") = 
    org.scalajs.dom.window.asInstanceOf[scala.scalajs.js.Dynamic].navbarGoBackToCurrentTourney = () => goBackToCurrentTourney()
    setHtml(gE(NavbarId), cviews.comps.html.navbar()) 
    true

  override def handleEvent(elem: org.scalajs.dom.raw.HTMLElement, event: org.scalajs.dom.Event) =
    HtmlId(elem.id) match
      case `ToggleSidebarId` => toggleClass(gE(AsideId), "d-none")
      case `DoLogoutId`      => doLogout()
      case `ShowQuickCompId` => 
        if (base.Global.user.isDefined || base.Global.isDemoMode || base.Global.isLocalMode) doQuickStart()
        else services.DemoManager.promptDemoMode("template-single", () => doQuickStart(), () => ())
      case `StartFullId`     => 
        if (base.Global.user.isDefined || base.Global.isDemoMode || base.Global.isLocalMode) confirmLeave(() => pages.loadPage(PageNameTyp("TourneyNew"), ""))
        else services.DemoManager.promptDemoMode("template-full", () => confirmLeave(() => pages.loadPage(PageNameTyp("TourneyNew"), "")), () => ())
      case `StartSimpleId`   => 
        if (base.Global.user.isDefined || base.Global.isDemoMode || base.Global.isLocalMode) confirmLeave(() => pages.loadPage(PageNameTyp("CompetitionNew"), ""))
        else services.DemoManager.promptDemoMode("template-single", () => confirmLeave(() => pages.loadPage(PageNameTyp("CompetitionNew"), "")), () => ())
      case `ExportLocalId`   => doLocalExport()
      case `ImportLocalId`   => doLocalImport()
      case `ExitOfflineId`   => doExitOffline()
      case _                 => debug(s"event -> unknown event for elem:${elem.id} with event:${event.`type`}")

  private def confirmLeave(action: () => Unit): Unit =
    import shared.BoxButton
    import shared.model.Selection
    
    if (base.Global.currentSelection.tourney.isDefined) {
      dialogs.DlgMsgbox.show(
        "Möchten Sie das aktuelle Turnier / den aktuellen Wettbewerb wirklich verlassen? Nicht gespeicherte Änderungen gehen verloren.",
        "Aktion bestätigen",
        List(BoxButton.Yes, BoxButton.No)
      ).map {
        case BoxButton.Yes => 
          base.Global.currentSelection = Selection()
          comps.ContextHeader.hide()
          action()
        case _ => debug("Leave cancelled")
      }
    } else {
      action()
    }

  def doQuickStart(): Unit =
    import dialogs.*
    import shared.model.*
    import services.*
    import base.Global

    def runDialog(initialCategory: CompCategory): Future[Unit] = {
      DlgCompetition.show(initialCategory).flatMap {
        case Right(res) =>
          val c = res.competition
          val dateInt = c.startDate.take(10).replace("-", "").toIntOption.getOrElse(20260101)
          
          // 1. Create SIMPLE dummy tournament
          val dummy = Tourney(
            wpId = 0,
            name = c.name,
            organizer = Global.user.map(_.username).getOrElse("System"),
            startDate = dateInt,
            endDate = dateInt,
            ident = "SIMPLE",
            category = c.category
          )
          
          // 2. Register tournament on server/locally
          TourneyDB.apiCreate(dummy).flatMap {
            case Right(slug) =>
              TourneyDB.update(dummy, doSync = false)
              
              // 3. Add the competition
              TourneyDB.tourney.addCompetition(
                c.name, 
                c.typ, 
                c.category, 
                c.startDate, 
                c.lowLevel, 
                c.upperLevel
              ) match {
                case Right(newComp) =>
                  Global.currentSelection = Selection(Some(TourneyDB.tourney), Some(newComp))
                  comps.ContextHeader.render()
                  // Force sync to ensure competition is saved
                  TourneyDB.sync().map { _ =>
                    pages.loadPage(PageNameTyp("CompetitionInfo"), "")
                  }
                case Left(err) =>
                  dom.window.alert(s"Fehler beim Erstellen des Wettbewerbs: ${err.msgCode}")
                  Future.successful(())
              }
            case Left(err) =>
              val errMsg = if (err.is("tourney_already_exists")) {
                "Ein Wettbewerb mit diesem Namen existiert bereits. Bitte wählen Sie einen anderen Namen."
              } else {
                s"Fehler beim Erstellen des Wettbewerbs: ${err.msgCode}"
              }
              dom.window.alert(errMsg)
              runDialog(c.category)
          }
        case Left(_) => 
          debug("Quick Start cancelled")
          Future.successful(())
      }
    }

    runDialog(CompCategory.TT)

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

  private def doLocalExport(): Unit = {
    import services.TourneyDB
    import services.DemoManager.DemoPayload
    import shared.basic.Pickle.*

    val tReq = TourneyDB.TourneySyncRequest(TourneyDB.version, TourneyDB.tourney)
    val payload = DemoPayload(tReq)
    val jsonString = write(payload)
    
    val blob = new dom.Blob(scalajs.js.Array(jsonString), dom.BlobPropertyBag("application/json"))
    val url = dom.URL.createObjectURL(blob)
    val a = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
    a.href = url
    a.download = s"tourney_${TourneyDB.tourney.name.replaceAll("\\s+", "_")}_export.json"
    dom.document.body.appendChild(a)
    a.click()
    dom.document.body.removeChild(a)
    dom.URL.revokeObjectURL(url)
  }

  private def doLocalImport(): Unit = {
    import services.*
    import base.Global
    import services.DemoManager.DemoPayload
    import shared.basic.Pickle.*

    val fileInput = dom.document.getElementById("navbar-import-file-input").asInstanceOf[dom.html.Input]
    if (fileInput != null) {
      fileInput.click()
      fileInput.onchange = (e: dom.Event) => {
        if (fileInput.files.length > 0) {
          val file = fileInput.files(0)
          val reader = new dom.FileReader()
          reader.onload = (e: dom.Event) => {
            val jsonString = reader.result.asInstanceOf[String]
            try {
              val payload = read[DemoPayload](jsonString)
              
              // Set Local Mode
              Global.isLocalMode = true
              Global.isDemoMode = false
              
              // Restore Tourney
              TourneyDB.tourney = payload.tourney.tourney
              TourneyDB.version = payload.tourney.version
              
              // Force sync to local storage
              TourneyDB.sync()
              ClubDB.sync(TourneyDB.tourney.clubs.toSeq)
              PlayerDB.sync(TourneyDB.tourney.players.toSeq)
              CompetitionDB.sync(TourneyDB.tourney.competitions.filter(_ != null).toSeq)
              StageDB.sync(TourneyDB.tourney.stages.filter(_ != null).toSeq)
              
              dom.window.alert("Import erfolgreich! Das lokale Turnier wird nun geladen.")
              comps.Navbar.render() // re-render to update badges/menu
              pages.loadPage(PageNameTyp("TourneyInfo"), "")
            } catch {
              case ex: Exception =>
                dom.window.alert(s"Fehler beim Importieren der Datei: ${ex.getMessage}")
            }
          }
          reader.readAsText(file)
        }
      }
    }
  }

  private def doExitOffline(): Unit = {
    import shared.BoxButton
    import shared.model.Selection
    import base.Global
    
    dialogs.DlgMsgbox.show(
      "Möchten Sie den lokalen Modus / Demo-Modus wirklich beenden? Alle ungesicherten lokalen Daten gehen verloren.",
      "Modus beenden",
      List(BoxButton.Yes, BoxButton.No)
    ).map {
      case BoxButton.Yes =>
        Global.isDemoMode = false
        Global.isLocalMode = false
        Global.currentSelection = Selection()
        comps.ContextHeader.hide()
        comps.Navbar.render() // re-render to remove badges/menu
        pages.loadPage(pages.MainView.name, "")
      case _ =>
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
