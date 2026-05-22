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

object InfoTourney extends BasePage with JsWrapper:
  def name = PageNameTyp("InfoTourney")

  val BtnEditTourney:   HtmlId = genId(name)
  val BtnDeleteTourney: HtmlId = genId(name)
  val BtnAddComp:       HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    // When viewing tourney info, we usually want to clear the competition/round selection
    if (Global.currentSelection.competition.isDefined || Global.currentSelection.round.isDefined) {
      Global.currentSelection = Global.currentSelection.copy(competition = None, round = None)
      comps.ContextHeader.render()
    }

    // Get tournament from Selection or TourneyDB
    val tourney = Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)

    if (tourney.id != 0) {
        // Update selection if it was empty
        if (Global.currentSelection.tourney.isEmpty) {
          Global.currentSelection = Global.currentSelection.copy(tourney = Some(tourney))
          comps.ContextHeader.render()
        }

        // Get real competitions from DB
        val compList = services.CompetitionDB.competitions.toSeq.filter(c => c != null && !c.deleted)

        setMain(cviews.pages.html.InfoTourney(tourney, compList))
        true
    } else {
        debug("InfoTourney: No tournament found, redirecting to Home")
        loadPage(MainMulti.name, "")
        false
    }

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnAddComp` => 
        DlgCompetition.show().map {
          case Right(res) => 
            debug(s"Adding competition: ${res.competition.name}")
            services.TourneyDB.tourney.addCompetition(
              res.competition.name, 
              res.competition.typ, 
              res.competition.startDate.replace("-", "")
            ) match {
              case Right(c) => 
                info(s"Competition added: ${c.name}")
                render() // Re-render page
              case Left(err) => 
                error(s"Failed to add competition: ${err.msgCode}")
            }
          case Left(_) => debug("Add competition cancelled")
        }
      case `BtnDeleteTourney` => 
        import shared.BoxValues.*
        DlgMsgbox.show("Möchten Sie dieses Turnier wirklich unwiderruflich löschen?", "Turnier löschen", List(Yes, No)).map {
          case Yes => 
            // In a real app, we would call an API here. 
            // For now, we clear the local state.
            services.TourneyDB.tourney = Tourney.default
            Global.currentSelection = Selection()
            comps.ContextHeader.render()
            loadPage(MainMulti.name, "")
          case _ => debug("Delete cancelled")
        }
      case _ => 
        debug(s"InfoTourney handleEvent: ${elem.id}")
