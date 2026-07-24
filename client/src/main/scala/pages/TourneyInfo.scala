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
import javax.swing.Box

object TourneyInfo extends BasePage with JsWrapper:
  def name = PageNameTyp("TourneyInfo")

  val BtnEditTourney:   HtmlId = genId(name)
  val BtnDeleteTourney: HtmlId = genId(name)
  val BtnAddComp:       HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    // When viewing tourney info, we usually want to clear the competition/round selection
    Global.currentSelection = Global.currentSelection.copy(competition = None, stage = None)

    // Get tournament from Selection or TourneyDB
    val tourney = Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)

    if (tourney.wpId != 0) {
        // Update selection to ensure tournament is set
        Global.currentSelection = Global.currentSelection.copy(tourney = Some(tourney))
        
        // Always render ContextHeader because loadPage hides it by default
        comps.ContextHeader.render()

        // Get real competitions from DB
        val compList = services.CompetitionDB.competitions.toSeq.filter(c => c != null && !c.deleted)

        setMain(cviews.pages.html.TourneyInfo(tourney, compList))
        true
    } else {
        debug("TourneyInfo: No tournament found, redirecting to Home")
        loadPage(MainView.name, "")
        false
    }

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnAddComp` => 
        val currentCategory = Global.currentSelection.tourney.map(_.category).getOrElse(CompCategory.TT)
        DlgCompetition.show(currentCategory).map {
          case Right(res) => 
            val comp = res.competition
            debug(s"Adding competition: ${comp.name}")
            services.TourneyDB.tourney.addCompetition(
              comp.name, 
              comp.typ, 
              comp.category,
              comp.startDate.replace("-", ""),
              comp.lowLevel,
              comp.upperLevel
            ) match {
              case Right(newComp) => 
                info(s"Competition added: ${newComp.name}")
                render() // Re-render page
              case Left(err) => 
                error(s"Failed to add competition: ${err.msgCode}")
            }
          case Left(_) => debug("Add competition cancelled")
        }
      case `BtnDeleteTourney` =>
        import shared.BoxButton
        val tourney = services.TourneyDB.tourney
        DlgMsgbox.show("Möchten Sie dieses Turnier wirklich unwiderruflich löschen?", "Turnier löschen", List(BoxButton.Yes, BoxButton.No)).map {
          case BoxButton.Yes =>
            services.TourneyDB.apiDelete(tourney.wpId).map {
              case Right(_) =>
                info(s"Turnier '${tourney.name}' wurde gelöscht.")
                services.TourneyDB.tourney = Tourney.default
                Global.currentSelection = Selection()
                comps.ContextHeader.render()
                loadPage(MainView.name, "")
              case Left(err) =>
                error(s"Löschen fehlgeschlagen: ${err.msgCode}")
                dom.window.alert(s"Fehler beim Löschen des Turniers: ${err.msgCode}")
            }
          case _ => debug("Delete cancelled")
        }

      case _ => 
        debug(s"TourneyInfo handleEvent: ${elem.id}")
