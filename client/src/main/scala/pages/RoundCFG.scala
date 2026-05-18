package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import base.*
import shared.model.*

object RoundCFG extends BasePage with JsWrapper:
  def name = PageNameTyp("RoundCFG")

  def render(param: String = ""): Boolean = 
    // If param is provided, try to find and select that round
    if (param.nonEmpty) {
      val rId = RoundId(param.toInt)
      services.RoundDB.rounds.find(r => r != null && r.id == rId).foreach { r =>
        Global.currentSelection = Global.currentSelection.copy(round = Some(r))
        comps.ContextHeader.render()
      }
    }

    Global.currentSelection.round match
      case Some(r) => 
        val comp = Global.currentSelection.competition
        val participants = comp.map(_.pants.toSeq).getOrElse(Seq.empty)
        setMain(cviews.comps.html.RoundLayout(r, "CFG")(cviews.pages.html.RoundCFG(r, participants)))
        true
      case None => 
        debug("RoundCFG: No round selected, redirecting to Competition Info")
        loadPage(InfoCompetition.name, "")
        false

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    debug(s"RoundCFG handleEvent: ${elem.id}")
    // Handle status changes or parameter updates here
