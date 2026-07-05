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
    val stages = Global.currentSelection.competition.map { c =>
      services.StageDB.stages.toSeq.filter(s => s != null && s.coId == c.id && !s.deleted)
    }.getOrElse(Seq.empty)
    
    setHtml(gE(ContextHeaderId), cviews.comps.html.ContextHeader(Global.currentSelection, comps, stages))
    true

  def hide(): Unit =
    setHtml(gE(ContextHeaderId), "")

  override def handleEvent(elem: HTMLElement, event: dom.Event): Unit = 
    debug(s"ContextHeader handleEvent: ${elem.id}")
