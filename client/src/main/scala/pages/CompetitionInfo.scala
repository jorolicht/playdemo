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

object CompetitionInfo extends BasePage with JsWrapper:
  def name = PageNameTyp("CompetitionInfo")

  val BtnEditComp:       HtmlId = genId(name)
  val BtnDeleteComp:     HtmlId = genId(name)
  val BtnAddParticipant: HtmlId = genId(name)
  val BtnUploadCsv:      HtmlId = genId(name)
  val BtnStartRound:     HtmlId = genId(name)

  // Header IDs for sorting
  val IdHeaderSno:       HtmlId = genId(name)
  val IdHeaderName:      HtmlId = genId(name)
  val IdHeaderClub:      HtmlId = genId(name)
  val IdHeaderTtr:       HtmlId = genId(name)
  val IdHeaderActive:    HtmlId = genId(name)
  val PantActivId:       HtmlId = genId(name)

  private var sortCol = "name"
  private var sortAsc = true

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
        // Real participants from the competition object, sorted by state
        val participants = sortParticipants(c.pants.toSeq)
        
        // Real rounds from the tourney object (shared model)
        val rounds = services.TourneyDB.tourney.rounds.toSeq.filter(r => r != null && r.coId == c.id && !r.deleted)

        setMain(cviews.pages.html.CompetitionInfo(c, participants, rounds, sortCol, sortAsc))
        true
      case None => 
        debug("CompetitionInfo: No competition selected, redirecting to Tourney Info")
        loadPage(TourneyInfo.name, "")
        false

  private def sortParticipants(pants: Seq[Pant]): Seq[Pant] =
    val sorted = sortCol match
      case "sno"    => pants.sortBy(_.id.startId)
      case "club"   => pants.sortBy(_.club.toLowerCase)
      case "ttr"    => pants.sortBy(-_.rating)
      case "active" => pants.sortBy(_.active)
      case _        => pants.sortBy(_.name.toLowerCase)
    
    if (sortAsc) sorted else sorted.reverse

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `IdHeaderSno`    => toggleSort("sno")
      case `IdHeaderName`   => toggleSort("name")
      case `IdHeaderClub`   => toggleSort("club")
      case `IdHeaderTtr`    => toggleSort("ttr")
      case `IdHeaderActive` => toggleSort("active")
      case id if id.id.startsWith(PantActivId.id) =>
        val snoStr = getData(elem, "sno", "")
        val active = elem.asInstanceOf[dom.html.Input].checked
        updateParticipation(snoStr, active)

      case `BtnStartRound` => 
        val c = Global.currentSelection.competition.get
        val existingRounds = services.TourneyDB.tourney.rounds.toSeq.filter(r => r != null && r.coId == c.id && !r.deleted)
        
        dialogs.DlgRoundStart.show(existingRounds).map {
          case Right(res) => 
            services.TourneyDB.tourney.addRound(
              coId = c.id, 
              prefId = res.prefId, 
              name = res.name, 
              rndCfg = RoundCfg.VRGR, 
              size = 8, 
              noPlayers = 0
            ) match {
              case Right(r) => 
                Global.currentSelection = Global.currentSelection.copy(round = Some(r))
                comps.ContextHeader.render()
                loadPage(RoundAdmin.name, "")
              case Left(err) => 
                error(s"Failed to start round: ${err.msgCode}")
            }
          case _ => debug("Start round cancelled")
        }

      case `BtnDeleteComp` => 
        import shared.BoxValues.*
        DlgMsgbox.show("Möchten Sie diesen Wettbewerb wirklich löschen?", "Wettbewerb löschen", List(Yes, No)).map {
          case Yes => 
            val cId = Global.currentSelection.competition.map(_.id).getOrElse(CompId(0))
            services.TourneyDB.tourney.deleteCompetition(cId) match {
              case Right(_) => 
                Global.currentSelection = Global.currentSelection.copy(competition = None, round = None)
                comps.ContextHeader.render()
                loadPage(TourneyInfo.name, "")
              case Left(err) => 
                error(s"Failed to delete competition: ${err.msgCode}")
            }
          case _ => debug("Delete cancelled")
        }
      case _ => 
        debug(s"CompetitionInfo handleEvent: ${elem.id}")

  private def toggleSort(col: String): Unit =
    if (sortCol == col) sortAsc = !sortAsc
    else {
      sortCol = col
      sortAsc = true
    }
    render()

  private def updateParticipation(snoStr: String, active: Boolean): Unit =
    Global.currentSelection.competition.foreach { c =>
      val sno = SNO.fromString(snoStr)
      c.pants.find(_.id == sno).foreach { p =>
        p.active = active
        p.status = if (active) PantStatus.PLAY else PantStatus.REGI
        debug(s"Updated participant ${p.name}: active=$active")
        
        // Trigger sync of the entire competition
        services.TourneyDB.tourney.updateCompetition(c)
      }
    }
