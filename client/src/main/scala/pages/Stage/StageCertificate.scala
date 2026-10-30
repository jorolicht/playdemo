package pages
package Stage

import base.*
import shared.model.*

/** Reiter Urkunden: gleiche Ansicht wie Ergebnisse (je Format), mit Urkunden-Button pro Spieler mit feststehender Platzierung. */
object StageCertificate extends BasePage with JsWrapper:
  def name = PageNameTyp("StageCertificate")

  /**
   * Zeigt die Ergebnisansicht der ausgewählten Stage im Urkunden-Modus.
   *
   * @param param Nicht verwendet.
   * @return True, wenn eine Stage ausgewählt ist; sonst Weiterleitung zu CompetitionInfo.
   */
  def render(param: String = ""): Boolean = 
    Global.currentSelection.stage match
      case Some(stage) => 
        comps.ContextHeader.render()
        val view = stage.data match
          case StageData.GroupsStage(groups)  => cviews.pages.Stage.Result.html.Groups(stage, groups.toSeq, cert = true)
          case StageData.KnockoutStage(_)     => cviews.pages.Stage.Result.html.SingleElimination(stage, cert = true)
          case StageData.SwissStage(sw)       => cviews.pages.Stage.Result.html.SwissSystem(stage, sw, cert = true)
          case StageData.RoundRobinStage(rr)  => cviews.pages.Stage.Result.html.RoundRobin(stage, Seq(rr), cert = true)
        setMain(cviews.comps.html.StageLayout(stage, "CERT")(view))
        if (stage.data.isInstanceOf[StageData.KnockoutStage]) StageResult.initSlidingWindow()
        true
      case _ => 
        debug("StageCertificate: No stage selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false
