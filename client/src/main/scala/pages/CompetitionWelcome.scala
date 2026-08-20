package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import base.*
import shared.MainIds.*
import shared.model.*

/**
 * Public Competition Welcome page for VIEW mode.
 * Displays competition details, participant list, stages, and public registration.
 */
object CompetitionWelcome extends BasePage with JsWrapper:
  def name = PageNameTyp("CompetitionWelcome")

  val IdHeaderSno:  HtmlId = genId(name)
  val IdHeaderName: HtmlId = genId(name)
  val IdHeaderClub: HtmlId = genId(name)
  val IdHeaderTtr:  HtmlId = genId(name)
  val IdHeaderYear: HtmlId = genId(name)

  private var sortCol = "name"
  private var sortAsc = true

  def render(param: String = ""): Boolean = 
    if (param.nonEmpty) {
      val cId = CompId(param.toIntOption.getOrElse(0))
      services.CompetitionDB.competitions.find(c => c != null && c.id == cId).foreach { c =>
        val compStages = services.TourneyDB.tourney.stages.toSeq.filter(s => s != null && s.coId == c.id && !s.deleted)
        val autoStage = if (compStages.length == 1) compStages.headOption else None
        Global.currentSelection = Global.currentSelection.copy(competition = Some(c), stage = autoStage)
      }
    }

    Global.currentSelection.competition match
      case Some(c) =>
        comps.ContextHeader.render()
        val stages = services.TourneyDB.tourney.stages.toSeq.filter(s => s != null && s.coId == c.id && !s.deleted)
        val participants = sortParticipants(c.pants1Stage.toSeq.filter(_.active))
        val tourney = Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)
        val isFuture = TourneyWelcome.isCompStartInFuture(c, tourney)

        setMain(cviews.pages.html.CompetitionWelcome(c, participants, stages, sortCol, sortAsc, isFuture))
        true
      case None =>
        debug("CompetitionWelcome: No competition selected, redirecting to TourneyWelcome")
        loadPage(TourneyWelcome.name, "")
        false

  private def sortParticipants(pants: Seq[Pant]): Seq[Pant] =
    val sorted = sortCol match
      case "sno"  => pants.sortBy(_.id.startId)
      case "club" => pants.sortBy(_.club.toLowerCase)
      case "ttr"  => pants.sortBy(-_.rating)
      case "year" => pants.sortBy(_.birthYear)
      case _      => pants.sortBy(_.name.toLowerCase)
    
    if (sortAsc) sorted else sorted.reverse

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `IdHeaderSno`  => toggleSort("sno")
      case `IdHeaderName` => toggleSort("name")
      case `IdHeaderClub` => toggleSort("club")
      case `IdHeaderTtr`  => toggleSort("ttr")
      case `IdHeaderYear` => toggleSort("year")
      case _              => debug(s"CompetitionWelcome handleEvent: ${elem.id}")

  private def toggleSort(col: String): Unit =
    if (sortCol == col) sortAsc = !sortAsc
    else {
      sortCol = col
      sortAsc = true
    }
    render()
