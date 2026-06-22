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
  /** Button to apply custom seeding permutation. */
  val BtnSetzen:       HtmlId = genId(name)
  /** HTML ID prefix for interactive player items (allows drag/click swap). */
  val PlayerItem:      HtmlId = genId(name)

  private var selectedPlayer: Option[(Int, SNO, HTMLElement)] = None
  private var rrResizeListener: Option[scala.scalajs.js.Function1[dom.Event, Unit]] = None

  def render(param: String = ""): Boolean = 
    selectedPlayer = None
    rrResizeListener.foreach(l => dom.window.removeEventListener("resize", l))
    rrResizeListener = None
    Global.currentSelection.stage match
      case Some(r) => 
        comps.ContextHeader.render()
        r.data match
          case StageData.GroupsStage(groups) =>
            setMain(cviews.comps.html.StageLayout(r, "DRW")(cviews.pages.Stage.Draw.html.Groups(r, groups.toSeq)))
            true
          case StageData.RoundRobinStage(rrGroup) =>
            setMain(cviews.comps.html.StageLayout(r, "DRW")(cviews.pages.Stage.Draw.html.RoundRobin(r, Seq(rrGroup))))
            initRoundRobinConnections(rrGroup.size)
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
      case `BtnSetzen` =>
        applySeeding()
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
                val p1Name = if (oldSno.isBye) "Freilos" else comp.pants1Stage.find(_.id == oldSno).map(_.name).getOrElse("Freilos")
                val p2Name = if (sno.isBye) "Freilos" else comp.pants1Stage.find(_.id == sno).map(_.name).getOrElse("Freilos")
                val stage = Global.currentSelection.stage.get

                dialogs.DlgMsgbox.show(
                  s"Möchten Sie Spieler ${p1Name} und Spieler ${p2Name} vertauschen?",
                  "Spieler tauschen",
                  List(shared.BoxButton.Yes, shared.BoxButton.No)
                ).map {
                  case shared.BoxButton.Yes =>
                    if (stage.isKoStage) {
                      SingleElimination.swapPlayers(stage, oldSno, sno)
                    } else {
                      Groups.swapPlayers(stage, oldGrId, oldSno, grId, sno)
                    }
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

  private def applySeeding(): Unit =
    Global.currentSelection.stage.foreach { stage =>
      stage.data match {
        case StageData.KnockoutStage(state) =>
          val inputs = dom.document.getElementsByClassName("draw-pos-input")
          val size = state.size
          
          val parsed = (0 until inputs.length).flatMap { i =>
            val input = inputs.item(i).asInstanceOf[dom.html.Input]
            val origPos = input.getAttribute("data-orig-pos").toIntOption
            val newPos = input.value.toIntOption
            for {
              op <- origPos
              np <- newPos
            } yield (op, np)
          }
          
          if (parsed.length != size) {
            dom.window.alert("Fehler: Nicht alle Setzpositionen konnten gelesen werden.")
          } else {
            val newPositions = parsed.map(_._2)
            val uniquePos = newPositions.distinct
            
            val isValidPermutation = newPositions.forall(p => p >= 1 && p <= size) && uniquePos.length == size
            
            if (!isValidPermutation) {
              dom.window.alert(s"Ungültige Setzpositionen! Es muss eine gültige Permutation von 1 bis $size sein.")
            } else {
              val newPants = scala.collection.mutable.ArrayBuffer.fill(size)(Pant(SNO.nn, name = ""))
              parsed.foreach { case (origPos, newPos) =>
                newPants(newPos - 1) = state.pants(origPos - 1)
              }
              
              state.pants.clear()
              state.pants ++= newPants
              
              state.sno2pos = scala.collection.mutable.Map[String, Int]()
              for (i <- 0 until size) state.sno2pos += (state.pants(i).id.toString -> i)
              
              services.TourneyDB.tourney.updateStage(stage) match {
                case Right(updatedStage) =>
                  Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
                  render()
                case Left(err) =>
                  dom.window.alert(s"Fehler beim Speichern der Setzung: ${err.msgCode}")
              }
            }
          }
        case _ =>
      }
    }

  /**
   * Initializes the connection lines representing the first round pairings in a Round Robin stage.
   * Draws straight, rectangular elbow lines outside the group card, updated dynamically on resize.
   *
   * @param size The number of players/positions in the group.
   */
  private def initRoundRobinConnections(size: Int): Unit =
    def updateConnections(): Unit =
      val svg = dom.document.querySelector(".rr-draw-connections").asInstanceOf[dom.html.Element]
      val ul = dom.document.querySelector(".list-group").asInstanceOf[dom.html.UList]
      val card = dom.document.querySelector(".draw-wrapper .card").asInstanceOf[dom.html.Element]
      if (svg == null || ul == null || card == null) return
      
      // Clear SVG children
      while (svg.lastChild != null) {
        svg.removeChild(svg.lastChild)
      }
      
      val listItems = ul.querySelectorAll(".player-draw-item")
      if (listItems.length < size) return
      
      import shared.utils.GroupPlan
      val plan = GroupPlan.get(size)
      if (plan.rounds.length == 0) return
      val round1Matches = plan.rounds(0)
      
      val cardRect = card.getBoundingClientRect()
      val svgRect = svg.getBoundingClientRect()
      val xStart = cardRect.right - svgRect.left
      val xStartAdjusted = xStart - 2.0
      val colors = Array("#0d6efd", "#198754", "#0dcaf0", "#e67e22")
      
      round1Matches.zipWithIndex.foreach { case (wgw, idx) =>
        val p1 = wgw._1
        val p2 = wgw._2
        
        // Only draw connection if both players are valid (not BYEs)
        if (p1 <= size && p2 <= size) {
          val idx1 = p1 - 1
          val idx2 = p2 - 1
          val li1 = listItems.item(idx1).asInstanceOf[dom.html.LI]
          val li2 = listItems.item(idx2).asInstanceOf[dom.html.LI]
          
          if (li1 != null && li2 != null) {
            val li1Rect = li1.getBoundingClientRect()
            val li2Rect = li2.getBoundingClientRect()
            
            val y1 = li1Rect.top - svgRect.top + li1Rect.height / 2.0
            val y2 = li2Rect.top - svgRect.top + li2Rect.height / 2.0
            
            val span = scala.math.abs(idx2 - idx1)
            val xOffset = xStart + 15.0 + (span * 8.0)
            
            val path = dom.document.createElementNS("http://www.w3.org/2000/svg", "path").asInstanceOf[dom.Element]
            // Straight, rectangular elbow line
            path.setAttribute("d", s"M ${xStartAdjusted} ${y1} L ${xOffset} ${y1} L ${xOffset} ${y2} L ${xStartAdjusted} ${y2}")
            
            val color = colors(idx % colors.length)
            path.setAttribute("style", s"stroke: ${color}; stroke-width: 2px; stroke-opacity: 0.7; fill: none; stroke-dasharray: 4 2;")
            svg.appendChild(path)
          }
        }
      }

    val rListener: scala.scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) => updateConnections()
    rrResizeListener = Some(rListener)
    dom.window.addEventListener("resize", rListener)
    
    dom.window.setTimeout(() => updateConnections(), 100)
