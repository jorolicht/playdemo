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
import dialogs.DlgMsgbox
import scala.collection.mutable.ArrayBuffer

/**
 * Usecase/Page for entering results of group matches in a stage.
 * Organizes matches by round, validates short-entry set results,
 * updates match statuses according to dependencies, and manages printing.
 */
object StageInput extends BasePage with JsWrapper with services.ComWrapper:
  def name = PageNameTyp("StageInput")

  // HtmlId definitions for Twirl-bindable actions
  val PrintSrzBtn:    HtmlId = genId(name)
  val SaveMatchBtn:   HtmlId = genId(name)
  val DeleteMatchBtn: HtmlId = genId(name)
  val InfoMatchBtn:   HtmlId = genId(name)
  val SchiriBtn:      HtmlId = genId(name)
  val BtnStartNextRound: HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    Global.currentSelection.stage match
      case Some(r) => 
        comps.ContextHeader.render()
        val comp = Global.currentSelection.competition
        val pants = comp.map(_.pants1Stage.toSeq).getOrElse(Seq.empty)
        
        val matches = r.matches.toSeq.map { m =>
          (m, formatSnoName(m.stNoA, pants), formatSnoName(m.stNoB, pants))
        }

        setMain(cviews.comps.html.StageLayout(r, "INP")(cviews.pages.Stage.html.StageInput(r, matches)))
        
        // Setup initial validation states and input change listeners
        attachInputListeners(r)
        startRunningTimer()
        true
      case None => 
        stopRunningTimer()
        debug("StageInput: No stage selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case id if id.id.startsWith(PrintSrzBtn.id) =>
        val roundNo = elem.id.substring(PrintSrzBtn.id.length + 1).toInt
        loadPage(PageNameTyp("StageScoreSheet"), s"round_$roundNo")
      case id if id.id.startsWith(SaveMatchBtn.id) =>
        val gameNo = elem.id.substring(SaveMatchBtn.id.length + 1).toInt
        saveMatchResult(gameNo)
      case id if id.id.startsWith(DeleteMatchBtn.id) =>
        val gameNo = elem.id.substring(DeleteMatchBtn.id.length + 1).toInt
        deleteMatchResult(gameNo)
      case id if id.id.startsWith(InfoMatchBtn.id) =>
        val gameNo = elem.id.substring(InfoMatchBtn.id.length + 1).toInt
        showMatchInfo(gameNo)
      case id if id.id.startsWith(SchiriBtn.id) =>
        val gameNo = elem.id.substring(SchiriBtn.id.length + 1).toInt
        loadPage(PageNameTyp("StageScoreSheet"), gameNo.toString)
      case `BtnStartNextRound` =>
        startNextSwissRound()
      case _ =>

  /**
   * Formats player/double names. For singles, it outputs only the last name.
   * For doubles, it displays first names formatted as "Vorname1/Vorname2".
   */
  def formatSnoName(sno: SNO, pants: Seq[Pant]): String =
    if (sno.isNN) gM("+not_determined")
    else if (sno.isBye) gM("+bye")
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
      val isFin = comp.status == CompStatus.FIN || stage.status != StageStatus.EIN || !base.Global.hasTourneyAccess(services.TourneyDB.tourney)
      if (isFin) {
        debug("StageInput: Cannot save match result because stage is not in EIN status, competition is finalized, or user has no write access.")
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
        
        val playfieldInput = dom.document.getElementById(s"table_$gameNo").asInstanceOf[dom.html.Input]
        val playfieldVal = if (playfieldInput != null) playfieldInput.value.trim else ""
        stage.inputMatch(gameNo, (aWins, bWins), parsedBalls.mkString("·"), "", playfieldVal) match {
          case Left(err) => error(s"StageInput: inputMatch failed: ${err.msgCode}")
          case Right(triggered) =>
            stage.matches.find(_.gameNo == gameNo).foreach { m =>
              sendMatchboardUpdate("finish", playfieldVal, m, stage)
            }
            services.TourneyDB.tourney.updateStage(stage) match {
              case Right(updatedStage) =>
                Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
                if (updatedStage.status == StageStatus.FIN) {
                  render()
                } else {
                  // Disable save button, stop running time, and update colors without full page re-render
                  val saveBtn = dom.document.getElementById(s"${SaveMatchBtn.id}-$gameNo").asInstanceOf[dom.html.Button]
                  if (saveBtn != null) saveBtn.disabled = true
                  val timeCell = dom.document.getElementById(s"running-time-$gameNo").asInstanceOf[dom.raw.HTMLElement]
                  if (timeCell != null) {
                    timeCell.setAttribute("data-status", MEntry.MS_FIN.toString)
                    timeCell.textContent = "-"
                  }
                  updateColors(updatedStage)
                }
              case Left(err) =>
                error(s"StageInput: Failed to save match result: ${err.msgCode}")
            }
        }
      }
    }

  /**
   * Resets/deletes the match result and updates dependent block/playable statuses.
   */
  def deleteMatchResult(gameNo: Int): Unit =
    Global.currentSelection.stage.foreach { stage =>
      val comp = Global.currentSelection.competition.get
      val hasAccess = base.Global.hasTourneyAccess(services.TourneyDB.tourney)
      val allowedStatus = stage.status == StageStatus.EIN || stage.status == StageStatus.FIN
      val cannotDelete = comp.status == CompStatus.FIN || !allowedStatus || !hasAccess
      if (cannotDelete) {
        debug("StageInput: Cannot delete match result because stage status is not allowed, competition is finalized, or user has no write access.")
      } else {
        import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
        import dialogs.DlgMsgbox
        import shared.BoxButton

        DlgMsgbox.show(
          "Möchten Sie das Ergebnis dieses Spiels wirklich löschen?",
          "Spielergebnis löschen",
          List(BoxButton.Yes, BoxButton.No)
        ).map {
          case BoxButton.Yes =>
            stage.matches.find(_.gameNo == gameNo).foreach { m =>
              sendMatchboardUpdate("finish", m.playfield, m, stage)
            }
            stage.resetMatch(gameNo) match {
              case Left(err) => error(s"StageInput: resetMatch failed: ${err.msgCode}")
              case Right(triggered) =>
                services.TourneyDB.tourney.updateStage(stage) match {
                  case Right(updatedStage) =>
                    Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
                    render()
                  case Left(err) =>
                    error(s"StageInput: Failed to delete match result: ${err.msgCode}")
                }
            }
          case _ =>
            debug("StageInput: Match deletion cancelled by user.")
        }
      }
    }

  /**
   * Startet die nachfolgende Runde für das Schweizer System (Swiss Stage).
   * Ermittelt die nächste Runden-Nummer, ruft stage.initMatches auf, setzt den Status
   * auf EIN, aktualisiert die DB und lädt die Ansicht neu.
   */
  def startNextSwissRound(): Unit =
    Global.currentSelection.stage.foreach { stage =>
      val comp = Global.currentSelection.competition.get
      val isFin = comp.status == CompStatus.FIN || !base.Global.hasTourneyAccess(services.TourneyDB.tourney)
      if (isFin) {
        debug("StageInput: Cannot start next Swiss round because competition is finalized or no write access.")
      } else {
        SwissSys.generateNextRoundPairing(stage) match {
          case Right(updatedStage) =>
            services.TourneyDB.tourney.updateStage(updatedStage) match {
              case Right(savedStage) =>
                Global.currentSelection = Global.currentSelection.copy(stage = Some(savedStage))
                loadPage(PageNameTyp("StageDraw"), "")
              case Left(err) =>
                error(s"Failed to update stage status: ${err.msgCode}")
            }
          case Left(err) =>
            error(s"Failed to generate next Swiss round pairings: ${err.msgCode}")
        }
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
    stage.matches.foreach { m =>
      checkMatchValidity(m.gameNo, stage.noWinSets)
    }
    
    // Attach event listeners for table number input
    val tableInputs = dom.document.querySelectorAll(".match-table-input")
    for (i <- 0 until tableInputs.length) {
      val tInput = tableInputs.item(i).asInstanceOf[dom.html.Input]
      val gameNo = tInput.getAttribute("data-game").toInt
      tInput.onchange = (e: dom.Event) => {
        updateMatchTable(gameNo, tInput.value.trim, stage)
      }
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
    if (stage.isKoStage) updateKoMatchStatuses(stage)
    else updateGrMatchStatuses(stage)

  private def updateGrMatchStatuses(stage: Stage): Unit =
    val matches = stage.matches.collect { case m: MEntryGr => m }.toSeq
    var changed = true
    while (changed) {
      changed = false
      matches.foreach { m =>
        if (!m.finished && m.status != MEntry.MS_RUN) {
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

  private def updateKoMatchStatuses(stage: Stage): Unit =
    var changed = true
    while (changed) {
      changed = false
      stage.matches.foreach {
        case m: MEntryKo =>
          if (m.stNoA.isNN || m.stNoB.isNN) {
            val targetStatus = MEntry.MS_BLOCK
            if (m.status != targetStatus || m.sets != (0, 0) || m.result != "") {
              m.status = targetStatus
              m.setSets((0, 0))
              m.setResult("")
              changed = true
            }
          } else if (!m.finished && m.status != MEntry.MS_RUN) {
            val aReady = m.stNoA != SNO.nn
            val bReady = m.stNoB != SNO.nn
            val targetStatus = if (aReady && bReady) {
              if (m.stNoA.isBye || m.stNoB.isBye) {
                val winnerSno = if (m.stNoA.isBye) m.stNoB else m.stNoA
                val (nextGameNo, nextPos) = m.getWinPos()
                if (nextGameNo > 0) {
                  stage.matches.find(_.gameNo == nextGameNo).foreach { nm =>
                    val currentSno = if (nextPos == 0) nm.stNoA else nm.stNoB
                    if (currentSno != winnerSno) {
                      nm.setPant(nextPos, winnerSno)
                      changed = true
                    }
                  }
                }
                val (setsA, setsB) = if (m.stNoA.isBye) (0, m.winSets) else (m.winSets, 0)
                m.setSets((setsA, setsB))
                m.setResult("")
                MEntry.MS_FIX
              } else {
                MEntry.MS_READY
              }
            } else {
              MEntry.MS_BLOCK
            }
            if (m.status != targetStatus) {
              m.status = targetStatus
              changed = true
            }
          }
        case _ =>
      }
    }

  private def updateColors(stage: Stage): Unit =
    if (stage.isKoStage) updateKoColors(stage)
    else if (stage.stageConfig.format == StageFormat.SW) updateSwColors(stage)
    else updateGrColors(stage)

  /**
   * Aktualisiert die Farbcodierung für das Schweizer System (Swiss Stage).
   * Bereits abgeschlossene Spiele werden dunkel gefärbt, noch offene Spiele werden
   * grün markiert, da im Schweizer System alle Spiele einer Runde sofort spielbereit sind.
   */
  private def updateSwColors(stage: Stage): Unit =
    val matches = stage.matches.collect { case m: MEntrySw => m }.toSeq
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
          nameAEl.classList.add("text-success")
          nameBEl.classList.add("text-success")
        }
      }
    }

  private def updateGrColors(stage: Stage): Unit =
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

  private def updateKoColors(stage: Stage): Unit =
    val comp = Global.currentSelection.competition
    val pants = comp.map(_.pants1Stage.toSeq).getOrElse(Seq.empty)
    stage.matches.foreach {
      case m: MEntryKo =>
        val nameAEl = dom.document.getElementById(s"nameA-${m.gameNo}").asInstanceOf[dom.html.Span]
        val nameBEl = dom.document.getElementById(s"nameB-${m.gameNo}").asInstanceOf[dom.html.Span]
        
        if (nameAEl != null && nameBEl != null) {
          nameAEl.textContent = formatSnoName(m.stNoA, pants)
          nameBEl.textContent = formatSnoName(m.stNoB, pants)
          nameAEl.className = "fw-bold player-name-display"
          nameBEl.className = "fw-bold player-name-display"
          
          if (m.finished) {
            nameAEl.classList.add("text-dark")
            nameBEl.classList.add("text-dark")
          } else {
            if (m.status == MEntry.MS_READY || m.status == MEntry.MS_RUN) {
              nameAEl.classList.add("text-success")
              nameBEl.classList.add("text-success")
            } else {
              nameAEl.classList.add("text-danger")
              nameBEl.classList.add("text-danger")
            }
          }
        }

        // Enable/disable inputs and buttons based on access and player determination
        val isBlocked = m.stNoA.isNN || m.stNoB.isNN || m.stNoA.isBye || m.stNoB.isBye || m.status == MEntry.MS_FIX
        val hasNoAccess = !base.Global.hasTourneyAccess(services.TourneyDB.tourney) || 
                          comp.exists(_.status == CompStatus.FIN) || 
                          stage.status != StageStatus.EIN
        val rowDisabled = hasNoAccess || isBlocked

        val tableInput = dom.document.getElementById(s"table_${m.gameNo}").asInstanceOf[dom.html.Input]
        if (tableInput != null) tableInput.disabled = rowDisabled

        (1 to (stage.noWinSets * 2) - 1).foreach { setNo =>
          val el = dom.document.getElementById(s"input_${m.gameNo}_$setNo").asInstanceOf[dom.html.Input]
          if (el != null) el.disabled = rowDisabled
        }

        val deleteBtn = dom.document.getElementById(s"${DeleteMatchBtn.id}-${m.gameNo}").asInstanceOf[dom.html.Button]
        if (deleteBtn != null) deleteBtn.disabled = rowDisabled

        val saveBtn = dom.document.getElementById(s"${SaveMatchBtn.id}-${m.gameNo}").asInstanceOf[dom.html.Button]
        if (saveBtn != null && rowDisabled) saveBtn.disabled = true
      case _ =>
    }

  private def nowTimestamp(): String =
    val d = new scala.scalajs.js.Date()
    val yyyy = d.getFullYear().toInt.toString
    val mm = f"${d.getMonth().toInt + 1}%02d"
    val dd = f"${d.getDate().toInt}%02d"
    val hh = f"${d.getHours().toInt}%02d"
    val min = f"${d.getMinutes().toInt}%02d"
    val ss = f"${d.getSeconds().toInt}%02d"
    s"$yyyy$mm$dd$hh$min$ss"

  def getRunningTime(startTimeStr: String): String =
    if startTimeStr == null || startTimeStr.length != 14 then return "-"
    try
      val year = startTimeStr.substring(0, 4).toInt
      val month = startTimeStr.substring(4, 6).toInt - 1
      val day = startTimeStr.substring(6, 8).toInt
      val hour = startTimeStr.substring(8, 10).toInt
      val minute = startTimeStr.substring(10, 12).toInt
      val second = startTimeStr.substring(12, 14).toInt
      
      val startMs = new scala.scalajs.js.Date(year, month, day, hour, minute, second).getTime()
      val nowMs = new scala.scalajs.js.Date().getTime()
      val diffSeconds = ((nowMs - startMs) / 1000).toLong
      if diffSeconds < 0 then return "00:00"
      
      val m = diffSeconds / 60
      val s = diffSeconds % 60
      f"$m%02d:$s%02d"
    catch
      case _: Throwable => "-"

  import scala.scalajs.js.timers.*
  private var runningTimer: Option[SetIntervalHandle] = None

  def startRunningTimer(): Unit =
    stopRunningTimer()
    val intervalId = setInterval(1000) {
      val elements = dom.document.querySelectorAll("[id^='running-time-']")
      if elements.length == 0 then
        stopRunningTimer()
      else
        for i <- 0 until elements.length do
          val elem = elements.item(i).asInstanceOf[dom.raw.HTMLElement]
          val status = elem.getAttribute("data-status")
          val startTime = elem.getAttribute("data-start-time")
          if status == MEntry.MS_RUN.toString && startTime != null && startTime.nonEmpty then
            elem.textContent = getRunningTime(startTime)
    }
    runningTimer = Some(intervalId)

  def stopRunningTimer(): Unit =
    runningTimer.foreach(clearInterval)
    runningTimer = None

  def updateMatchTable(gameNo: Int, tableVal: String, stage: Stage): Unit =
    val comp = Global.currentSelection.competition.get
    val isFin = comp.status == CompStatus.FIN || stage.status != StageStatus.EIN || !base.Global.hasTourneyAccess(services.TourneyDB.tourney)
    if (isFin) {
      debug("StageInput: Cannot update table number because stage is not in EIN status, competition is finalized, or user has no write access.")
    } else {
      stage.matches.find(_.gameNo == gameNo) match
        case Some(m) =>
          val oldCourtVal = m.playfield
          m.setPlayfield(tableVal)
          
          if (!m.finished) {
            if (tableVal.nonEmpty) {
              if (m.status != MEntry.MS_RUN) {
                m.setStatus(MEntry.MS_RUN)
                if (m.startTime == null || m.startTime.trim.isEmpty) {
                  m.startTime = nowTimestamp()
                }
              }
              sendMatchboardUpdate("start", tableVal, m, stage)
            } else {
              m.setStatus(MEntry.MS_READY)
              m.startTime = ""
              updateStageMatchStatuses(stage)
              sendMatchboardUpdate("start", "", m, stage)
            }
          }
          
          services.TourneyDB.tourney.updateStage(stage) match
            case Right(updatedStage) =>
              Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
              // Update time display attributes and colors without full render
              val timeCell = dom.document.getElementById(s"running-time-$gameNo").asInstanceOf[dom.raw.HTMLElement]
              if (timeCell != null) {
                timeCell.setAttribute("data-status", m.status.toString)
                timeCell.setAttribute("data-start-time", m.startTime)
                if (m.status == MEntry.MS_RUN) {
                  timeCell.textContent = getRunningTime(m.startTime)
                } else {
                  timeCell.textContent = "-"
                }
              }
              updateColors(updatedStage)
            case Left(err) =>
              error(s"StageInput: Failed to update table number: ${err.msgCode}")
        case _ =>
    }

  private def sendMatchboardUpdate(entryType: String, courtVal: String, m: MEntry, stage: Stage): Unit =
    val t = services.TourneyDB.tourney
    val slugName = t.slug.split('/').lastOption.getOrElse(t.slug)
    val slugParam = s"${slugName.trim}-${t.wpId}"
    
    val comp = t.competitions.filter(_ != null).find(_.id == stage.coId).get
    
    // Look up player names
    val pants = comp.pants1Stage.toSeq
    val nameA = formatSnoName(m.stNoA, pants)
    val nameB = formatSnoName(m.stNoB, pants)
    
    val entry = shared.model.MatchboardEntry(
      id = s"match_${stage.id.value}_${m.gameNo}",
      entryType = entryType,
      court = Some(courtVal.trim),
      nameA = Some(nameA),
      nameB = Some(nameB),
      compName = Some(comp.name),
      stageId = Some(stage.id),
      gameNo = Some(m.gameNo)
    )
    
    val request = shared.model.MatchboardSetRequest(
      action = entryType,
      tourneyName = Some(t.name),
      entry = Some(entry)
    )
    
    ajaxPost[shared.model.MatchboardSetRequest, shared.model.MatchboardSetResponse]("/matchboard/set", List("slug" -> slugParam), request).map {
      case Right(res) if res.success =>
        debug(s"Matchboard update successfully sent: entryType=$entryType, court=$courtVal")
      case Right(_) =>
        debug(s"Matchboard update returned success=false: entryType=$entryType, court=$courtVal")
      case Left(err) =>
        debug(s"Failed to send matchboard update: ${err.msgCode}")
    }

  def showMatchInfo(gameNo: Int): Unit =
    Global.currentSelection.stage.foreach { stage =>
      val comp = Global.currentSelection.competition.get
      stage.matches.find(_.gameNo == gameNo) match {
        case Some(m) =>
          val pants = comp.pants1Stage.toSeq
          val nameA = formatSnoName(m.stNoA, pants)
          val nameB = formatSnoName(m.stNoB, pants)
          
          val statusStr = m.status match {
            case MEntry.MS_RESET => "Nicht konfiguriert"
            case MEntry.MS_MISS  => "Spieler fehlt"
            case MEntry.MS_BLOCK => "Blockiert"
            case MEntry.MS_READY => "Bereit"
            case MEntry.MS_RUN   => "Laufend"
            case MEntry.MS_FIN   => "Beendet"
            case MEntry.MS_FIX   => "Beendet (Kampflos/Freilos)"
            case MEntry.MS_DRAW  => "Unentschieden"
            case _               => "Unbekannt"
          }

          val infoText = m.info.trim
          val infoDisplay = if (infoText.isEmpty) "-" else infoText

          val startTimeDisplay = if (m.startTime == null || m.startTime.trim.isEmpty) "-" else m.startTime
          val endTimeDisplay = if (m.endTime == null || m.endTime.trim.isEmpty) "-" else m.endTime
          val playfieldDisplay = if (m.playfield == null || m.playfield.trim.isEmpty) "-" else m.playfield

          val resultDisplay = if (m.result == null || m.result.trim.isEmpty) "-" else m.result.replace("·", ", ")

          val body =
            s"""Spiel-Nr:       ${m.gameNo}
               |Wettbewerb:     ${comp.name}
               |Begegnung:     $nameA  vs  $nameB
               |Tisch:          $playfieldDisplay
               |Status:         $statusStr
               |Startzeit:      $startTimeDisplay
               |Endzeit:        $endTimeDisplay
               |Sätze:          ${m.sets._1}:${m.sets._2} (Satzstände: $resultDisplay)
               |Bemerkung:      $infoDisplay""".stripMargin

          val hasResult = infoText.startsWith("Result")
          val buttons = if (hasResult) {
            List(shared.BoxButton.Ok, shared.BoxButton.AcceptResult)
          } else {
            List(shared.BoxButton.Ok)
          }

          DlgMsgbox.show(body, s"Spiel-Informationen (Spiel ${m.gameNo})", buttons).map {
            case shared.BoxButton.AcceptResult =>
              acceptMatchResult(gameNo, infoText)
            case _ =>
              // do nothing
          }
        case None =>
          error(s"StageInput: Match not found for info: $gameNo")
      }
    }

  def acceptMatchResult(gameNo: Int, infoText: String): Unit =
    Global.currentSelection.stage.foreach { stage =>
      val comp = Global.currentSelection.competition.get
      val isFin = comp.status == CompStatus.FIN || stage.status != StageStatus.EIN || !base.Global.hasTourneyAccess(services.TourneyDB.tourney)
      if (isFin) {
        debug("StageInput: Cannot accept match result because stage is not in EIN status or user has no write access.")
      } else {
        val setsStr = infoText.stripPrefix("Result").trim
        val sets = setsStr.split(",").map(_.trim).filter(_.nonEmpty)
        
        var aWins = 0
        var bWins = 0
        val parsedBalls = ArrayBuffer[String]()
        
        for (set <- sets) {
          val pts = set.split(":").map(_.trim.toInt)
          if (pts.length == 2) {
            val aPoints = pts(0)
            val bPoints = pts(1)
            if (aPoints > bPoints) aWins += 1 else bWins += 1
            val shortForm = if (aPoints > bPoints) {
              bPoints.toString
            } else {
              if (aPoints == 0 && bPoints == 11) "-0"
              else s"-$aPoints"
            }
            parsedBalls += shortForm
          }
        }
        
        val playfieldInput = dom.document.getElementById(s"table_$gameNo").asInstanceOf[dom.html.Input]
        val playfieldVal = if (playfieldInput != null) playfieldInput.value.trim else ""
        
        stage.inputMatch(gameNo, (aWins, bWins), parsedBalls.mkString("·"), "", playfieldVal) match {
          case Left(err) => error(s"StageInput: acceptMatchResult failed: ${err.msgCode}")
          case Right(_) =>
            stage.matches.find(_.gameNo == gameNo).foreach { m =>
              sendMatchboardUpdate("finish", playfieldVal, m, stage)
            }
            services.TourneyDB.tourney.updateStage(stage) match {
              case Right(updatedStage) =>
                Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
                info(s"Ergebnis für Spiel $gameNo übernommen.")
                render()
              case Left(err) =>
                error(s"StageInput: Failed to save accepted match result: ${err.msgCode}")
            }
        }
      }
    }

