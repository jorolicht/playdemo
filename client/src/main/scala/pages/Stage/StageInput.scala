package pages
package Stage

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import base.*
import shared.model.*
import shared.format.*
import scala.collection.mutable.ArrayBuffer

/**
 * Usecase/Page for entering results of group matches in a stage.
 * Organizes matches by round, validates short-entry set results,
 * updates match statuses according to dependencies, and manages printing.
 */
object StageInput extends BasePage with JsWrapper:
  def name = PageNameTyp("StageInput")

  // HtmlId definitions for Twirl-bindable actions
  val PrintSrzBtn:    HtmlId = genId(name)
  val SaveMatchBtn:   HtmlId = genId(name)
  val DeleteMatchBtn: HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    Global.currentSelection.stage match
      case Some(r) => 
        comps.ContextHeader.render()
        val comp = Global.currentSelection.competition
        val pants = comp.map(_.pants1Stage.toSeq).getOrElse(Seq.empty)
        
        val matches = r.matches.collect { case m: MEntryGr => m }.toSeq.map { m =>
          (m, formatSnoName(m.stNoA, pants), formatSnoName(m.stNoB, pants))
        }

        setMain(cviews.comps.html.StageLayout(r, "INP")(cviews.pages.Stage.html.StageInput(r, matches)))
        
        // Setup initial validation states and input change listeners
        attachInputListeners(r)
        true
      case None => 
        debug("StageInput: No stage selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case id if id.id.startsWith(PrintSrzBtn.id) =>
        val roundNo = elem.id.substring(PrintSrzBtn.id.length + 1).toInt
        printRound(roundNo)
      case id if id.id.startsWith(SaveMatchBtn.id) =>
        val gameNo = elem.id.substring(SaveMatchBtn.id.length + 1).toInt
        saveMatchResult(gameNo)
      case id if id.id.startsWith(DeleteMatchBtn.id) =>
        val gameNo = elem.id.substring(DeleteMatchBtn.id.length + 1).toInt
        deleteMatchResult(gameNo)
      case _ =>

  /**
   * Formats player/double names. For singles, it outputs only the last name.
   * For doubles, it displays first names formatted as "Vorname1/Vorname2".
   */
  def formatSnoName(sno: SNO, pants: Seq[Pant]): String =
    if (sno.isNN) "Spielfrei"
    else if (sno.isBye) "Freilos"
    else
      pants.find(_.id == sno) match
        case Some(p) =>
          if (sno.isDouble) {
            val (id1, id2) = sno.doubleId
            val tourney = services.TourneyDB.tourney
            val p1Opt = tourney.players.find(_.id == id1)
            val p2Opt = tourney.players.find(_.id == id2)
            (p1Opt, p2Opt) match
              case (Some(pl1), Some(pl2)) =>
                s"${pl1.firstName}/${pl2.firstName}"
              case _ =>
                p.name.replace(" / ", "/")
          } else {
            val parts = p.name.split(",")
            if (parts.length > 0) parts(0).trim else p.name
          }
        case None =>
          sno.toString

  /**
   * Prints the referee sheets (Schiedsrichterzettel) for the specified round.
   * Hides all unnecessary admin UI elements during printing.
   */
  def printRound(roundNo: Int): Unit =
    val body = dom.document.body
    val roundEl = dom.document.getElementById(s"round-section-$roundNo")
    if (roundEl != null) {
      body.classList.add("print-active")
      roundEl.classList.add("active-print")
      dom.window.print()
      dom.window.setTimeout(() => {
        body.classList.remove("print-active")
        roundEl.classList.remove("active-print")
      }, 500)
    }

  /**
   * Saves the entered match results. Parses set inputs, propagates status updates
   * through dependencies, and updates the stage in the backend.
   */
  def saveMatchResult(gameNo: Int): Unit =
    Global.currentSelection.stage.foreach { stage =>
      val comp = Global.currentSelection.competition.get
      val isFin = comp.status == CompStatus.FIN || !base.Global.hasTourneyAccess(services.TourneyDB.tourney)
      if (isFin) {
        debug("StageInput: Cannot save match result because competition is finalized or user has no write access.")
      } else {
        val winSets = stage.noWinSets
        val inputs = (1 to (winSets * 2) - 1).map { setNo =>
          val el = dom.document.getElementById(s"input_${gameNo}_$setNo").asInstanceOf[dom.html.Input]
          if (el != null) el.value.trim else ""
        }.filter(_.nonEmpty)
        
        var aWins = 0
        var bWins = 0
        val parsedBalls = ArrayBuffer[String]()
        
        inputs.foreach { inp =>
          parseSetInput(inp).foreach { case (aPoints, bPoints) =>
            if (aPoints > bPoints) aWins += 1 else bWins += 1
            parsedBalls += inp
          }
        }
        
        stage.matches.find(_.gameNo == gameNo) match
          case Some(m: MEntryGr) =>
            m.sets = (aWins, bWins)
            m.result = parsedBalls.mkString("·")
            m.status = MEntry.MS_FIN
            
            // Re-evaluate stage match statuses based on dependencies
            updateStageMatchStatuses(stage)
            
            services.TourneyDB.tourney.updateStage(stage) match
              case Right(updatedStage) =>
                Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
                render()
              case Left(err) =>
                error(s"StageInput: Failed to save match result: ${err.msgCode}")
          case _ =>
      }
    }

  /**
   * Resets/deletes the match result and updates dependent block/playable statuses.
   */
  def deleteMatchResult(gameNo: Int): Unit =
    Global.currentSelection.stage.foreach { stage =>
      val comp = Global.currentSelection.competition.get
      val isFin = comp.status == CompStatus.FIN || !base.Global.hasTourneyAccess(services.TourneyDB.tourney)
      if (isFin) {
        debug("StageInput: Cannot delete match result because competition is finalized or user has no write access.")
      } else {
        stage.matches.find(_.gameNo == gameNo) match
          case Some(m: MEntryGr) =>
            m.sets = (0, 0)
            m.result = ""
            m.status = MEntry.MS_READY
            
            // Re-evaluate stage match statuses
            updateStageMatchStatuses(stage)
            
            services.TourneyDB.tourney.updateStage(stage) match
              case Right(updatedStage) =>
                Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
                render()
              case Left(err) =>
                error(s"StageInput: Failed to delete match result: ${err.msgCode}")
          case _ =>
      }
    }

  /**
   * Attaches event listeners for input validation to all match rows.
   */
  private def attachInputListeners(stage: Stage): Unit =
    val inputs = dom.document.querySelectorAll(".match-set-input")
    for (i <- 0 until inputs.length) {
      val inputEl = inputs.item(i).asInstanceOf[dom.html.Input]
      val gameNo = inputEl.getAttribute("data-game").toInt
      
      inputEl.oninput = (e: dom.Event) => {
        checkMatchValidity(gameNo, stage.noWinSets)
      }
    }
    
    // Run validation on load for all matches
    stage.matches.collect { case m: MEntryGr => m }.foreach { m =>
      checkMatchValidity(m.gameNo, stage.noWinSets)
    }
    
    // Set colors according to status
    updateColors(stage)

  /**
   * Performs validation on the set inputs of a specific match.
   * Activates the save button and shows sets score when valid and complete.
   */
  private def checkMatchValidity(gameNo: Int, winSets: Int): Boolean =
    val inputs = (1 to (winSets * 2) - 1).map { setNo =>
      val el = dom.document.getElementById(s"input_${gameNo}_$setNo").asInstanceOf[dom.html.Input]
      if (el != null) el.value.trim else ""
    }
    
    var aWins = 0
    var bWins = 0
    var isValid = true
    var winnerReached = false
    
    val nonOptInputs = inputs.filter(_.nonEmpty)
    
    for (inp <- nonOptInputs if !winnerReached) {
      parseSetInput(inp) match
        case Some((aPoints, bPoints)) =>
          if (aPoints > bPoints) aWins += 1 else bWins += 1
          if (aWins == winSets || bWins == winSets) {
            winnerReached = true
          }
        case None =>
          isValid = false
    }
    
    val saveBtn = dom.document.getElementById(s"${SaveMatchBtn.id}-$gameNo").asInstanceOf[dom.html.Button]
    val setsDisplay = dom.document.getElementById(s"sets_$gameNo").asInstanceOf[dom.html.Element]
    val setsPlayed = aWins + bWins
    
    if (isValid && winnerReached && nonOptInputs.length == setsPlayed) {
      if (saveBtn != null) saveBtn.disabled = false
      if (setsDisplay != null) {
        setsDisplay.textContent = s"$aWins:$bWins"
        setsDisplay.className = "badge bg-success text-white"
      }
      true
    } else {
      if (saveBtn != null) saveBtn.disabled = true
      if (setsDisplay != null) {
        setsDisplay.textContent = if (setsPlayed > 0) s"$aWins:$bWins" else ""
        setsDisplay.className = "badge bg-light text-dark border"
      }
      false
    }

  /**
   * Helper to parse TT results.
   * e.g. -5 stands for 5:11, 10 stands for 12:10.
   */
  private def parseSetInput(input: String): Option[(Int, Int)] =
    val trimmed = input.trim
    if (trimmed.isEmpty) None
    else
      try
        if (trimmed == "-0") {
          Some((0, 11))
        } else if (trimmed.startsWith("-")) {
          val value = trimmed.substring(1).toInt
          if (value >= 9) Some((value, value + 2))
          else Some((value, 11))
        } else {
          val value = trimmed.toInt
          if (value >= 9) Some((value + 2, value))
          else Some((11, value))
        }
      catch
        case _: NumberFormatException => None

  /**
   * Recalculates match status for all matches in the stage, setting them to
   * MS_READY if all their dependencies are finished, or MS_BLOCK otherwise.
   */
  private def updateStageMatchStatuses(stage: Stage): Unit =
    val matches = stage.matches.collect { case m: MEntryGr => m }.toSeq
    var changed = true
    while (changed) {
      changed = false
      matches.foreach { m =>
        if (!m.finished) {
          val deps = m.getDepend()
          val allDepsFinished = deps.forall { depGameNo =>
            matches.find(_.gameNo == depGameNo).exists(_.finished)
          }
          val targetStatus = if (allDepsFinished) MEntry.MS_READY else MEntry.MS_BLOCK
          if (m.status != targetStatus) {
            m.status = targetStatus
            changed = true
          }
        }
      }
    }

  /**
   * Dynamically colors the player name display elements.
   * Green for playable, Black for finished, Red for blocked.
   */
  private def updateColors(stage: Stage): Unit =
    val matches = stage.matches.collect { case m: MEntryGr => m }.toSeq
    matches.foreach { m =>
      val nameAEl = dom.document.getElementById(s"nameA-${m.gameNo}").asInstanceOf[dom.html.Span]
      val nameBEl = dom.document.getElementById(s"nameB-${m.gameNo}").asInstanceOf[dom.html.Span]
      
      if (nameAEl != null && nameBEl != null) {
        nameAEl.classList.remove("text-success")
        nameAEl.classList.remove("text-danger")
        nameAEl.classList.remove("text-dark")
        
        nameBEl.classList.remove("text-success")
        nameBEl.classList.remove("text-danger")
        nameBEl.classList.remove("text-dark")
        
        if (m.finished) {
          nameAEl.classList.add("text-dark")
          nameBEl.classList.add("text-dark")
        } else {
          val deps = m.getDepend()
          val allDepsFinished = deps.forall { depGameNo =>
            matches.find(_.gameNo == depGameNo).exists(_.finished)
          }
          if (allDepsFinished) {
            nameAEl.classList.add("text-success")
            nameBEl.classList.add("text-success")
          } else {
            nameAEl.classList.add("text-danger")
            nameBEl.classList.add("text-danger")
          }
        }
      }
    }
