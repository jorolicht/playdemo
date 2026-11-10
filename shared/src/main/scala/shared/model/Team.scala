package shared.model

import scala.collection.mutable.ArrayBuffer
import shared.basic.Pickle.{ReadWriter => RW, macroRW, *}

/**
 * Mannschaft eines Mannschaftswettbewerbs. Die ersten `teamSize` Spieler (laut Format) sind Stammspieler, weitere
 * Spieler sind Ersatz.
 *
 * @param id      Kennung innerhalb des Wettbewerbs (Startnummer `[T<id>]`)
 * @param name    Name der Mannschaft
 * @param clubId  Verein (0 = ohne)
 * @param players gemeldete Spieler in Reihenfolge (Stammspieler zuerst)
 */
case class Team(
  id:          Int,
  var name:    String,
  var clubId:  Int           = 0,
  var players: Seq[PlayerId] = Seq()
):
  /** Startnummer der Mannschaft. */
  def sno: SNO = SNO.team(id)

  /**
   * Durchschnittliche Wertung (TTR) der Stammspieler mit Wertung.
   *
   * @param teamSize   Anzahl der Stammspieler
   * @param allPlayers Spieler des Turniers
   * @return Durchschnitt, 0 wenn kein Stammspieler eine Wertung hat
   */
  def rating(teamSize: Int, allPlayers: Seq[Player]): Int =
    val ttrs = players.take(teamSize).flatMap(id => allPlayers.find(_.id == id)).flatMap(_.meta.ttr)
    if ttrs.isEmpty then 0 else ttrs.sum / ttrs.length

object Team:
  given rw: RW[Team] = macroRW


/**
 * Aufstellung einer Mannschaft für eine Begegnung.
 *
 * @param players Spieler je Position (Einzel: ein Spieler, Doppel: zwei Spieler, leer = nicht besetzt)
 * @param done    true, sobald die Aufstellung abgegeben ist
 */
case class Lineup(
  players: Map[String, Seq[PlayerId]] = Map(),
  done:    Boolean                    = false
):
  /**
   * Startnummer einer Position: Einzel `[id]`, Doppel `[id1,id2]`, nicht besetzt `[0]`.
   *
   * @param pos    Position
   * @param double true bei einer Doppelposition
   */
  def sno(pos: String, double: Boolean): SNO =
    players.getOrElse(pos, Seq()) match
      case Seq(a)    if !double && a.value > 0              => SNO.single(a)
      case Seq(a, b) if double && a.value > 0 && b.value > 0 => SNO.double(a, b)
      case _                                                 => SNO.nn

object Lineup:
  given rw: RW[Lineup] = macroRW


/**
 * Ein Spiel (Einzel oder Doppel) innerhalb einer Begegnung.
 *
 * @param no     laufende Nummer in der Spielfolge (1 …)
 * @param double true bei einem Doppel
 * @param snoA   Startnummer Heim (Spieler bzw. Doppel)
 * @param snoB   Startnummer Gast
 * @param sets   Satzstand
 * @param result Satzergebnisse in Schnelleingabe, getrennt durch "·"
 * @param status Status wie bei [[MEntry]] (MS_BLOCK, MS_READY, MS_FIN, MS_FIX, MS_SKIP)
 */
case class SubGame(
  no:         Int,
  double:     Boolean,
  var snoA:   SNO,
  var snoB:   SNO,
  var sets:   (Int, Int) = (0, 0),
  var result: String     = "",
  var status: Int        = MEntry.MS_BLOCK
):
  /** true, wenn das Spiel gewertet ist (gespielt oder kampflos). */
  def decided: Boolean = status == MEntry.MS_FIN || status == MEntry.MS_FIX

object SubGame:
  given rw: RW[SubGame] = macroRW


/**
 * Begegnung zweier Mannschaften zu einem Eintrag im Spielplan (`gameNo`). Der Stand der Begegnung wird berechnet und
 * als `sets` des Eintrags gespeichert.
 *
 * @param gameNo  Spielnummer des Eintrags in der Stage
 * @param lineupA Aufstellung der Mannschaft A
 * @param lineupB Aufstellung der Mannschaft B
 * @param games   Spiele der Begegnung
 */
case class Encounter(
  gameNo:      Int,
  var lineupA: Lineup                 = Lineup(),
  var lineupB: Lineup                 = Lineup(),
  games:       ArrayBuffer[SubGame]   = ArrayBuffer.empty
):
  /** Aufstellung einer Seite (0 = A, 1 = B). */
  def lineup(side: Int): Lineup = if side == 0 then lineupA else lineupB

object Encounter:
  given rw: RW[Encounter] = macroRW
