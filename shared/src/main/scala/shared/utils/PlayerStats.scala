package shared.utils

import shared.model.PlayerId

/**
 * Spielerstatistik über alle Einzel- und Doppelspiele eines Turniers: Spiele, Siege, Niederlagen, Sätze, Bälle, längstes
 * Spiel und voraussichtliche TTR-Veränderung.
 *
 * Längstes Spiel: die meisten gespielten Bälle, bei Gleichstand die meisten Sätze (die Spieldauer wird nicht zuverlässig
 * erfasst). Ohne eingetragene Bälle zählen nur die Sätze.
 *
 * TTR-Veränderung (nur Einzel, beide Spieler mit TTR): vereinfachte Berechnung nach der TTR-Formel; Gewinnerwartung
 * E = 1 / (1 + 10^((TTR Gegner − TTR eigen) / 150)), Summe (Ergebnis − E) über alle Einzel der Veranstaltung mal
 * Änderungskonstante [[TtrK]], dann gerundet. Alters- und Neulingszuschläge zur Änderungskonstante sind nicht
 * berücksichtigt; der Wert ist daher nur ein Anhaltspunkt, maßgeblich ist die Berechnung des Verbands.
 */
object PlayerStats:

  /** Änderungskonstante der TTR-Berechnung ohne Zuschläge. */
  val TtrK: Double = 16.0

  /**
   * Gewertetes Spiel.
   *
   * @param ref   Kennung des Spiels für die Anzeige (z. B. Wettbewerb und Spielnummer)
   * @param sideA Spieler der Seite A (Einzel einer, Doppel zwei)
   * @param sideB Spieler der Seite B
   * @param sets  Sätze aus Sicht von A
   * @param balls Bälle je Satz aus Sicht von A (leer, wenn nicht eingetragen)
   */
  case class Game(ref: String, sideA: Seq[PlayerId], sideB: Seq[PlayerId], sets: (Int, Int), balls: Seq[(Int, Int)]):
    /** Einzel (je Seite ein Spieler). */
    def single: Boolean = sideA.length == 1 && sideB.length == 1
    /** Gespielte Bälle insgesamt. */
    def ballCount: Int = balls.map(b => b._1 + b._2).sum
    /** Gespielte Sätze insgesamt. */
    def setCount: Int = sets._1 + sets._2
    /** Maß für die Länge: erst Bälle, dann Sätze. */
    def length: (Int, Int) = (ballCount, setCount)

  /**
   * Zeile der Spielerstatistik.
   *
   * @param player     Spieler
   * @param games      Spiele
   * @param wins       Siege
   * @param setsWon    gewonnene Sätze
   * @param setsLost   verlorene Sätze
   * @param ballsWon   gewonnene Bälle
   * @param ballsLost  verlorene Bälle
   * @param longest    längstes Spiel des Spielers
   * @param ttrDelta   voraussichtliche TTR-Veränderung (None ohne gewertetes Einzel mit TTR)
   */
  case class Row(player: PlayerId, games: Int, wins: Int, setsWon: Int, setsLost: Int, ballsWon: Int, ballsLost: Int,
                 longest: Option[Game], ttrDelta: Option[Int]):
    /** Niederlagen. */
    def losses: Int = games - wins

  /**
   * Gewinnerwartung eines Spielers gegen einen Gegner.
   *
   * @param own TTR des Spielers
   * @param opp TTR des Gegners
   * @return Erwartung zwischen 0 und 1
   */
  def expectation(own: Int, opp: Int): Double = 1.0 / (1.0 + math.pow(10.0, (opp - own) / 150.0))

  /**
   * Berechnet die Statistik aller Spieler.
   *
   * @param games Gewertete Spiele (Einzel und Doppel, mit Sieger; unentschiedene Spiele werden ignoriert)
   * @param ttr   TTR je Spieler (fehlend oder 0 = ohne TTR)
   * @return Zeilen sortiert nach Siegen, dann Spielen (jeweils absteigend)
   */
  def compute(games: Seq[Game], ttr: PlayerId => Option[Int]): Seq[Row] =
    val decided = games.filter(g => g.sets._1 != g.sets._2)
    val perSide = decided.flatMap { g =>
      val aWins = g.sets._1 > g.sets._2
      val ballsA = (g.balls.map(_._1).sum, g.balls.map(_._2).sum)
      g.sideA.map(p => (p, g, aWins, g.sets, ballsA, g.sideB)) ++
        g.sideB.map(p => (p, g, !aWins, g.sets.swap, ballsA.swap, g.sideA))
    }
    perSide.groupBy(_._1).toSeq.map { case (p, entries) =>
      val singlesWithTtr = for
        (_, g, won, _, _, opp) <- entries if g.single
        own                    <- ttr(p).filter(_ > 0)
        other                  <- ttr(opp.head).filter(_ > 0)
      yield (if won then 1.0 else 0.0) - expectation(own, other)
      Row(
        player    = p,
        games     = entries.length,
        wins      = entries.count(_._3),
        setsWon   = entries.map(_._4._1).sum,
        setsLost  = entries.map(_._4._2).sum,
        ballsWon  = entries.map(_._5._1).sum,
        ballsLost = entries.map(_._5._2).sum,
        longest   = Some(entries.map(_._2).maxBy(_.length)),
        ttrDelta  = if singlesWithTtr.isEmpty then None else Some(math.round(singlesWithTtr.sum * TtrK).toInt)
      )
    }.sortBy(r => (-r.wins, -r.games))

  /**
   * Längstes Spiel des Turniers.
   *
   * @param games Gewertete Spiele
   * @return Spiel mit den meisten Bällen (bei Gleichstand den meisten Sätzen)
   */
  def longest(games: Seq[Game]): Option[Game] =
    games.filter(_.setCount > 0).maxByOption(_.length)
