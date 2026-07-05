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
import pages.Stage.StageAdmin

/**
 * Page displaying detailed information about a selected competition.
 * Allows managing competition status (RUN/FIN), stages, and viewing participants.
 */
object CompetitionInfo extends BasePage with JsWrapper:
  def name = PageNameTyp("CompetitionInfo")

  /** Button to edit competition details. */
  val BtnEditComp:       HtmlId = genId(name)
  /** Button to delete the competition. */
  val BtnDeleteComp:     HtmlId = genId(name)
  /** Button to toggle competition status between RUN and FIN. */
  val BtnToggleStatus:   HtmlId = genId(name)
  /** Button to start a new stage. */
  val BtnStartStage:     HtmlId = genId(name)
  /** Button prefix to delete a specific stage. */
  val BtnDeleteStage:    HtmlId = genId(name)
  /** Radio button to update stage certificate configuration. */
  val RadioCert:         HtmlId = genId(name)

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
        val participants = sortParticipants(c.pants1Stage.toSeq.filter(_.active))
        
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
            if (existingStages.isEmpty) {
              c.pants1Stage.foreach { p =>
                p.status = if (p.active) PantStatus.PLAY else PantStatus.REGI
              }
              services.TourneyDB.tourney.updateCompetition(c)
            }

            val initialNoPlayers = if (existingStages.isEmpty) c.pants1Stage.count(_.active) else 0

            services.TourneyDB.tourney.addStage(
              coId = c.id, 
              prefId = res.prefId, 
              name = res.name, 
              stageConfig = StageConfig.CFG, 
              size = 8, 
              noPlayers = initialNoPlayers
            ) match {
              case Right(r) => 
                val updatedCompOpt = services.TourneyDB.tourney.competitions.find(comp => comp != null && comp.id == c.id)
                Global.currentSelection = Global.currentSelection.copy(stage = Some(r), competition = updatedCompOpt)
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

      case `BtnToggleStatus` =>
        Global.currentSelection.competition.foreach { comp =>
          val newStatus = if (comp.status == CompStatus.FIN) CompStatus.RUN else CompStatus.FIN
          val updatedComp = comp.copy(status = newStatus, version = comp.version + 1)
          
          services.TourneyDB.tourney.updateCompetition(updatedComp) match {
            case Right(c) =>
              Global.currentSelection = Global.currentSelection.copy(competition = Some(c))
              comps.ContextHeader.render()
              render()
            case Left(err) =>
              error(s"Failed to toggle status: ${err.msgCode}")
          }
        }

      case id if id.id.startsWith(BtnDeleteStage.id) =>
        val stageId = StageId(elem.id.substring(BtnDeleteStage.id.length + 1).toInt)
        handleDeleteStage(stageId)

      case id if id.id.startsWith(RadioCert.id) =>
        val stageId = StageId(elem.id.substring(RadioCert.id.length + 1).toInt)
        val isAlreadyChecked = services.TourneyDB.tourney.stages(stageId.value - 1).certificate
        val newCertVal = !isAlreadyChecked
        handleCertificateChange(stageId, newCertVal)

      case _ => 
        debug(s"CompetitionInfo handleEvent: ${elem.id}")

  private def handleCertificateChange(stageId: StageId, value: Boolean): Unit =
    val stages = services.TourneyDB.tourney.stages
    Global.currentSelection.competition.foreach { comp =>
      stages.zipWithIndex.foreach { case (s, idx) =>
        if (s != null && s.coId == comp.id && !s.deleted) {
          val newVal = if (s.id == stageId) value else false
          if (s.certificate != newVal) {
            s.certificate = newVal
            services.TourneyDB.tourney.updateStage(s) match {
              case Right(updatedStage) =>
                if (Global.currentSelection.stage.exists(_.id == s.id)) {
                  Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
                }
              case Left(err) =>
                error(s"Failed to update stage certificate: ${err.msgCode}")
            }
          }
        }
      }
    }
    render()

  private def handleDeleteStage(stageId: StageId): Unit =
    import shared.BoxButton
    val stages = services.TourneyDB.tourney.stages
    val rIdx = stageId.value - 1
    if (rIdx >= 0 && rIdx < 128 && stages(rIdx) != null) {
      val r = stages(rIdx)
      val msg = if (r.nextIds.nonEmpty) {
        s"Möchten Sie diese Stage und ALLE ${r.nextIds.length} nachfolgenden Stages wirklich löschen?"
      } else {
        "Möchten Sie diese Stage wirklich löschen?"
      }
      
      dialogs.DlgMsgbox.show(msg, "Stage löschen", List(BoxButton.Yes, BoxButton.No)).map {
        case BoxButton.Yes => 
          services.TourneyDB.tourney.deleteStage(stageId) match {
            case Right(_) => 
              if (Global.currentSelection.stage.exists(_.id == stageId)) {
                Global.currentSelection = Global.currentSelection.copy(stage = None)
              }
              val updatedCompOpt = Global.currentSelection.competition.flatMap { comp =>
                services.TourneyDB.tourney.competitions.find(c => c != null && c.id == comp.id)
              }
              Global.currentSelection = Global.currentSelection.copy(competition = updatedCompOpt)
              comps.ContextHeader.render()
              render()
            case Left(err) => 
              error(s"Failed to delete stage: ${err.msgCode}")
          }
        case _ => debug("Delete cancelled")
      }
    }

  private def toggleSort(col: String): Unit =
    if (sortCol == col) sortAsc = !sortAsc
    else {
      sortCol = col
      sortAsc = true
    }
    render()
