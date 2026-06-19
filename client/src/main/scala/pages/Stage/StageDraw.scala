package pages
package Stage

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.model.*
import shared.format.*

/**
 * Page handling the draw view for a stage.
 * Displays groups or elimination tree and allows swapping players before starting matches.
 */
object StageDraw extends BasePage with JsWrapper:
  def name = PageNameTyp("StageDraw")

  /** Button to start playing the stage. */
  val BtnStartPlaying: HtmlId = genId(name)
  /** HTML ID prefix for interactive player items (allows drag/click swap). */
  val PlayerItem:      HtmlId = genId(name)

  private var selectedPlayer: Option[(Int, SNO, HTMLElement)] = None

  def render(param: String = ""): Boolean = 
    selectedPlayer = None
    Global.currentSelection.stage match
      case Some(r) => 
        comps.ContextHeader.render()
        r.data match
          case StageData.GroupsStage(groups) =>
            setMain(cviews.comps.html.StageLayout(r, "DRW")(cviews.pages.Stage.Draw.html.Groups(r, groups.toSeq)))
            true
          case StageData.RoundRobinStage(rrGroup) =>
            setMain(cviews.comps.html.StageLayout(r, "DRW")(cviews.pages.Stage.Draw.html.RoundRobin(r, Seq(rrGroup))))
            true
          case StageData.SwissStage(swGroup) =>
            setMain(cviews.comps.html.StageLayout(r, "DRW")(cviews.pages.Stage.Draw.html.SwissSystem(r, Seq(swGroup))))
            true
          case StageData.KnockoutStage(state) =>
            val g = Group(1, state.size, 1, "KO-Baum (Setzung)", r.noWinSets)
            state.pants.zipWithIndex.foreach { case (p, i) => if (i < g.pants.length) g.pants(i) = p }
            setMain(cviews.comps.html.StageLayout(r, "DRW")(cviews.pages.Stage.Draw.html.SingleElimination(r, Seq(g))))
            true

      case None => 
        debug("StageDraw: No stage selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnStartPlaying` =>
        val stage = Global.currentSelection.stage.get
        val comp = Global.currentSelection.competition.get
        val isFin = comp.status == CompStatus.FIN || !base.Global.hasTourneyAccess(services.TourneyDB.tourney)
        if (isFin) {
          debug("Cannot start playing: competition is finalized or no write access.")
        } else {
          stage.initMatches(comp.typ) match {
            case Right(_) =>
              stage.status = StageStatus.EIN
              services.TourneyDB.tourney.updateStage(stage) match {
                case Right(updatedStage) =>
                  Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
                  loadPage(StageInput.name, "")
                case Left(err) =>
                  error(s"Failed to update stage status: ${err.msgCode}")
              }
            case Left(err) =>
              error(s"Failed to initialize matches: ${err.msgCode}")
          }
        }

      case id if id.id.startsWith(PlayerItem.id) =>
        val comp = Global.currentSelection.competition.get
        if (comp.status == CompStatus.FIN || !base.Global.hasTourneyAccess(services.TourneyDB.tourney)) {
          debug("Cannot swap players: competition is finalized or no write access.")
        } else {
          val suffix = elem.id.substring(PlayerItem.id.length + 1)
          val dashIdx = suffix.indexOf('-')
          val grId = suffix.substring(0, dashIdx).toInt
          val sno = SNO.fromString(suffix.substring(dashIdx + 1))
          val li = elem.asInstanceOf[HTMLElement]

          selectedPlayer match {
            case None =>
              selectedPlayer = Some((grId, sno, li))
              li.classList.add("bg-warning")
              li.classList.add("text-dark")
            case Some((oldGrId, oldSno, oldLi)) =>
              if (oldGrId == grId && oldSno == sno) {
                // Deselect
                li.classList.remove("bg-warning")
                li.classList.remove("text-dark")
                selectedPlayer = None
              } else {
                // Highlight the second player too!
                li.classList.add("bg-warning")
                li.classList.add("text-dark")

                // Swap confirmation
                val comp = Global.currentSelection.competition.get
                val p1 = comp.pants1Stage.find(_.id == oldSno).get
                val p2 = comp.pants1Stage.find(_.id == sno).get
                val stage = Global.currentSelection.stage.get

                dialogs.DlgMsgbox.show(
                  s"Möchten Sie Spieler ${p1.name} und Spieler ${p2.name} vertauschen?",
                  "Spieler tauschen",
                  List(shared.BoxButton.Yes, shared.BoxButton.No)
                ).map {
                  case shared.BoxButton.Yes =>
                    swapPlayers(stage, oldGrId, oldSno, grId, sno)
                    services.TourneyDB.tourney.updateStage(stage) match {
                      case Right(updatedStage) =>
                        Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
                      case _ =>
                    }
                    selectedPlayer = None
                    render()
                  case _ =>
                    oldLi.classList.remove("bg-warning")
                    oldLi.classList.remove("text-dark")
                    li.classList.remove("bg-warning")
                    li.classList.remove("text-dark")
                    selectedPlayer = None
                }
              }
          }
        }
      case _ =>

  private def swapPlayers(stage: Stage, gId1: Int, sno1: SNO, gId2: Int, sno2: SNO): Unit =
    stage.data match {
      case StageData.GroupsStage(groups) =>
        val g1Opt = groups.find(_.grId == gId1)
        val g2Opt = groups.find(_.grId == gId2)
        
        for {
          g1 <- g1Opt
          g2 <- g2Opt
          idx1 = g1.pants.indexWhere(p => p != null && p.id == sno1)
          idx2 = g2.pants.indexWhere(p => p != null && p.id == sno2)
          if idx1 != -1 && idx2 != -1
        } {
          val temp = g1.pants(idx1)
          g1.pants(idx1) = g2.pants(idx2)
          g2.pants(idx2) = temp
          
          recalcGroupAvgRating(g1)
          recalcGroupAvgRating(g2)
          recalcGroupOccu(g1)
          recalcGroupOccu(g2)
        }
      case _ =>
    }

  private def recalcGroupAvgRating(g: Group): Unit =
    val activePants = g.pants.filter(p => p != null && p.id != SNO.nn)
    if (activePants.nonEmpty) {
      g.avgRating = activePants.map(_.rating).sum / activePants.length
    } else {
      g.avgRating = 0
    }

  private def recalcGroupOccu(g: Group): Unit =
    val newOccu = scala.collection.mutable.Map[String, Int]().withDefaultValue(0)
    g.pants.foreach { p =>
      if (p != null && p.id != SNO.nn && p.club.trim.nonEmpty) {
        newOccu(p.club) = newOccu(p.club) + 1
      }
    }
    g.occu = newOccu
