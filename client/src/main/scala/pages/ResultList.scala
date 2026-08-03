package pages

import org.scalajs.dom
import base.*
import shared.model.*

/**
 * Page displaying tournament results, supporting overall view and competition-specific filtering.
 */
object ResultList extends BasePage with JsWrapper:
  def name = PageNameTyp("ResultList")
  
  case class DisplayResult(name: String, club: String, compName: String, place: Int)

  def render(param: String = ""): Boolean = 
    val allComps = services.CompetitionDB.competitions.toSeq.filter(c => c != null && !c.deleted)
    
    val selectedCompId = try { if (param.nonEmpty) Some(param.toInt) else None } catch { case _: Exception => None }
    
    selectedCompId match {
      case Some(id) =>
        allComps.find(_.id.value == id).foreach { c =>
          Global.currentSelection = Global.currentSelection.copy(competition = Some(c))
        }
      case None =>
        Global.currentSelection = Global.currentSelection.copy(competition = None, stage = None)
    }
    
    comps.ContextHeader.render()

    val targetComps = selectedCompId match {
      case Some(id) => allComps.filter(_.id.value == id)
      case None     => allComps
    }
    
    val results = targetComps.flatMap { c =>
      c.pants1Stage.filter(_.place._1 > 0).map { p =>
        DisplayResult(p.name, p.club, c.name, p.place._1)
      }
    }.sortBy(_.place)

    setMain(cviews.pages.html.ResultList(results))
    true
