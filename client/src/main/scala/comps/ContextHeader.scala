package comps

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import base.*
import shared.MainIds.*
import shared.model.*

object ContextHeader extends BaseComp with JsWrapper:
  def name = PageNameTyp("ContextHeader")

  def render(param: String = ""): Boolean = 
    val comps = services.CompetitionDB.competitions.toSeq.filter(c => c != null && !c.deleted)
    val rounds = Global.currentSelection.competition.map { c =>
      services.RoundDB.rounds.toSeq.filter(r => r != null && r.coId == c.id && !r.deleted)
    }.getOrElse(Seq.empty)
    
    setHtml(gE(NavbarId), cviews.comps.html.ContextHeader(Global.currentSelection, comps, rounds))
    true

  override def handleEvent(elem: HTMLElement, event: dom.Event): Unit = 
    debug(s"ContextHeader handleEvent: ${elem.id}")
