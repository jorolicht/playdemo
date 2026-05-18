package pages

import org.scalajs.dom
import base.*
import shared.model.*

object ResultList extends BasePage with JsWrapper:
  def name = PageNameTyp("ResultList")
  
  case class DisplayResult(name: String, club: String, compName: String, place: Int)

  def render(param: String = ""): Boolean = 
    val competitions = services.CompetitionDB.competitions.toSeq.filter(c => c != null && !c.deleted)
    
    val results = competitions.flatMap { c =>
      c.pants.filter(_.place._1 > 0).map { p =>
        DisplayResult(p.name, p.club, c.name, p.place._1)
      }
    }.sortBy(_.place)

    setMain(cviews.pages.html.ResultList(results))
    true
