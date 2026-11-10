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
  val BtnDemoScores:     HtmlId = genId(name)

  // Mannschaften: Aufstellung (Suffix gameNo_side), Auf-/Zuklappen (gameNo), Spiel speichern/löschen (gameNo_no)
  val LineupBtn:   HtmlId = genId(name)
  val TngBtn:      HtmlId = genId(name)
  val TeamSaveBtn: HtmlId = genId(name)
  val TeamDelBtn:  HtmlId = genId(name)

  /** Aufgeklappte Begegnungen (Stage-Id, Spielnummer), bleibt beim Neuzeichnen erhalten. */
  var expanded: Set[(Int, Int)] = Set()

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
      case `BtnDemoScores` =>
        setRandomScores()
      case id if id.id.startsWith(LineupBtn.id + "-") =>
        val Array(gameNo, side) = elem.id.substring(LineupBtn.id.length + 1).split('_').map(_.toInt)
        editLineup(gameNo, side)
      case id if id.id.startsWith(TngBtn.id + "-") =>
        toggleEncounter(elem.id.substring(TngBtn.id.length + 1).toInt)
      case id if id.id.startsWith(TeamSaveBtn.id + "-") =>
        val Array(gameNo, no) = elem.id.substring(TeamSaveBtn.id.length + 1).split('_').map(_.toInt)
        saveSubGame(gameNo, no)
      case id if id.id.startsWith(TeamDelBtn.id + "-") =>
        val Array(gameNo, no) = elem.id.substring(TeamDelBtn.id.length + 1).split('_').map(_.toInt)
        deleteSubGame(gameNo, no)
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
        case Some(p) if sno.isTeam => p.name
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
   * Name eines Spielers bzw. Doppels in einem Spiel einer Begegnung (Startnummer aus der Aufstellung).
   *
   * @param sno Startnummer: Einzel, Doppel oder `[0]` für eine nicht besetzte Position
   * @return Nachname, "Nachname/Nachname" oder Hinweis "fehlt"
   */
  def formatPlayerSno(sno: SNO): String =
    val players = services.TourneyDB.tourney.players
    def last(id: PlayerId) = players.find(_.id == id).map(_.lastName).getOrElse(s"[${id.value}]")
    if (sno.isNN) gM("+player_missing")
    else if (sno.isDouble) { val (a, b) = sno.doubleId; s"${last(a)}/${last(b)}" }
    else players.find(_.id == sno.singleId).map(_.displayName).getOrElse(sno.toString)

  /** Wettbewerb und Spielsystem, wenn die gewählte Stage zu einem Mannschaftswettbewerb gehört. */
  private def teamContext: Option[(Stage, Competition, shared.sport.EncounterCfg)] =
    for
      stage <- Global.currentSelection.stage
      comp  <- Global.currentSelection.competition
      cfg   <- TeamEncounter.config(comp.typ)
    yield (stage, comp, cfg)

  /** true, wenn Ergebnisse der Stage geändert werden dürfen. */
  private def canEdit(stage: Stage, comp: Competition): Boolean =
    comp.status != CompStatus.FIN && stage.status == StageStatus.EIN && base.Global.hasTourneyAccess(services.TourneyDB.tourney)

  /**
   * Speichert die Stage nach einer Änderung an einer Begegnung und zeichnet die Seite neu.
   *
   * @param stage Stage
   * @param res   Ergebnis der Änderung
   */
  private def saveTeamChange(stage: Stage, res: Either[AppError, List[Int]]): Unit =
    res match
      case Left(err) => DlgMsgbox.showError(err, gM("+team_error"))
      case Right(_) =>
        services.TourneyDB.tourney.updateStage(stage) match
          case Right(updated) =>
            Global.currentSelection = Global.currentSelection.copy(stage = Some(updated))
            render()
          case Left(err) => error(s"StageInput: Failed to save encounter: ${err.msgCode}")

  /**
   * Klappt die Spiele einer Begegnung auf oder zu.
   *
   * @param gameNo Spielnummer der Begegnung
   */
  def toggleEncounter(gameNo: Int): Unit =
    Global.currentSelection.stage.foreach { stage =>
      val key = (stage.id.value, gameNo)
      val open = !expanded.contains(key)
      expanded = if (open) expanded + key else expanded - key
      val row  = dom.document.getElementById(s"enc-sub-$gameNo")
      val icon = dom.document.getElementById(s"tng-icon-$gameNo")
      if (row != null) { if (open) row.classList.remove("d-none") else row.classList.add("d-none") }
      if (icon != null) icon.setAttribute("class", if (open) "bi bi-caret-down-fill" else "bi bi-caret-right-fill")
    }

  /**
   * Öffnet die Aufstellung einer Mannschaft und erzeugt danach die Spiele der Begegnung neu.
   *
   * @param gameNo Spielnummer der Begegnung
   * @param side   0 = Mannschaft A, 1 = Mannschaft B
   */
  def editLineup(gameNo: Int, side: Int): Unit =
    teamContext.foreach { case (stage, comp, cfg) =>
      if (canEdit(stage, comp)) {
        val m    = stage.getMatch(gameNo)
        val sno  = if (side == 0) m.stNoA else m.stNoB
        val enc  = stage.encounters.find(_.gameNo == gameNo)
        comp.teams.find(_.id == sno.teamId).foreach { team =>
          dialogs.DlgLineup.show(cfg, side, team, services.TourneyDB.tourney.players.toSeq, enc.map(_.lineup(side)).getOrElse(Lineup())).map {
            case Right(lineup) =>
              expanded = expanded + ((stage.id.value, gameNo))
              saveTeamChange(stage, TeamEncounter.setLineup(stage, comp, gameNo, side, lineup))
            case Left(_) => ()
          }
        }
      }
    }

  /**
   * Liest die Satzeingaben eines Spiels einer Begegnung.
   *
   * @param gameNo Spielnummer der Begegnung
   * @param no     Nummer des Spiels in der Begegnung
   * @param winSets Gewinnsätze
   * @return (gültig und entschieden, Satzstand, Ergebnis in Schnelleingabe)
   */
  private def readSubInputs(gameNo: Int, no: Int, winSets: Int): (Boolean, (Int, Int), String) =
    val inputs = (1 to winSets * 2 - 1).map { setNo =>
      val el = dom.document.getElementById(s"tin_${gameNo}_${no}_$setNo").asInstanceOf[dom.html.Input]
      if (el != null) el.value.trim else ""
    }.filter(_.nonEmpty)
    val parsed = inputs.map(parseSetInput)
    var (a, b) = (0, 0)
    var decidedAt = -1
    parsed.zipWithIndex.foreach { case (r, i) =>
      r.foreach { case (x, y) => if (decidedAt < 0) { if (x > y) a += 1 else b += 1; if (a == winSets || b == winSets) decidedAt = i } }
    }
    val ok = parsed.forall(_.isDefined) && decidedAt == inputs.length - 1
    (ok, (a, b), inputs.mkString("·"))

  /**
   * Prüft die Eingaben eines Spiels einer Begegnung und schaltet den Speichern-Button.
   *
   * @param stage  Stage
   * @param gameNo Spielnummer der Begegnung
   * @param no     Nummer des Spiels
   */
  private def checkSubValidity(stage: Stage, gameNo: Int, no: Int): Unit =
    val (ok, (a, b), result) = readSubInputs(gameNo, no, stage.noWinSets)
    val stored  = stage.encounters.find(_.gameNo == gameNo).flatMap(_.games.find(_.no == no)).map(_.result).getOrElse("")
    val saveBtn = dom.document.getElementById(s"${TeamSaveBtn.id}-${gameNo}_$no").asInstanceOf[dom.html.Button]
    val badge   = dom.document.getElementById(s"tsets_${gameNo}_$no").asInstanceOf[dom.html.Element]
    if (saveBtn != null) saveBtn.disabled = !(ok && result != stored)
    if (badge != null) {
      badge.textContent = if (a + b > 0) s"$a:$b" else ""
      badge.className = if (ok) "badge bg-success text-white" else "badge bg-light text-dark border"
    }

  /**
   * Speichert das Ergebnis eines Spiels einer Begegnung.
   *
   * @param gameNo Spielnummer der Begegnung
   * @param no     Nummer des Spiels
   */
  def saveSubGame(gameNo: Int, no: Int): Unit =
    teamContext.foreach { case (stage, comp, _) =>
      if (canEdit(stage, comp)) {
        val (ok, sets, result) = readSubInputs(gameNo, no, stage.noWinSets)
        if (ok) {
          val m = stage.getMatch(gameNo)
          if (m.startTime == null || m.startTime.trim.isEmpty) m.startTime = nowTimestamp()
          val res = TeamEncounter.inputGame(stage, comp.typ, gameNo, no, sets, result)
          if (stage.getMatch(gameNo).finished) {
            stage.getMatch(gameNo).endTime = nowTimestamp()
            sendMatchboardUpdate("finish", m.playfield, m, stage)
          }
          saveTeamChange(stage, res)
        }
      }
    }

  /**
   * Löscht das Ergebnis eines Spiels einer Begegnung.
   *
   * @param gameNo Spielnummer der Begegnung
   * @param no     Nummer des Spiels
   */
  def deleteSubGame(gameNo: Int, no: Int): Unit =
    teamContext.foreach { case (stage, comp, _) =>
      if (canEdit(stage, comp)) saveTeamChange(stage, TeamEncounter.inputGame(stage, comp.typ, gameNo, no, (0, 0), ""))
    }

  /**
   * Standardaufstellung für Demo-Ergebnisse: Stammspieler in die Einzel, die ersten beiden ins Doppel.
   *
   * @param cfg  Spielsystem
   * @param side 0 = Heim, 1 = Gast
   * @param team Mannschaft
   */
  private def defaultLineup(cfg: shared.sport.EncounterCfg, side: Int, team: Team): Lineup =
    val singles = cfg.sidePositions(side).filterNot(cfg.isDoublePos(side, _))
    val doubles = cfg.sidePositions(side).filter(cfg.isDoublePos(side, _))
    Lineup((singles.zip(team.players).map { case (pos, id) => pos -> Seq(id) } ++
            doubles.map(pos => pos -> team.players.take(2))).toMap)

  /**
   * Trägt zufällige Ergebnisse in offene Begegnungen ein (Demo).
   *
   * @param stage Stage
   * @param comp  Wettbewerb
   * @param cfg   Spielsystem
   * @param roundOpt nur diese Runde
   * @return true, wenn mindestens eine Begegnung geändert wurde
   */
  private def setRandomTeamScores(stage: Stage, comp: Competition, cfg: shared.sport.EncounterCfg, roundOpt: Option[Int]): Boolean =
    var changed = false
    stage.matches.filter(m => m != null && !m.finished && m.stNoA.isTeam && m.stNoB.isTeam && roundOpt.forall(_ == m.round)).foreach { m =>
      for (side <- 0 to 1) {
        val done = stage.encounters.find(_.gameNo == m.gameNo).exists(_.lineup(side).done)
        if (!done) {
          val sno = if (side == 0) m.stNoA else m.stNoB
          comp.teams.find(_.id == sno.teamId).foreach { t =>
            TeamEncounter.setLineup(stage, comp, m.gameNo, side, defaultLineup(cfg, side, t))
          }
        }
      }
      stage.encounters.find(_.gameNo == m.gameNo).foreach { e =>
        e.games.filter(_.status == MEntry.MS_READY).map(_.no).foreach { no =>
          if (e.games.find(_.no == no).exists(_.status == MEntry.MS_READY)) {
            val (sets, result) = generateRandomMatchResult(if (stage.noWinSets > 0) stage.noWinSets else 3)
            TeamEncounter.inputGame(stage, comp.typ, m.gameNo, no, sets, result)
            changed = true
          }
        }
      }
      if (stage.getMatch(m.gameNo).finished) {
        val fm = stage.getMatch(m.gameNo)
        if (fm.startTime == null || fm.startTime.trim.isEmpty) fm.startTime = nowTimestamp()
        fm.endTime = nowTimestamp()
      }
    }
    changed

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
              if (m.startTime == null || m.startTime.trim.isEmpty) {
                m.startTime = nowTimestamp()
              }
              m.endTime = nowTimestamp()
              sendMatchboardUpdate("finish", playfieldVal, m, stage)
            }
            services.TourneyDB.tourney.updateStage(stage) match {
              case Right(updatedStage) =>
                Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))

                // Reset the info button class in the DOM in case it was blinking
                val infoBtn = dom.document.getElementById(s"${InfoMatchBtn.id}-$gameNo").asInstanceOf[dom.html.Button]
                if (infoBtn != null) {
                  infoBtn.className = "btn btn-outline-info"
                }

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
            val reset =
              if (TeamEncounter.isTeam(comp.typ)) TeamEncounter.clearResults(stage, comp.typ, gameNo)
              else stage.resetMatch(gameNo)
            reset match {
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
   * Prüft, ob sich die Anwendung im Demo-Modus befindet oder ein Demo-Turnier geladen ist.
   *
   * @return True, falls Demo-Modus aktiv oder der Turniername auf eine Demo hinweist.
   */
  def isDemo: Boolean =
    base.Global.isDemoMode || (services.TourneyDB.tourney != null && (
      services.TourneyDB.tourney.name.startsWith("Demo:") ||
      services.TourneyDB.tourney.name.startsWith(base.Messages.gM("demo.namePrefix")) ||
      services.TourneyDB.tourney.name.toLowerCase.contains("demo")
    ))

  /**
   * Generiert ein zufälliges Satzergebnis im Tischtennis-Format für die Schnelleingabe.
   *
   * @param aWinsSet True wenn Spieler A den Satz gewinnt, False wenn Spieler B gewinnt.
   * @return String im Schnelleingabe-Format (z.B. "7" für 11:7 oder "-8" für 8:11).
   */
  def randomSetInput(aWinsSet: Boolean): String =
    val isOvertime = scala.util.Random.nextDouble() < 0.15
    if (aWinsSet) {
      if (isOvertime) {
        val bScore = 9 + scala.util.Random.nextInt(4)
        s"$bScore"
      } else {
        val bScore = scala.util.Random.nextInt(10)
        s"$bScore"
      }
    } else {
      if (isOvertime) {
        val aScore = 9 + scala.util.Random.nextInt(4)
        s"-$aScore"
      } else {
        val aScore = scala.util.Random.nextInt(10)
        s"-$aScore"
      }
    }

  /**
   * Erzeugt ein vollständiges zufälliges Match-Ergebnis basierend auf den Gewinnsätzen.
   *
   * @param winSets Anzahl der Gewinnsätze (z.B. 3 für Best-of-5).
   * @return Ein Tupel bestehend aus Satzverhältnis ((aWins, bWins)) und dem formatierten Ergebnisstring mit '·'.
   */
  def generateRandomMatchResult(winSets: Int): ((Int, Int), String) =
    val setInputs = scala.collection.mutable.ArrayBuffer[String]()
    var aWins = 0
    var bWins = 0
    while (aWins < winSets && bWins < winSets) {
      val aWinsSet = scala.util.Random.nextBoolean()
      if (aWinsSet) aWins += 1 else bWins += 1
      setInputs += randomSetInput(aWinsSet)
    }
    ((aWins, bWins), setInputs.mkString("·"))

  /**
   * Trägt zufällige Spielergebnisse für noch ungespielte, spielbereite Begegnungen der aktuellen Phase ein.
   * Propagiert Sieger bei K.o.-Systemen automatisch in Folgerunden und wiederholt die Vergabe,
   * bis alle determinierten Spiele abgeschlossen sind. Aktualisiert die Spieldatenbank und rendert die Seite neu.
   *
   * @param roundOpt Optional eine konkrete Runde; None für alle offenen Runden.
   */
  def setRandomScores(roundOpt: Option[Int] = None): Unit =
    Global.currentSelection.stage.foreach { stage =>
      val compOpt = Global.currentSelection.competition.orElse(
        services.TourneyDB.tourney.competitions.find(c => c != null && c.id == stage.coId)
      )
      val isFin = compOpt.exists(_.status == CompStatus.FIN) || stage.status != StageStatus.EIN || !base.Global.hasTourneyAccess(services.TourneyDB.tourney)
      if (isFin) {
        debug("StageInput: Cannot generate demo scores because stage is not in EIN status, competition is finalized, or user has no write access.")
      } else {
        val winSets = if (stage.noWinSets > 0) stage.noWinSets else 3
        val teamCfg = compOpt.flatMap(c => TeamEncounter.config(c.typ).map(cfg => (c, cfg)))
        var changed = true
        var loopCount = 0
        while (changed && loopCount < 50) {
          changed = false
          loopCount += 1
          teamCfg.foreach { case (c, cfg) => changed = setRandomTeamScores(stage, c, cfg, roundOpt) }
          val unplayed = if (teamCfg.isDefined) ArrayBuffer.empty[MEntry] else stage.matches.filter(m =>
            m != null && !m.finished && m.result.trim.isEmpty &&
            m.status != MEntry.MS_FIX &&
            !m.stNoA.isNN && !m.stNoB.isNN &&
            !m.stNoA.isBye && !m.stNoB.isBye &&
            roundOpt.forall(_ == m.round)
          )
          if (unplayed.nonEmpty) {
            unplayed.foreach { m =>
              val ((aWins, bWins), resultStr) = generateRandomMatchResult(winSets)
              val playfield = if (m.playfield != null && m.playfield.trim.nonEmpty) m.playfield.trim else (1 + ((m.gameNo - 1) % 12)).toString
              stage.inputMatch(m.gameNo, (aWins, bWins), resultStr, "", playfield) match {
                case Right(_) =>
                  if (m.startTime == null || m.startTime.trim.isEmpty) {
                    m.startTime = nowTimestamp()
                  }
                  m.endTime = nowTimestamp()
                  sendMatchboardUpdate("finish", playfield, m, stage)
                  changed = true
                case Left(err) =>
                  error(s"StageInput: setRandomScores inputMatch failed for gameNo ${m.gameNo}: ${err.msgCode}")
              }
            }
          }
        }

        services.TourneyDB.tourney.updateStage(stage) match {
          case Right(updatedStage) =>
            Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
            render()
          case Left(err) =>
            error(s"StageInput: Failed to update stage in database: ${err.msgCode}")
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

    // Mannschaften: Satzeingaben der Spiele einer Begegnung
    val teamInputs = dom.document.querySelectorAll(".team-set-input")
    for (i <- 0 until teamInputs.length) {
      val inputEl = teamInputs.item(i).asInstanceOf[dom.html.Input]
      val gameNo  = inputEl.getAttribute("data-game").toInt
      val no      = inputEl.getAttribute("data-sub").toInt
      inputEl.oninput = (_: dom.Event) => checkSubValidity(stage, gameNo, no)
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
    
    val isDifferent = Global.currentSelection.stage.flatMap(_.matches.find(_.gameNo == gameNo)).map { m =>
      val parsedBalls = scala.collection.mutable.ArrayBuffer[String]()
      inputs.filter(_.nonEmpty).foreach { inp =>
        parseSetInput(inp).foreach { _ =>
          parsedBalls += inp
        }
      }
      val currentResultStr = parsedBalls.mkString("·")
      currentResultStr != m.result
    }.getOrElse(true)

    val canSave = isValid && winnerReached && nonOptInputs.length == setsPlayed && isDifferent

    if (isValid && winnerReached && nonOptInputs.length == setsPlayed) {
      if (saveBtn != null) saveBtn.disabled = !canSave
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
   * Liest die Schnelleingabe eines Satzes laut Wertungssystem der Sportart.
   * Tischtennis: -5 steht für 5:11, 10 für 12:10.
   */
  private def parseSetInput(input: String): Option[(Int, Int)] = StageHelper.setLevel.parseShort(input)

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
                val (setsA, setsB) = StageHelper.byeSets(m.winSets, m.stNoA.isBye)
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

  private def formatTimestamp(ts: String): String =
    if (ts == null || ts.trim.isEmpty) "-"
    else {
      val t = ts.trim
      if (t.length == 14) {
        val hh = t.substring(8, 10)
        val mm = t.substring(10, 12)
        s"$hh:$mm Uhr"
      } else if (t.length == 8) {
        val yyyy = t.substring(0, 4)
        val mm = t.substring(4, 6)
        val dd = t.substring(6, 8)
        s"$dd.$mm.$yyyy"
      } else t
    }

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
    if (t == null || base.Global.isDemoMode || base.Global.isLocalMode || t.wpId == 999999 || t.wpId <= 0) return

    val slugName = t.slug.split('/').lastOption.getOrElse(t.slug)
    val slugParam = s"${slugName.trim}-${t.wpId}"
    
    t.competitions.filter(_ != null).find(_.id == stage.coId).foreach { comp =>
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

          val startTimeDisplay = formatTimestamp(m.startTime)
          val endTimeDisplay = formatTimestamp(m.endTime)
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

          DlgMsgbox.show(body, s"Spiel-Information (Spiel ${m.gameNo})", buttons).map {
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
        
        val winSets = stage.noWinSets
        val maxSets = winSets * 2 - 1
        
        // 1. Fill the HTML input elements with the short format values
        for (setIdx <- 1 to maxSets) {
          val el = dom.document.getElementById(s"input_${gameNo}_$setIdx").asInstanceOf[dom.html.Input]
          if (el != null) {
            if (setIdx <= sets.length) {
              val set = sets(setIdx - 1)
              val pts = set.split(":").map(_.trim.toInt)
              if (pts.length == 2) {
                val aPoints = pts(0)
                val bPoints = pts(1)
                el.value = StageHelper.setLevel.formatShort((aPoints, bPoints))
              } else {
                el.value = ""
              }
            } else {
              el.value = ""
            }
          }
        }
        
        // 2. Run the validation logic so that the Save button is enabled and sets badge is updated
        checkMatchValidity(gameNo, winSets)

        // 3. Reset the info button class in the DOM
        val infoBtn = dom.document.getElementById(s"${InfoMatchBtn.id}-$gameNo").asInstanceOf[dom.html.Button]
        if (infoBtn != null) {
          infoBtn.className = "btn btn-outline-info"
        }
        
        info(s"Ergebnis für Spiel $gameNo in Eingabefelder übertragen. Bitte mit dem grünen Haken-Button speichern.")
      }
    }

