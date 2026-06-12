package pages

import org.scalajs.dom
import base.*
import shared.model.*

object StageDraw extends BasePage with JsWrapper:
  def name = PageNameTyp("StageDraw")

  def render(param: String = ""): Boolean = 
    Global.currentSelection.stage match
      case Some(r) => 
        comps.ContextHeader.render()
        val groups = r.groups.toSeq
        setMain(cviews.comps.html.StageLayout(r, "DRW")(cviews.pages.html.StageDraw(r, groups)))
        true
      case None => 
        debug("StageDraw: No stage selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false
