package pages
package Stage

import org.scalajs.dom
import base.*
import shared.model.*

object StageResult extends BasePage with JsWrapper:
  def name = PageNameTyp("StageResult")

  def render(param: String = ""): Boolean = 
    Global.currentSelection.stage match
      case Some(stage) => 
        comps.ContextHeader.render()
        stage.data match
          case StageData.GroupsStage(groups) => 
            setMain(cviews.comps.html.StageLayout(stage, "RES")(cviews.pages.Stage.html.StageResult(stage, groups.toSeq)))
            true
          case StageData.KnockoutStage(ko) => 
            val g = shared.format.Group(1, ko.size, 1, "KO-Baum", stage.noWinSets)
            ko.pants.zipWithIndex.foreach { case (p, i) => if (i < g.pants.length) g.pants(i) = p }
            setMain(cviews.comps.html.StageLayout(stage, "RES")(cviews.pages.Stage.html.StageResult(stage, Seq(g))))
            true
          case StageData.SwissStage(sw) => 
            // TODO: Implement Swiss System Result View
            setMain(cviews.comps.html.StageLayout(stage, "RES")(play.twirl.api.Html("<span>Schweizer System Ergebnisse (Platzhalter)</span>")))
            true
          case StageData.RoundRobinStage(rr) => 
            // TODO: Implement Round Robin Result View
            setMain(cviews.comps.html.StageLayout(stage, "RES")(play.twirl.api.Html("<span>Round Robin Ergebnisse (Platzhalter)</span>")))
            true
      case None => 
        debug("StageResult: No stage selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false
