package shared.format

import scala.collection.mutable.ArrayBuffer
import shared.model.*
import shared.sport.Sports

/**
 * Doppeltes K.-o.-System als zwei gekoppelte Stages (siehe doc/DoubleModes.md):
 * Gewinnerrunde (WB, normale K.-o.-Runde ohne Spiel um Platz 3) und Verliererrunde (LB) mit Finale.
 *
 * Feldgröße N = 2^k (k Runden in der WB). Die LB hat 2·(k−1) Runden mit N−2 Spielen:
 *  - ungerade LB-Runde t (intern): Sieger der vorigen LB-Runde paarweise (t = 1: Verlierer der WB-Runde 1),
 *  - gerade LB-Runde t (Einsteiger): Sieger der LB-Runde t−1 (Position A) gegen Verlierer der WB-Runde t/2+1 (B).
 * Danach das Finale (WB-Sieger gegen LB-Sieger), optional mit Rückspiel.
 *
 * Spiele der LB sind `MEntryKo`: `winPos` zeigt innerhalb der LB auf das nächste Spiel, `looPos` bleibt leer.
 * Die Zuordnung WB → LB steckt nur in [[loserTarget]]. Rundennummern der LB: 100 + t, Finale [[RoundFinal]],
 * Rückspiel [[RoundReset]].
 */
