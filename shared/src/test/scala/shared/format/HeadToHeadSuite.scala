package shared.format

import shared.model.*
import shared.sport.Sports

/**
 * Tests für den direkten Vergleich bei Punktgleichheit in Gruppentabellen ([[Group.calc]], Kriterium `headToHead`,
 * ab Wertungssystem tt_003). Ältere Versionen werten unverändert nach Spiel-, Satz- und Balldifferenz aller Spiele.
 */
class HeadToHeadSuite extends munit.FunSuite:

  /** Gruppe mit n Teilnehmern (Spieler 1 … n an Position 0 … n-1). */
  private def group(n: Int): Group =
    val g = Group(1, n, 2, "A", 3)
    g.pants = Array.tabulate(n)(i => Pant(SNO.single(PlayerId(i + 1)), s"Spieler ${i + 1}"))
    g

  /**
   * Trägt ein Spiel ein: Sätze aus Sicht von a; Bälle je Satz 11:5 bzw. 5:11, außer `close` (11:9 bzw. 9:11).
   */
  private def play(g: Group, a: Int, b: Int, sets: (Int, Int), close: Boolean = false): Unit =
    val (w, l) = if close then (11, 9) else (11, 5)
    val balls  = (sets._1 * w + sets._2 * l, sets._1 * l + sets._2 * w)
    val win    = if sets._1 > sets._2 then (1, 0) else (0, 1)
    val e      = GroupEntry(true, win, sets, balls, Array[String]())
    g.results(a)(b) = e
    g.results(b)(a) = e.invert

  /** Plätze nach Position. */
  private def places(g: Group, key: String): Seq[Int] =
    Sports.using(key)(g.calc())
    g.pants.map(_.place._1).toSeq

  /**
   * Zwei Paare mit gleich vielen Siegen: Spieler 1 und 2 (je 2 Siege), Spieler 3 und 4 (je 1 Sieg).
   * Die Satzdifferenz aller Spiele spricht jeweils für den Verlierer des direkten Spiels.
   */
  private def twoPairs: Group =
    val g = group(4)
    play(g, 0, 1, (3, 2))   // 1 schlägt 2
    play(g, 0, 3, (3, 2))   // 1 schlägt 4
    play(g, 2, 0, (3, 0))   // 3 schlägt 1
    play(g, 1, 2, (3, 0))   // 2 schlägt 3
    play(g, 1, 3, (3, 0))   // 2 schlägt 4
    play(g, 3, 2, (3, 2))   // 4 schlägt 3
    g

  test("Zwei Gleichstehende: das direkte Spiel entscheidet (tt_003)") {
    assertEquals(places(twoPairs, "tt_003"), Seq(1, 2, 4, 3))
  }

  test("Ältere Versionen unverändert: Satzdifferenz aller Spiele (tt_002, tt_001)") {
    assertEquals(places(twoPairs, "tt_002"), Seq(2, 1, 3, 4))
    assertEquals(places(twoPairs, "tt_001"), Seq(2, 1, 3, 4))
  }

  test("Drei Gleichstehende im Kreis: Teiltabelle nur aus den Spielen untereinander") {
    val g = group(4)
    play(g, 0, 1, (3, 0))   // Teiltabelle: 1 +2 Sätze, 2 ±0, 3 -2
    play(g, 1, 2, (3, 0))
    play(g, 2, 0, (3, 2))
    play(g, 0, 3, (3, 2))   // gegen Spieler 4: 2 gewinnt deutlicher, insgesamt Sätze 1 +3, 2 +3
    play(g, 1, 3, (3, 0))
    play(g, 2, 3, (3, 0))
    assertEquals(places(g, "tt_003"), Seq(1, 2, 3, 4))
  }

  test("Teiltabelle trennt einen ab: für die übrigen beginnt der Vergleich neu (ITTF 3.7.5.4)") {
    val g = group(4)
    // 1, 2, 3 je 2 Siege (alle schlagen 4); untereinander je 1 Sieg
    play(g, 0, 1, (3, 0))                 // 1 schlägt 2 deutlich (Bälle +18)
    play(g, 2, 0, (3, 1), close = true)   // 3 schlägt 1 knapp (Bälle aus Sicht von 1: -4)
    play(g, 1, 2, (3, 2))                 // 2 schlägt 3 (Bälle aus Sicht von 3: -6)
    play(g, 0, 3, (3, 0)); play(g, 1, 3, (3, 0)); play(g, 2, 3, (3, 0))
    // Teiltabelle Sätze: 1: 3:0 + 1:3 = +1, 3: 3:1 + 2:3 = +1, 2: 0:3 + 3:2 = -2 → 2 ist Dritter.
    // 1 und 3: Vergleich beginnt neu, nur ihr Spiel → 3 schlug 1. In einem einzigen Durchgang hätten dagegen die
    // Bälle der Teiltabelle entschieden (1: +14, 3: -2) und 1 läge vorn.
    assertEquals(places(g, "tt_003"), Seq(2, 3, 1, 4))
  }

  test("Doppelte Runde: beide direkten Spiele zählen") {
    val g = group(3)
    g.results = Array.fill(3, 6)(GroupEntry(false, (0, 0), (0, 0), (0, 0), Array("")))
    def play2(a: Int, b: Int, leg: Int, sets: (Int, Int)): Unit =
      val win = if sets._1 > sets._2 then (1, 0) else (0, 1)
      val e   = GroupEntry(true, win, sets, (sets._1 * 11, sets._2 * 11), Array[String]())
      g.results(a)(leg * 3 + b) = e
      g.results(b)(leg * 3 + a) = e.invert
    // 1 und 2 je 3 Siege; direkt 1:1 Siege, Sätze 3:0 und 2:3 → Spieler 1 +2
    play2(0, 1, 0, (3, 0)); play2(1, 0, 1, (3, 2))
    play2(0, 2, 0, (3, 2)); play2(0, 2, 1, (3, 2))
    play2(1, 2, 0, (3, 0)); play2(1, 2, 1, (3, 0))
    assertEquals(places(g, "tt_003"), Seq(1, 2, 3))
  }

  test("Nicht auflösbar: gleicher Platz, Plätze fortlaufend") {
    val g = group(3)
    play(g, 0, 1, (3, 0)); play(g, 1, 2, (3, 0)); play(g, 2, 0, (3, 0))
    assertEquals(places(g, "tt_003"), Seq(1, 1, 1))
  }

  test("Mannschaften: kein direkter Vergleich in der Tabelle der Formate") {
    Seq("team2", "team3", "team4", "team6").foreach { f =>
      assert(!Sports.get("tt_003").forFormat(f).standings.tieBreakers.contains("headToHead"), f)
    }
    assertEquals(Sports.get("tt_003").standings.tieBreakers, Seq("diff:match", "headToHead", "diff:set", "diff:ball"))
    assertEquals(Sports.latestKey(), "tt_003")
  }
