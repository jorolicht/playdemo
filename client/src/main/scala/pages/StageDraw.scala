package pages

import org.scalajs.dom
import base.*
import shared.model.*
import shared.format.*

object StageDraw extends BasePage with JsWrapper:
  def name = PageNameTyp("StageDraw")

  def render(param: String = ""): Boolean = 
    Global.currentSelection.stage match
      case Some(r) => 
        comps.ContextHeader.render()
        r.data match
          case StageData.GroupsStage(groups) =>
            setMain(cviews.comps.html.StageLayout(r, "DRW")(cviews.pages.StageDraw.html.Groups(r, groups.toSeq)))
            true
          case StageData.RoundRobinStage(rrGroup) =>
            setMain(cviews.comps.html.StageLayout(r, "DRW")(cviews.pages.StageDraw.html.RoundRobin(r, Seq(rrGroup))))
            true
          case StageData.SwissStage(swGroup) =>
            setMain(cviews.comps.html.StageLayout(r, "DRW")(cviews.pages.StageDraw.html.SwissSystem(r, Seq(swGroup))))
            true
          case StageData.KnockoutStage(state) =>
            val g = Group(1, state.size, 1, "KO-Baum (Setzung)", r.noWinSets)
            state.pants.zipWithIndex.foreach { case (p, i) => if (i < g.pants.length) g.pants(i) = p }
            setMain(cviews.comps.html.StageLayout(r, "DRW")(cviews.pages.StageDraw.html.SingleElimination(r, Seq(g))))
            true

      case None => 
        debug("StageDraw: No stage selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false
