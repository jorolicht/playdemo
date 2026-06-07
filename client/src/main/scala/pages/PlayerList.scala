package pages

import org.scalajs.dom
import base.*
import shared.model.*

object PlayerList extends BasePage with JsWrapper:
  def name = PageNameTyp("PlayerList")

  def render(param: String = ""): Boolean = 
    comps.ContextHeader.render()
    val competitions = services.CompetitionDB.competitions.toSeq.filter(c => c != null && !c.deleted)
    setMain(cviews.pages.html.PlayerList(competitions))
    true
