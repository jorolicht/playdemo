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
  val BtnStartStage:     HtmlId = genId(name)

  // Header IDs for sorting
  val IdHeaderSno:       HtmlId = genId(name)
  val IdHeaderName:      HtmlId = genId(name)
  val IdHeaderClub:      HtmlId = genId(name)
  val IdHeaderTtr:       HtmlId = genId(name)
  val IdHeaderYear:      HtmlId = genId(name)

  private var sortCol = "name"
  private var sortAsc = true

  def render(param: String = ""): Boolean = 
    // If param is provided, try to find and select that competition
    if (param.nonEmpty) {
      val cId = CompId(param.toInt)
      services.CompetitionDB.competitions.find(c => c != null && c.id == cId).foreach { c =>
        // Clear stage when switching competition
        Global.currentSelection = Global.currentSelection.copy(competition = Some(c), stage = None)
        comps.ContextHeader.render()
      }
    }

    Global.currentSelection.competition match
      case Some(c) => 
        comps.ContextHeader.render()
        // Real participants from the competition object, filtered by active and sorted
        val participants = sortParticipants(c.pants.toSeq.filter(_.active))
        
        // Real stages from the tourney object (shared model)
        val stages = services.TourneyDB.tourney.stages.toSeq.filter(s => s != null && s.coId == c.id && !s.deleted)

        setMain(cviews.pages.html.CompetitionInfo(c, participants, stages, sortCol, sortAsc))
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
      case "year"   => pants.sortBy(_.birthYear)
      case _        => pants.sortBy(_.name.toLowerCase)
    
    if (sortAsc) sorted else sorted.reverse

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `IdHeaderSno`    => toggleSort("sno")
      case `IdHeaderName`   => toggleSort("name")
      case `IdHeaderClub`   => toggleSort("club")
      case `IdHeaderTtr`    => toggleSort("ttr")
      case `IdHeaderYear`   => toggleSort("year")

      case `BtnStartStage` => 
        val c = Global.currentSelection.competition.get
        val existingStages = services.TourneyDB.tourney.stages.toSeq.filter(s => s != null && s.coId == c.id && !s.deleted)
        
        dialogs.DlgStageStart.show(existingStages).map {
          case Right(res) => 
            services.TourneyDB.tourney.addStage(
              coId = c.id, 
              prefId = res.prefId, 
              name = res.name, 
              stageConfig = StageConfig.VRGR, 
              size = 8, 
              noPlayers = 0
            ) match {
              case Right(r) => 
                Global.currentSelection = Global.currentSelection.copy(stage = Some(r))
                comps.ContextHeader.render()
                loadPage(StageAdmin.name, "")
              case Left(err) => 
                error(s"Failed to start stage: ${err.msgCode}")
            }
          case _ => debug("Start stage cancelled")
        }

      case `BtnDeleteComp` => 
        import shared.BoxButton
        DlgMsgbox.show("Möchten Sie diesen Wettbewerb wirklich löschen?", "Wettbewerb löschen", List(BoxButton.Yes, BoxButton.No)).map {
          case BoxButton.Yes => 
            val cId = Global.currentSelection.competition.map(_.id).getOrElse(CompId(0))
            services.TourneyDB.tourney.deleteCompetition(cId) match {
              case Right(_) => 
                Global.currentSelection = Global.currentSelection.copy(competition = None, stage = None)
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
