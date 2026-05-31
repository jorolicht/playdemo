package pages

import org.scalajs.dom
import base.*
import shared.model.*

object RoundDraw extends BasePage with JsWrapper:
  def name = PageNameTyp("RoundDraw")

  def render(param: String = ""): Boolean = 
    Global.currentSelection.round match
      case Some(r) => 
        val groups = r.groups.toSeq
        setMain(cviews.comps.html.RoundLayout(r, "DRW")(cviews.pages.html.RoundDraw(r, groups)))
        true
      case None => 
        debug("RoundDraw: No round selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false
