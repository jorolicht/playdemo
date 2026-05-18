package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.MainIds.*
import shared.model.*
import dialogs.*

object InfoCompetition extends BasePage with JsWrapper:
  def name = PageNameTyp("InfoCompetition")

  val BtnEditComp:       HtmlId = genId(name)
  val BtnDeleteComp:     HtmlId = genId(name)
  val BtnAddParticipant: HtmlId = genId(name)
  val BtnUploadCsv:      HtmlId = genId(name)
  val BtnAddRound:       HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    // If param is provided, try to find and select that competition
    if (param.nonEmpty) {
      val cId = CompId(param.toInt)
      services.CompetitionDB.competitions.find(c => c != null && c.id == cId).foreach { c =>
        // Clear round when switching competition
        Global.currentSelection = Global.currentSelection.copy(competition = Some(c), round = None)
        comps.ContextHeader.render()
      }
    }

    Global.currentSelection.competition match
      case Some(c) => 
        // Real participants from the competition object
        val participants = c.pants.toSeq
        
        // Real rounds from RoundDB for this competition
        val rounds = services.RoundDB.rounds.toSeq.filter(r => r != null && r.coId == c.id && !r.deleted)

        setMain(cviews.pages.html.InfoCompetition(c, participants, rounds))
        true
      case None => 
        debug("InfoCompetition: No competition selected, redirecting to Tourney Info")
        loadPage(InfoTourney.name, "")
        false

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnAddRound` => 
        // Simple prompt for round name for now, in a real app we might have DlgRound
        dialogs.DlgPrompt.show("").map {
          case Right(name) if name.trim.nonEmpty => 
            val coId = Global.currentSelection.competition.map(_.id).getOrElse(CompId(0))
            services.RoundDB.addRound(
              coId, 
              None, // No predecessor for new start round
              name, 
              RoundCfg.VRGR, // Default to Vorrunde Gruppen
              8, // Default size
              0  // Default players
            ) match {
              case Right(r) => 
                info(s"Round added: ${r.name}")
                render() // Re-render page
              case Left(err) => 
                error(s"Failed to add round: ${err.msgCode}")
            }
          case _ => debug("Add round cancelled or empty")
        }
      case `BtnDeleteComp` => 
        import shared.BoxValues.*
        DlgMsgbox.show("Möchten Sie diesen Wettbewerb wirklich löschen?", "Wettbewerb löschen", List(Yes, No)).map {
          case Yes => 
            val cId = Global.currentSelection.competition.map(_.id).getOrElse(CompId(0))
            services.CompetitionDB.delete(cId) match {
              case Right(_) => 
                Global.currentSelection = Global.currentSelection.copy(competition = None, round = None)
                comps.ContextHeader.render()
                loadPage(InfoTourney.name, "")
              case Left(err) => 
                error(s"Failed to delete competition: ${err.msgCode}")
            }
          case _ => debug("Delete cancelled")
        }
      case _ => 
        debug(s"InfoCompetition handleEvent: ${elem.id}")
