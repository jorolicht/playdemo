package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.MainIds.*
import shared.model.*
import dialogs.*
import javax.swing.Box

object TourneyInfo extends BasePage with JsWrapper:
  def name = PageNameTyp("TourneyInfo")

  val BtnEditTourney:   HtmlId = genId(name)
  val BtnDeleteTourney: HtmlId = genId(name)
  val BtnSaveLeave:     HtmlId = genId(name)
  val BtnAddComp:       HtmlId = genId(name)
  val TngComp:          HtmlId = genId(name)
  val TngStage:         HtmlId = genId(name)
  val BtnStageTab:      HtmlId = genId(name)

  /** Stage-Reiter im Baum der Wettbewerbsliste: (Seitenname, Bootstrap-Icon, Message-Key), wie in StageLayout. */
  val StageTabs: Seq[(String, String, String)] = Seq(
    ("StageAdmin",       "bi-gear",                 "stageLayout.tabAdmin"),
    ("StageDraw",        "bi-shuffle",              "stageLayout.tabDraw"),
    ("StageInput",       "bi-input-cursor-text",    "stageLayout.tabInput"),
    ("StageResult",      "bi-bar-chart-line",       "stageLayout.tabResult"),
    ("StageScoreSheet",  "bi-file-earmark-text",    "stageLayout.tabScoresheet"),
    ("StageCertificate", "bi-award",                "stageLayout.tabCertificate"))

  /** Aufgeklappte Wettbewerbe (CompId) und Stages (StageId) im Baum der Wettbewerbsliste. */
  private val openComps  = scala.collection.mutable.Set[Int]()
  private val openStages = scala.collection.mutable.Set[Int]()

  /**
   * Renders the tournament overview page.
   * If the tournament is a simple quick-start competition (ident == "SIMPLE"),
   * automatically redirects to CompetitionInfo instead of displaying TourneyInfo.
   *
   * @param param Optional routing parameter.
   * @return True upon successful render or redirect.
   */
  def render(param: String = ""): Boolean = 
    // Get tournament from TourneyDB or Selection
    val tourney = if (services.TourneyDB.tourney.wpId != 0) services.TourneyDB.tourney else Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)

    if (tourney.wpId != 0) {
      if (tourney.ident == "SIMPLE") {
        val comp = services.CompetitionDB.competitions.find(c => c != null && !c.deleted)
          .orElse(tourney.competitions.find(c => c != null && !c.deleted))
        Global.currentSelection = Global.currentSelection.copy(tourney = Some(tourney), competition = comp, stage = None)
        comps.ContextHeader.render()
        loadPage(CompetitionInfo.name, "")
        true
      } else {
        // When viewing tourney info, we usually want to clear the competition/round selection
        Global.currentSelection = Global.currentSelection.copy(tourney = Some(tourney), competition = None, stage = None)

        // Always render ContextHeader because loadPage hides it by default
        comps.ContextHeader.render()

        // Get real competitions from DB
        val compList = services.CompetitionDB.competitions.toSeq.filter(c => c != null && !c.deleted)

        val stagesByComp = services.StageDB.stages.toSeq.filter(s => s != null && !s.deleted).groupBy(_.coId.value)

        setMain(cviews.pages.html.TourneyInfo(tourney, compList, stagesByComp, openComps.toSet, openStages.toSet))
        true
      }
    } else {
      debug("TourneyInfo: No tournament found, redirecting to Home")
      loadPage(MainView.name, "")
      false
    }

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnEditTourney` =>
        val tourney = Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)
        dialogs.DlgEditTourney.show(tourney).map {
          case Right(updatedTourney) =>
            info(s"Turnier '${updatedTourney.name}' wurde aktualisiert.")
            Global.currentSelection = Selection(Some(updatedTourney))
            render()
          case Left(_) =>
            debug("Edit tourney cancelled")
        }
      case `BtnAddComp` => 
        val currentCategory = Global.currentSelection.tourney.map(_.category).getOrElse(CompCategory.TT)
        DlgCompetition.show(currentCategory).map {
          case Right(res) => 
            val comp = res.competition
            debug(s"Adding competition: ${comp.name}")
            services.TourneyDB.tourney.addCompetition(
              comp.name, 
              comp.typ, 
              comp.category,
              comp.startDate,
              comp.lowLevel,
              comp.upperLevel
            ) match {
              case Right(newComp) => 
                info(s"Competition added: ${newComp.name}")
                render() // Re-render page
              case Left(err) => 
                error(s"Failed to add competition: ${err.msgCode}")
                if (err.is("competition.date.outside.tourney")) {
                  dom.window.alert(base.Messages.gM("dlg.comp.errDate"))
                } else if (err.is("error.competition_already_exists") || err.is("competition.alreadyExists")) {
                  dom.window.alert(base.Messages.gM("error.competition_already_exists"))
                } else {
                  dialogs.DlgMsgbox.showError(err, "Fehler beim Hinzufügen des Wettbewerbs")
                }
            }
          case Left(_) => debug("Add competition cancelled")
        }
      case `BtnDeleteTourney` =>
        import shared.BoxButton
        val tourney = Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)
        DlgMsgbox.show("Möchten Sie dieses Turnier wirklich unwiderruflich löschen?", "Turnier löschen", List(BoxButton.Yes, BoxButton.No)).map {
          case BoxButton.Yes =>
            services.TourneyDB.apiDelete(tourney.wpId).map {
              case Right(_) =>
                info(s"Turnier '${tourney.name}' wurde gelöscht.")
                services.TourneyDB.tourney = Tourney.default
                Global.currentSelection = Selection()
                comps.ContextHeader.hide()
                comps.Navbar.render()
                loadPage(MainView.name, "")
              case Left(err) =>
                error(s"Löschen fehlgeschlagen: ${err.msgCode}")
                dialogs.DlgMsgbox.showError(err, "Fehler beim Löschen des Turniers")
            }
          case _ => debug("Delete cancelled")
        }

      case `BtnSaveLeave` =>
        import shared.BoxButton
        val tourney = Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)
        val isLocal = Global.isLocalMode || Global.isDemoMode || tourney.wpId == 999999 || tourney.slug == "local"
        val locationMsg = if (isLocal) {
          if (Global.lang == "en") "Storage location: \"local\" in browser"
          else "Speicherort: \"lokal\" im Browser"
        } else if (!Global.fws) {
          val pageId = if (Global.hostPageId > 0) Global.hostPageId else tourney.wpId
          if (Global.lang == "en") s"Storage location: WordPress Page ID $pageId"
          else s"Speicherort: WordPress-Seite ID $pageId"
        } else {
          if (Global.lang == "en") s"Storage location: \"cloud\" (${tourney.slug})"
          else s"Speicherort: \"cloud\" (${tourney.slug})"
        }
        val title = if (Global.lang == "en") "Save & Leave" else "Speichern & Verlassen"
        services.TourneyDB.sync().flatMap { _ =>
          DlgMsgbox.show(locationMsg, title, List(BoxButton.Ok)).map { _ =>
            Global.currentSelection = shared.model.Selection()
            comps.ContextHeader.render()
            loadPage(MainView.name, "")
          }
        }

      case id if id.id.startsWith(TngComp.id + "-") =>
        val coId = elem.id.substring(TngComp.id.length + 1).toInt
        if (!openComps.remove(coId)) openComps += coId
        render()

      case id if id.id.startsWith(TngStage.id + "-") =>
        val stageId = elem.id.substring(TngStage.id.length + 1).toInt
        if (!openStages.remove(stageId)) openStages += stageId
        render()

      // Suffix "<StageId>-<Seitenname>": Wettbewerb und Stage auswählen, dann Reiter-Seite laden
      case id if id.id.startsWith(BtnStageTab.id + "-") =>
        elem.id.substring(BtnStageTab.id.length + 1).split("-", 2) match
          case Array(sId, page) =>
            services.StageDB.stages.find(s => s != null && s.id.value == sId.toInt).foreach { stage =>
              val comp = services.CompetitionDB.competitions.find(c => c != null && c.id == stage.coId)
              Global.currentSelection = Global.currentSelection.copy(competition = comp, stage = Some(stage))
              comps.ContextHeader.render()
              loadPage(PageNameTyp(page), "")
            }
          case _ =>

      case _ =>
