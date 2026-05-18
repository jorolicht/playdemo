package pages

import org.scalajs.dom
import base.*
import shared.model.*

object RoundRES extends BasePage with JsWrapper:
  def name = PageNameTyp("RoundRES")

  def render(param: String = ""): Boolean = 
    Global.currentSelection.round match
      case Some(r) => 
        val groups = r.groups.toSeq
        setMain(cviews.comps.html.RoundLayout(r, "RES")(cviews.pages.html.RoundRES(r, groups)))
        true
      case None => 
        debug("RoundRES: No round selected, redirecting to Competition Info")
        loadPage(InfoCompetition.name, "")
        false
