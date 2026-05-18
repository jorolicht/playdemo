package pages

import org.scalajs.dom
import base.*
import shared.model.*

object RoundDRW extends BasePage with JsWrapper:
  def name = PageNameTyp("RoundDRW")

  def render(param: String = ""): Boolean = 
    Global.currentSelection.round match
      case Some(r) => 
        val groups = r.groups.toSeq
        setMain(cviews.comps.html.RoundLayout(r, "DRW")(cviews.pages.html.RoundDRW(r, groups)))
        true
      case None => 
        debug("RoundDRW: No round selected, redirecting to Competition Info")
        loadPage(InfoCompetition.name, "")
        false
