package pages

import org.scalajs.dom
import base.*
import shared.model.*

object StageResult extends BasePage with JsWrapper:
  def name = PageNameTyp("StageResult")

  def render(param: String = ""): Boolean = 
    Global.currentSelection.stage match
      case Some(r) => 
        comps.ContextHeader.render()
        val groups = r.groups.toSeq
        setMain(cviews.comps.html.StageLayout(r, "RES")(cviews.pages.html.StageResult(r, groups)))
        true
      case None => 
        debug("StageResult: No stage selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false
