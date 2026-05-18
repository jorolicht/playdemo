package pages

import org.scalajs.dom
import base.*
import shared.model.*

object RoundINP extends BasePage with JsWrapper:
  def name = PageNameTyp("RoundINP")

  def render(param: String = ""): Boolean = 
    Global.currentSelection.round match
      case Some(r) => 
        val comp = Global.currentSelection.competition
        val pants = comp.map(_.pants).getOrElse(Seq.empty)
        
        // Resolve SNO to Name helper
        def getName(sno: SNO): String = 
          pants.find(_.id == sno).map(_.name).getOrElse(sno.toString)

        val matches = r.matches.toSeq.map { m =>
          (m, getName(m.stNoA), getName(m.stNoB))
        }

        setMain(cviews.comps.html.RoundLayout(r, "INP")(cviews.pages.html.RoundINP(r, matches)))
        true
      case None => 
        debug("RoundINP: No round selected, redirecting to Competition Info")
        loadPage(InfoCompetition.name, "")
        false
