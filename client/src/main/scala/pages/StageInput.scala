package pages

import org.scalajs.dom
import base.*
import shared.model.*

object StageInput extends BasePage with JsWrapper:
  def name = PageNameTyp("StageInput")

  def render(param: String = ""): Boolean = 
    Global.currentSelection.stage match
      case Some(r) => 
        comps.ContextHeader.render()
        val comp = Global.currentSelection.competition
        val pants = comp.map(_.pants).getOrElse(Seq.empty)
        
        // Resolve SNO to Name helper
        def getName(sno: SNO): String = 
          pants.find(_.id == sno).map(_.name).getOrElse(sno.toString)

        val matches = r.matches.toSeq.map { m =>
          (m, getName(m.stNoA), getName(m.stNoB))
        }

        setMain(cviews.comps.html.StageLayout(r, "INP")(cviews.pages.html.StageInput(r, matches)))
        true
      case None => 
        debug("StageInput: No stage selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false
