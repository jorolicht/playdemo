package shared.format

import scala.collection.mutable.ArrayBuffer
import shared.model.*
import shared.basic.AppError
import shared.sport.{ EncounterCfg, Level, Sports }

/**
 * Begegnungen bei Mannschaftswettbewerben: Aufstellung, Spielfolge (Einzel/Doppel) und Stand der Begegnung.
 *
 * Ein Eintrag im Spielplan (`MEntry`) steht für die Begegnung zweier Mannschaften. Sein `sets` ist der Stand der
 * Begegnung (gewonnene Spiele), sein `winSets` kodiert die Spielweise (siehe [[winSetsCode]]). Das Spielsystem
 * wählt die Stage (`Stage.encSystem`, Spielweise `Stage.encVariant`). Aufstellungen und Einzelspiele liegen in
 * `Stage.encounters`.
 *
 * Wertung: Fehlt ein Spieler in der Aufstellung, ist das betroffene Spiel für diese Seite verloren. Gibt eine
 * Mannschaft eine leere Aufstellung ab (nicht angetreten), verliert sie alle Spiele.
 */
object TeamEncounter:

  /**
   * Spielsystem eines Wettbewerbstyps im aktuellen Wertungssystem ([[Sports.current]]).
   *
   * @param coTyp  Wettbewerbstyp
   * @param system Kennung des Systems (`Stage.encSystem`), leer = Standard des Formats
   * @return Spielsystem, None wenn der Typ keine Begegnungen kennt
   */
  def config(coTyp: CompTyp, system: String = ""): Option[EncounterCfg] = Sports.current.encounter(coTyp.formatId, system)

  /**
   * Spielsystem eines Wettbewerbs nach seinem Wertungssystem (`Competition.scoring`). Kadergröße (`teamSize`,
   * `maxPlayers`) ist für alle Systeme eines Formats gleich.
   *
   * @param comp   Wettbewerb
   * @param system Kennung des Systems, leer = Standard des Formats
   * @return Spielsystem, None wenn der Wettbewerb keine Begegnungen kennt
   */
  def configFor(comp: Competition, system: String = ""): Option[EncounterCfg] =
    Sports.get(comp.scoring).encounter(comp.typ.formatId, system)

  /**
   * Alle Spielsysteme eines Wettbewerbs.
   *
   * @param comp Wettbewerb
   * @return Systeme, leer bei Wettbewerben ohne Begegnungen
   */
  def systemsFor(comp: Competition): Seq[EncounterCfg] = Sports.get(comp.scoring).systems(comp.typ.formatId)

  /** true, wenn der Wettbewerbstyp in einem Wertungssystem mit Begegnungen gespielt wird (versionsunabhängig). */
  def isTeam(coTyp: CompTyp): Boolean = Sports.all.values.exists(_.encounter(coTyp.formatId).isDefined)

  /** Kennzahl-Zuschlag bei `fixed` mit Unentschieden. */
  private val DrawFlag    = 100
  /** Kennzahl-Zuschlag bei `fixed` mit Entscheidungsspiel. */
  private val DeciderFlag = 200
  /** Kennzahl-Zuschlag bei `bestOf` mit Unentschieden (zusammen mit [[DrawFlag]]). */
  private val BestOfFlag  = 1000

  /**
   * Kennzahl `winSets` eines Begegnungseintrags aus der Stufe der Begegnung.
   *
   *  - `bestOf`: `toWin` (positiv, z. B. 2 = bis 2 Siege); mit Unentschieden `-(1000 + 100 + toWin)`,
   *    z. B. -1108 = bis 8 Punkte, 7:7 unentschieden
   *  - `fixed`:  `-(count + 100 bei Unentschieden + 200 bei Entscheidungsspiel)`, z. B. -3 = alle 3 Spiele,
   *    -104 = 4 Spiele mit Unentschieden, -204 = 4 Spiele mit Entscheidungsspiel
   *
   * @param level Stufe der Begegnung
   * @return Kennzahl
   */
  def winSetsCode(level: Level): Int =
    if level.kind == Level.Fixed then
      -(level.count + (if level.allowDraw then DrawFlag else 0) + (if level.decider then DeciderFlag else 0))
    else if level.allowDraw then -(BestOfFlag + DrawFlag + level.toWin)
    else level.toWin

  /**
   * Stufe der Begegnung aus der Kennzahl `winSets` (Umkehrung von [[winSetsCode]]).
   *
   * @param code Kennzahl
   * @return Stufe `fixed` bei negativer Kennzahl, sonst `bestOf`
   */
  def levelFromCode(code: Int): Level =
    if code <= -BestOfFlag then
      Level("encounter", Level.BestOf, toWin = -code % DrawFlag, allowDraw = true)
    else if code < 0 then
      val c = -code
      Level("encounter", Level.Fixed, count = c % DrawFlag,
            allowDraw = (c / DrawFlag) % 2 == 1, decider = c / DeciderFlag >= 1)
    else Level("encounter", Level.BestOf, toWin = code)

  /**
   * `winSets` der Einträge einer Stage: bei Mannschaften die Kennzahl der Spielweise, sonst die Gewinnsätze.
   *
   * @param stage Stage
   * @param coTyp Wettbewerbstyp
   */
  def matchWinSets(stage: Stage, coTyp: CompTyp): Int = Sports.using(stage.scoring):
    config(coTyp, stage.encSystem).map(c => winSetsCode(levelOf(stage, c))).getOrElse(stage.noWinSets)

  /**
   * Stufe der Begegnung einer Stage (Spielweise der Stage, in K.-o.-Runden ohne Unentschieden).
   *
   * @param stage Stage
   * @param cfg   Spielsystem
   * @return Stufe der Begegnung
   */
  def levelOf(stage: Stage, cfg: EncounterCfg): Level = cfg.levelFor(stage.encVariant, stage.isKoStage)

  /**
   * Nummern der Entscheidungsspiele eines Systems.
   *
   * @param cfg Spielsystem
   * @return laufende Nummern (1 …) der Spiele mit `decider`
   */
  def deciderNos(cfg: EncounterCfg): Set[Int] =
    cfg.games.zipWithIndex.collect { case (g, i) if g.decider => i + 1 }.toSet

  /**
   * Stand einer Begegnung für die Anzeige, ohne die Begegnung zu verändern.
   *
   * @param stage Stage
   * @param cfg   Spielsystem
   * @param enc   Begegnung
   * @return Stand (Siege A, Siege B)
   */
  def preview(stage: Stage, cfg: EncounterCfg, enc: Encounter): (Int, Int) =
    evaluate(enc.copy(games = enc.games.map(_.copy())), levelOf(stage, cfg), stage.noWinSets, deciderNos(cfg))

  /**
   * Begegnung einer Spielnummer, wird bei Bedarf angelegt.
   *
   * @param stage  Stage
   * @param gameNo Spielnummer
   */
  def getOrCreate(stage: Stage, gameNo: Int): Encounter =
    stage.encounters.find(_.gameNo == gameNo).getOrElse {
      val e = Encounter(gameNo)
      stage.encounters += e
      e
    }

  /**
   * Prüft eine Aufstellung: nur Spieler der Mannschaft, Einzel mit höchstens einem, Doppel mit keinem oder zwei
   * verschiedenen Spielern, verschiedene Spieler in den Einzeln. Ein Einzelspieler darf zusätzlich im Doppel spielen.
   *
   * @param cfg    Spielsystem
   * @param side   0 = Heim, 1 = Gast
   * @param lineup Aufstellung
   * @param team   Mannschaft
   * @return Fehler oder Unit
   */
  def validateLineup(cfg: EncounterCfg, side: Int, lineup: Lineup, team: Team): Either[AppError, Unit] =
    val positions = cfg.sidePositions(side)
    val used      = lineup.players.filter { case (pos, ids) => positions.contains(pos) }
    val singles   = used.filter { case (pos, _) => !cfg.isDoublePos(side, pos) }.values.flatten.toSeq
    if used.values.flatten.exists(id => !team.players.contains(id)) then
      Left(AppError("err0260.lineup.player.not.in.team"))
    else if used.exists { case (pos, ids) => cfg.isDoublePos(side, pos) && ids.nonEmpty && (ids.length != 2 || ids.distinct.length != 2) } then
      Left(AppError("err0261.lineup.invalid.double"))
    else if used.exists { case (pos, ids) => !cfg.isDoublePos(side, pos) && ids.length > 1 } || singles.distinct.length != singles.length then
      Left(AppError("err0262.lineup.invalid.single"))
    else Right(())

  /**
   * Erzeugt die Spiele aus den Aufstellungen. Ergebnisse bleiben erhalten, wenn sich die Paarung eines Spiels nicht
   * geändert hat.
   *
   * @param enc Begegnung
   * @param cfg Spielsystem
   */
  def rebuild(enc: Encounter, cfg: EncounterCfg): Unit =
    val newGames = cfg.games.zipWithIndex.map { case (g, i) =>
      val snoA = enc.lineupA.sno(g.home, g.isDouble)
      val snoB = enc.lineupB.sno(g.away, g.isDouble)
      enc.games.find(_.no == i + 1) match
        case Some(old) if old.snoA == snoA && old.snoB == snoB => old
        case old => SubGame(i + 1, g.isDouble, snoA, snoB, playfield = old.map(_.playfield).getOrElse(""))
    }
    enc.games.clear()
    enc.games ++= newGames

  /**
   * Ist die Begegnung gestartet? Begegnungen aus der Zeit vor dem Start-Button gelten als gestartet, sobald ein
   * Spiel gespielt ist.
   *
   * @param enc Begegnung
   * @return true, wenn Ergebnisse eingegeben werden dürfen (sofern beide Aufstellungen abgegeben sind)
   */
  def isStarted(enc: Encounter): Boolean = enc.started || enc.games.exists(_.status == MEntry.MS_FIN)

  /**
   * Berechnet Status der Spiele und Stand der Begegnung.
   *
   *  - vor Abgabe beider Aufstellungen und vor dem Start ([[isStarted]]) sind alle Spiele blockiert
   *  - fehlt ein Spieler, gewinnt die andere Seite kampflos; fehlen beide, zählt das Spiel für niemanden
   *  - ist die Begegnung bei "bis n Siege" entschieden, entfallen die restlichen Spiele
   *  - ein Entscheidungsspiel wird nur bei Gleichstand nach allen übrigen Spielen gespielt und nur, wenn die Stufe
   *    es erlaubt (`decider`); bis die übrigen Spiele gewertet sind, ist es blockiert
   *
   * @param enc      Begegnung
   * @param level    Stufe der Begegnung
   * @param noWinSets Gewinnsätze eines Spiels
   * @return Stand der Begegnung (Siege A, Siege B)
   */
  def evaluate(enc: Encounter, level: Level, noWinSets: Int, deciders: Set[Int] = Set()): (Int, Int) =
    val ready = enc.lineupA.done && enc.lineupB.done && isStarted(enc)
    var a = 0
    var b = 0
    enc.games.foreach { g =>
      val alreadyDecided = level.kind == Level.BestOf && level.isValid((a, b))
      val isDecider      = deciders.contains(g.no)
      val othersDone     = enc.games.filterNot(x => deciders.contains(x.no)).forall(x => x.decided || x.status == MEntry.MS_SKIP)
      if !ready then
        g.status = MEntry.MS_BLOCK
      else if isDecider && (!level.decider || (othersDone && a != b)) then
        g.sets = (0, 0); g.result = ""
        g.status = MEntry.MS_SKIP
      else if isDecider && !othersDone then
        g.sets = (0, 0); g.result = ""
        g.status = MEntry.MS_BLOCK
      else if alreadyDecided then
        g.status = MEntry.MS_SKIP
      else if g.snoA.isNN || g.snoB.isNN then
        g.sets   = if g.snoA.isNN && g.snoB.isNN then (0, 0) else if g.snoA.isNN then (0, noWinSets) else (noWinSets, 0)
        g.result = ""
        g.status = MEntry.MS_FIX
      else if g.status == MEntry.MS_FIN && StageHelper.validSets(g.sets, noWinSets) then
        ()
      else
        g.sets   = (0, 0)
        g.result = ""
        g.status = MEntry.MS_READY

      if g.decided then
        if g.sets._1 > g.sets._2 then a += 1
        else if g.sets._2 > g.sets._1 then b += 1
    }
    (a, b)

  /**
   * Summe der Sätze und Bälle der gewerteten Spiele (für die Tabelle).
   *
   * @param enc       Begegnung
   * @param noWinSets Gewinnsätze eines Spiels
   * @return ((Sätze A, Sätze B), (Bälle A, Bälle B))
   */
  def totals(enc: Encounter, noWinSets: Int): ((Int, Int), (Int, Int)) =
    enc.games.filter(_.decided).foldLeft(((0, 0), (0, 0))) { case (((sa, sb), (ba, bb)), g) =>
      val balls = if g.result.isEmpty then (0, 0) else StageHelper.getBalls(g.result.split('·'), noWinSets)
      ((sa + g.sets._1, sb + g.sets._2), (ba + balls._1, bb + balls._2))
    }

  /**
   * Überträgt den Stand der Begegnung in den Eintrag im Spielplan. Ist die Begegnung entschieden, wird sie wie ein
   * Spielergebnis eingetragen (Tabelle, K.-o.-Weitergabe); wird sie durch eine Korrektur wieder offen, wird der
   * Eintrag zurückgesetzt, Tisch und Bemerkung bleiben.
   *
   * @param stage  Stage
   * @param coTyp  Wettbewerbstyp
   * @param enc    Begegnung
   * @return betroffene Spielnummern
   */
  def update(stage: Stage, coTyp: CompTyp, enc: Encounter): Either[AppError, List[Int]] = Sports.using(stage.scoring):
    config(coTyp, stage.encSystem) match
      case None => Left(AppError("err0263.encounter.no.team.competition"))
      case Some(cfg) =>
        val level = levelOf(stage, cfg)
        val score = evaluate(enc, level, stage.noWinSets, deciderNos(cfg))
        val m     = stage.getMatch(enc.gameNo)
        if level.isValid(score) then
          stage.inputMatch(enc.gameNo, score, "", m.info, m.playfield)
        else if stage.isKoStage && cfg.levelFor(stage.encVariant).allowDraw && koTieBreak(enc, level, score, stage.noWinSets).nonEmpty then
          stage.inputMatch(enc.gameNo, score, koTieBreak(enc, level, score, stage.noWinSets).get, m.info, m.playfield)
        else if m.finished then
          val (info, playfield) = (m.info, m.playfield)
          val res = stage.resetMatch(enc.gameNo)
          m.setInfo(info)
          m.setPlayfield(playfield)
          res
        else Right(List(enc.gameNo))

  /**
   * Entscheidung einer Begegnung "bis n Punkte", die in der K.-o.-Runde nach allen Spielen (n-1):(n-1) steht
   * (z. B. 7:7 bei der 4er-Mannschaft): zuerst die Satzdifferenz, dann die Balldifferenz aller gewerteten Spiele.
   *
   * @param enc       Begegnung
   * @param level     Stufe der Begegnung (K.-o., ohne Unentschieden)
   * @param score     Stand nach Punkten
   * @param noWinSets Gewinnsätze eines Spiels
   * @return [[MEntry.TieBreakA]] bzw. [[MEntry.TieBreakB]], None wenn kein Gleichstand nach allen Spielen vorliegt
   *         oder auch Sätze und Bälle gleich sind
   */
  def koTieBreak(enc: Encounter, level: Level, score: (Int, Int), noWinSets: Int): Option[String] =
    val allPlayed = enc.games.forall(g => g.decided || g.status == MEntry.MS_SKIP)
    if level.kind != Level.BestOf || score != (level.toWin - 1, level.toWin - 1) || !allPlayed then None
    else
      val ((sa, sb), (ba, bb)) = totals(enc, noWinSets)
      if sa != sb then Some(if sa > sb then MEntry.TieBreakA else MEntry.TieBreakB)
      else if ba != bb then Some(if ba > bb then MEntry.TieBreakA else MEntry.TieBreakB)
      else None

  /**
   * Setzt die Aufstellung einer Seite und erzeugt die Spiele neu. Nach dem Start der Begegnung ([[isStarted]]) ist
   * die Aufstellung nicht mehr änderbar.
   *
   * @param stage  Stage
   * @param comp   Wettbewerb
   * @param gameNo Spielnummer der Begegnung
   * @param side   0 = Mannschaft A, 1 = Mannschaft B
   * @param lineup neue Aufstellung (wird als abgegeben markiert)
   * @return betroffene Spielnummern
   */
  def setLineup(stage: Stage, comp: Competition, gameNo: Int, side: Int, lineup: Lineup): Either[AppError, List[Int]] = Sports.using(stage.scoring):
    config(comp.typ, stage.encSystem) match
      case None => Left(AppError("err0263.encounter.no.team.competition"))
      case Some(cfg) =>
        val m   = stage.getMatch(gameNo)
        val sno = if side == 0 then m.stNoA else m.stNoB
        comp.teams.find(_.id == sno.teamId) match
          case None => Left(AppError("err0264.encounter.team.not.found", sno.toString))
          case Some(team) =>
            validateLineup(cfg, side, lineup, team).flatMap { _ =>
              val enc = getOrCreate(stage, gameNo)
              if isStarted(enc) then Left(AppError("err0269.encounter.started", gameNo.toString))
              else
                val done = lineup.copy(done = true)
                if side == 0 then enc.lineupA = done else enc.lineupB = done
                rebuild(enc, cfg)
                update(stage, comp.typ, enc)
            }

  /**
   * Startet eine Begegnung: Danach sind die Eingaben der Spiele möglich. Beide Aufstellungen müssen abgegeben sein.
   *
   * @param stage  Stage
   * @param coTyp  Wettbewerbstyp
   * @param gameNo Spielnummer der Begegnung
   * @return betroffene Spielnummern
   */
  def start(stage: Stage, coTyp: CompTyp, gameNo: Int): Either[AppError, List[Int]] = Sports.using(stage.scoring):
    stage.encounters.find(_.gameNo == gameNo) match
      case Some(enc) if enc.lineupA.done && enc.lineupB.done =>
        enc.started = true
        update(stage, coTyp, enc)
      case _ => Left(AppError("err0268.encounter.lineup.missing", gameNo.toString))

  /**
   * Trägt das Ergebnis eines Spiels ein (leeres Ergebnis setzt es zurück).
   *
   * @param stage  Stage
   * @param coTyp  Wettbewerbstyp
   * @param gameNo Spielnummer der Begegnung
   * @param no     Nummer des Spiels in der Begegnung
   * @param sets   Satzstand
   * @param result Satzergebnisse in Schnelleingabe ("·"-getrennt)
   * @return betroffene Spielnummern
   */
  def inputGame(stage: Stage, coTyp: CompTyp, gameNo: Int, no: Int, sets: (Int, Int), result: String): Either[AppError, List[Int]] = Sports.using(stage.scoring):
    stage.encounters.find(_.gameNo == gameNo).flatMap(e => e.games.find(_.no == no).map(g => (e, g))) match
      case None => Left(AppError("err0265.encounter.game.not.found", s"$gameNo/$no"))
      case Some((enc, g)) =>
        if g.status == MEntry.MS_BLOCK || g.status == MEntry.MS_SKIP || g.status == MEntry.MS_FIX then
          Left(AppError("err0266.encounter.game.not.playable", s"$gameNo/$no"))
        else if result.isEmpty then
          g.sets = (0, 0); g.result = ""; g.status = MEntry.MS_READY
          update(stage, coTyp, enc)
        else if !StageHelper.validSets(sets, stage.noWinSets) then
          Left(AppError("err0267.encounter.game.invalid.result", s"$gameNo/$no"))
        else
          g.sets = sets; g.result = result; g.status = MEntry.MS_FIN
          update(stage, coTyp, enc)

  /**
   * Löscht alle gespielten Ergebnisse einer Begegnung und nimmt den Start zurück: Die Aufstellungen bleiben, können
   * aber wieder geändert werden; danach muss die Begegnung neu gestartet werden.
   *
   * @param stage  Stage
   * @param coTyp  Wettbewerbstyp
   * @param gameNo Spielnummer der Begegnung
   * @return betroffene Spielnummern
   */
  def clearResults(stage: Stage, coTyp: CompTyp, gameNo: Int): Either[AppError, List[Int]] = Sports.using(stage.scoring):
    stage.encounters.find(_.gameNo == gameNo) match
      case None => stage.resetMatch(gameNo)
      case Some(enc) =>
        enc.games.foreach { g => if g.status == MEntry.MS_FIN then { g.sets = (0, 0); g.result = ""; g.status = MEntry.MS_READY } }
        enc.started = false
        update(stage, coTyp, enc).map { res => stage.getMatch(gameNo).startTime = ""; res }
