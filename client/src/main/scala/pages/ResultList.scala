package pages

import org.scalajs.dom
import base.*
import shared.model.*

/**
 * Page displaying tournament results.
 * Displays a dedicated Card for each finished competition,
 * showing the results of the stage where certificate == true.
 */
object ResultList extends BasePage with JsWrapper:
  def name = PageNameTyp("ResultList")
  
  case class CompResultCard(
    comp: Competition,
    certStage: Option[Stage],
    pants: Seq[Pant]
  )

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
    
    val allStages = services.TourneyDB.tourney.stages.toSeq.filter(s => s != null && !s.deleted)

    val cards = targetComps.flatMap { comp =>
      val compStages = allStages.filter(_.coId == comp.id)
      val certStage = compStages.find(_.certificate).orElse(compStages.lastOption)
      val rankedPants = comp.pants1Stage.filter(_.place._1 > 0).sortBy(_.place._1).toSeq
      
      // Display card if competition is FIN or has ranked participants
      if (comp.status == CompStatus.FIN || rankedPants.nonEmpty) {
        Some(CompResultCard(comp, certStage, rankedPants))
      } else None
    }

    setMain(cviews.pages.html.ResultList(cards))
    true
