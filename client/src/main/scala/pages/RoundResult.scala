package pages

import org.scalajs.dom
import base.*
import shared.model.*

object RoundResult extends BasePage with JsWrapper:
  def name = PageNameTyp("RoundResult")

  def render(param: String = ""): Boolean = 
    Global.currentSelection.round match
      case Some(r) => 
        comps.ContextHeader.render()
        val groups = r.groups.toSeq
        setMain(cviews.comps.html.RoundLayout(r, "RES")(cviews.pages.html.RoundResult(r, groups)))
        true
      case None => 
        debug("RoundResult: No round selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false