object DoubleElimination:

  /** Rundennummer des Finales (WB-Sieger gegen LB-Sieger). */
  val RoundFinal = 150
  /** Rundennummer des Final-Rückspiels. */
  val RoundReset = 151

  /** Anzahl der WB-Runden k für Feldgröße N. */
  def wbRounds(size: Int): Int = KoRound.getNoRounds(size)

  /** Anzahl der LB-Runden 2·(k−1) für Feldgröße N. */
  def lbRounds(size: Int): Int = scala.math.max(0, 2 * (wbRounds(size) - 1))

  /** Anzahl der Spiele in LB-Runde t (1-basiert). */
  def lbMatchesIn(size: Int, t: Int): Int = size >> ((t + 1) / 2 + 1)

  /** Spielnummer des ersten Spiels der LB-Runde t (1-basiert). */
  def lbFirstGameNo(size: Int, t: Int): Int = (1 until t).map(lbMatchesIn(size, _)).sum + 1

  /** Spielnummer des Finales in der LB-Stage. */
  def finalGameNo(size: Int): Int = lbFirstGameNo(size, lbRounds(size) + 1)

  /** LB-Runde t eines LB-Spiels (Rundennummer 100 + t), 0 für Finale/Rückspiel. */
  def lbRoundOf(m: MEntry): Int = if m.round > 100 && m.round < RoundFinal then m.round - 100 else 0

  /**
   * Ziel in der Verliererrunde für den Verlierer eines WB-Spiels.
   *
   * WB-Runde 1 (Rundennummer k) füllt LB-Runde 1 paarweise; WB-Runde j ≥ 2 füllt die Einsteiger-Runde 2·(j−1) auf
   * Position B, gekreuzt: in ungeraden Einsteiger-Runden in umgekehrter Reihenfolge, in geraden mit getauschten
   * Hälften, damit Spieler aus derselben WB-Hälfte nicht sofort wieder aufeinandertreffen.
   *
   * @param size    Feldgröße N der WB
   * @param wbRound Rundennummer des WB-Spiels (k = erste Runde … 1 = Finale)
   * @param maNo    Spielnummer innerhalb der WB-Runde (1-basiert)
   * @return (LB-Spielnummer, Position 0 = A / 1 = B); der Verlierer des WB-Finales geht in die letzte LB-Runde
   */
  def loserTarget(size: Int, wbRound: Int, maNo: Int): (Int, Int) =
    val k = wbRounds(size)
    val j = k - wbRound + 1                       // WB-Runde 1-basiert ab der ersten Runde
    if j == 1 then
      (lbFirstGameNo(size, 1) + (maNo - 1) / 2, (maNo + 1) % 2)
    else
      val t = 2 * (j - 1)
      val cnt = lbMatchesIn(size, t)
      val d = j - 1                               // Nummer der Einsteiger-Runde
      val x =
        if cnt == 1 then 1
        else if d % 2 == 1 then cnt - maNo + 1
        else if maNo <= cnt / 2 then maNo + cnt / 2 else maNo - cnt / 2
      (lbFirstGameNo(size, t) + x - 1, 1)

  /** Ziel des Siegers eines LB-Spiels (LB-Spielnummer, Position); Sieger der letzten LB-Runde → Finale B. */
  private def lbWinTarget(size: Int, t: Int, x: Int): (Int, Int) =
    val last = lbRounds(size)
    if t == last then (finalGameNo(size), 1)
    else if t % 2 == 1 then (lbFirstGameNo(size, t + 1) + x - 1, 0)          // intern → Einsteiger-Runde, Position A
    else (lbFirstGameNo(size, t + 1) + (x - 1) / 2, (x + 1) % 2)             // Einsteiger → interne Runde, paarweise

  /**
   * Erzeugt alle Spiele der Verliererrunde (Teilnehmer offen) inklusive Finale und ggf. Rückspiel.
   *
   * @param lbId    ID der LB-Stage
   * @param wb      Gewinnerrunde (Matches initialisiert)
   * @param reset   Finale mit Rückspiel
   * @return Spiele der LB in Spielnummern-Reihenfolge
   */
  def initLbMatches(lbId: StageId, wb: Stage, reset: Boolean): Seq[MEntryKo] =
    val size    = wbSize(wb)
    val first   = wb.matches.headOption
    val coTyp   = first.map(_.coTyp).getOrElse(CompTyp.UNKN)
    val winSets = first.map(_.winSets).getOrElse(wb.noWinSets)
    def entry(gameNo: Int, round: Int, maNo: Int, winPos: String) =
      MEntryKo(wb.coId, coTyp, lbId, StageFormat.KO, gameNo, SNO.nn, SNO.nn, round, maNo, winPos, "",
               "", "", "", "", MEntry.MS_MISS, (0, 0), winSets, "")
    val buf = ArrayBuffer[MEntryKo]()
    for t <- 1 to lbRounds(size); x <- 1 to lbMatchesIn(size, t) do
      val (g, p) = lbWinTarget(size, t, x)
      buf += entry(lbFirstGameNo(size, t) + x - 1, 100 + t, x, s"${g}·0·0·${p}")
    buf += entry(finalGameNo(size), RoundFinal, 1, "")
    if reset then buf += entry(finalGameNo(size) + 1, RoundReset, 1, "")
    buf.toSeq

  /** Feldgröße N der WB. */
  def wbSize(wb: Stage): Int = wb.data match
    case StageData.KnockoutStage(ko) => ko.size
    case _                           => 0

  /**
   * Trägt die Verlierer der WB und den WB-Sieger in die LB ein und gibt Sieger innerhalb der LB weiter.
   * Freilose werden durchgereicht (Spiel MS_FIX, Gegner rückt weiter). Ändert sich ein bereits belegter Platz
   * (Korrektur in der WB), werden das betroffene LB-Spiel und alle davon abhängigen LB-Spiele zurückgesetzt.
   * Idempotent: ein zweiter Aufruf ändert nichts.
   *
   * @param wb Gewinnerrunde
   * @param lb Verliererrunde (wird verändert)
   * @return true, wenn sich in der LB etwas geändert hat
   */
  def propagate(wb: Stage, lb: Stage): Boolean = Sports.using(lb.scoring):
    val size = wbSize(wb)
    var changed = false
    def lbMatch(g: Int): Option[MEntry] = if lb.existsMatchNo(g) then Some(lb.getMatch(g)) else None

    def setSlot(g: Int, pos: Int, sno: SNO): Unit =
      lbMatch(g).foreach { m =>
        val cur = if pos == 0 then m.stNoA else m.stNoB
        if cur != sno then
          if m.finished || m.status == MEntry.MS_RUN then lb.resetMatch(g, pos == 0, pos == 1)
          m.setPant(pos, sno)
          changed = true
      }

    // 1. Verlierer der WB in die LB, WB-Sieger ins Finale
    wb.matches.foreach {
      case m: MEntryKo if m.round >= 1 =>
        val (g, p) = loserTarget(size, m.round, m.maNo)
        setSlot(g, p, if m.finished then m.getLooser() else SNO.nn)
        if m.round == 1 then setSlot(finalGameNo(size), 0, if m.finished then m.getWinner() else SNO.nn)
      case _ =>
    }

    // 2. innerhalb der LB in Spielnummern-Reihenfolge: Freilose festsetzen, Sieger weitergeben, Status setzen
    lb.matches.foreach {
      case m: MEntryKo if m.round != RoundReset =>
        val aSet = !m.stNoA.isNN
        val bSet = !m.stNoB.isNN
        if aSet && bSet && (m.stNoA.isBye || m.stNoB.isBye) then
          val sets = StageHelper.byeSets(m.winSets, m.stNoA.isBye)
          if m.status != MEntry.MS_FIX || m.sets != sets then
            m.setSets(sets); m.setResult(""); m.setStatus(MEntry.MS_FIX); changed = true
        else if !m.finished && m.status != MEntry.MS_RUN then
          val target = if aSet && bSet then MEntry.MS_READY else MEntry.MS_MISS
          if m.status != target then { m.setStatus(target); changed = true }
        val (g, p) = m.getWinPos()
        if g > 0 then setSlot(g, p, if m.finished then m.getWinner() else SNO.nn)
      case _ =>
    }

    // 3. Rückspiel: nur nötig, wenn der LB-Sieger (Position B) das erste Finale gewinnt
    lbMatch(finalGameNo(size) + 1).filter(_.round == RoundReset).foreach { r =>
      val f = lb.getMatch(finalGameNo(size))
      val (a, b) =
        if !f.finished then (SNO.nn, SNO.nn)
        else if f.getWinner() == f.stNoB then (f.stNoA, f.stNoB)
        else (SNO.bye(0), SNO.bye(1))                  // nicht erforderlich
      setSlot(r.gameNo, 0, a)
      setSlot(r.gameNo, 1, b)
      if a.isBye then
        val sets = StageHelper.byeSets(r.winSets, true)
        if r.status != MEntry.MS_FIX then { r.setSets(sets); r.setResult(""); r.setStatus(MEntry.MS_FIX); changed = true }
      else if !r.finished && r.status != MEntry.MS_RUN then
        val target = if a.isNN then MEntry.MS_MISS else MEntry.MS_READY
        if r.status != target then { r.setStatus(target); changed = true }
    }

    if changed then lb.updateStatus()
    changed

  /** Ist das Rückspiel ein Platzhalter (nicht erforderlich, weil der WB-Sieger das erste Finale gewonnen hat)? */
  def resetNotNeeded(m: MEntry): Boolean = m.round == RoundReset && m.stNoA.isBye && m.stNoB.isBye

  /**
   * Platzierungen des Doppelten K.-o.: 1/2 aus dem (letzten gespielten) Finale, 3 Verlierer der letzten LB-Runde,
   * 4 Verlierer der vorletzten, danach je LB-Runde gleich platziert (5–6, 7–8, …). Nur feststehende Plätze.
   *
   * @param lb Verliererrunde mit Finale
   * @return (Startnummer, Platz), nach Platz sortiert
   */
  def placements(lb: Stage): Seq[(SNO, Int)] =
    val size = lb.data match
      case StageData.KnockoutStage(ko) => ko.size
      case _                           => 0
    val res = ArrayBuffer[(SNO, Int)]()
    def real(s: SNO) = !s.isNN && !s.isBye
    val fin = lb.matches.find(_.round == RoundFinal)
    val rst = lb.matches.find(m => m.round == RoundReset && !resetNotNeeded(m))
    // mit Rückspiel entscheidet das Rückspiel, sobald der LB-Sieger das erste Finale gewonnen hat
    val decisive = if rst.isDefined && fin.exists(f => f.finished && f.getWinner() == f.stNoB) then rst else fin
    decisive.filter(_.finished).foreach { m =>
      if real(m.getWinner()) then res += ((m.getWinner(), 1))
      if real(m.getLooser()) then res += ((m.getLooser(), 2))
    }
    var place = 3
    for t <- lbRounds(size) to 1 by -1 do
      val losers = lb.matches.filter(m => lbRoundOf(m) == t && m.finished).map(_.getLooser()).filter(real)
      losers.foreach(s => res += ((s, place)))
      place += lbMatchesIn(size, t)
    res.toSeq.sortBy(_._2)
