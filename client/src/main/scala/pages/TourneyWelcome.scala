package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.MainIds.*
import shared.model.*

/**
 * Public Tournament Welcome Homepage page for VIEW-Mode.
 * Displays greeting, tournament name, date, competitions schedule with start times,
 * online registration buttons, and homepageInfo in Markdown format.
 */
object TourneyWelcome extends BasePage with JsWrapper:
  def name = PageNameTyp("TourneyWelcome")

  /**
   * Checks whether a tournament start/end date is today or in the future.
   *
   * @param tourney The tournament object.
   * @return True if the tournament is in the future or today, false if in the past.
   */
  def isFutureOrToday(tourney: Tourney): Boolean =
    try {
      val d = new scala.scalajs.js.Date()
      val yyyy = d.getFullYear().toInt
      val mm = d.getMonth().toInt + 1
      val dd = d.getDate().toInt
      val todayInt = yyyy * 10000 + mm * 100 + dd
      tourney.startDate == 0 || tourney.startDate >= todayInt || tourney.endDate >= todayInt
    } catch {
      case _: Exception => true
    }

  /**
   * Renders the public tournament welcome homepage for VIEW-mode.
   *
   * @param param Optional parameter string.
   * @return True if tournament is present and rendered, false otherwise.
   */
  def render(param: String = ""): Boolean = 
    Global.currentSelection = Global.currentSelection.copy(competition = None, stage = None)

    val tourney = Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)

    if (tourney.wpId != 0) {
      Global.currentSelection = Global.currentSelection.copy(tourney = Some(tourney))
      
      // Render ContextHeader sub-menu
      comps.ContextHeader.render()

      val compList = services.CompetitionDB.competitions.toSeq.filter(c => c != null && !c.deleted)

      setMain(cviews.pages.html.TourneyWelcome(tourney, compList))
      true
    } else {
      debug("TourneyWelcome: No tournament found, redirecting to Home")
      loadPage(MainView.name, "")
      false
    }
