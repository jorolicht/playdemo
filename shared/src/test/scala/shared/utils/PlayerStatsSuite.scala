package shared.utils

import shared.model.PlayerId
import shared.utils.PlayerStats.Game

/** Tests für die Spielerstatistik ([[PlayerStats.compute]], [[PlayerStats.longest]]). */
class PlayerStatsSuite extends munit.FunSuite:

  private val (p1, p2, p3, p4) = (PlayerId(1), PlayerId(2), PlayerId(3), PlayerId(4))
  private val noTtr: PlayerId => Option[Int] = _ => None

  private def single(ref: String, a: PlayerId, b: PlayerId, balls: (Int, Int)*): Game =
    val sets = (balls.count(x => x._1 > x._2), balls.count(x => x._2 > x._1))
    Game(ref, Seq(a), Seq(b), sets, balls)

  test("Spiele, Siege, Niederlagen, Sätze und Bälle je Spieler") {
    val rows = PlayerStats.compute(Seq(
      single("g1", p1, p2, (11, 5), (11, 7), (11, 9)),
      single("g2", p2, p3, (11, 9), (5, 11), (11, 8), (11, 4)),
    ), noTtr).map(r => r.player -> r).toMap
    val r2 = rows(p2)
    assertEquals((r2.games, r2.wins, r2.losses), (2, 1, 1))
    assertEquals((r2.setsWon, r2.setsLost), (3, 4))
    assertEquals((r2.ballsWon, r2.ballsLost), (21 + 38, 33 + 32))
    assertEquals((rows(p1).wins, rows(p3).wins), (1, 0))
  }

  test("Doppel zählt für beide Spieler; Sortierung nach Siegen") {
    val rows = PlayerStats.compute(Seq(Game("d1", Seq(p1, p2), Seq(p3, p4), (3, 1), Seq())), noTtr)
    assertEquals(rows.take(2).map(_.player).toSet, Set(p1, p2))
    assertEquals(rows.map(r => (r.games, r.wins)), Seq((1, 1), (1, 1), (1, 0), (1, 0)))
  }

  test("längstes Spiel: meiste Bälle, bei Gleichstand meiste Sätze; ohne Bälle nach Sätzen") {
    val short = single("kurz", p1, p2, (11, 0), (11, 0), (11, 0))
    val long  = single("lang", p1, p3, (12, 10), (9, 11), (11, 9), (13, 11))
    assertEquals(PlayerStats.compute(Seq(short, long), noTtr).find(_.player == p1).flatMap(_.longest).map(_.ref), Some("lang"))
    assertEquals(PlayerStats.longest(Seq(short, long)).map(_.ref), Some("lang"))
    val noBalls = Seq(Game("a", Seq(p1), Seq(p2), (3, 0), Seq()), Game("b", Seq(p1), Seq(p2), (3, 2), Seq()))
    assertEquals(PlayerStats.longest(noBalls).map(_.ref), Some("b"))
  }

  test("unentschiedene oder leere Spiele werden nicht gewertet") {
    assertEquals(PlayerStats.compute(Seq(Game("x", Seq(p1), Seq(p2), (0, 0), Seq())), noTtr), Seq())
    assertEquals(PlayerStats.longest(Seq()), None)
  }

  test("Gewinnerwartung: gleich stark 0,5; 150 Punkte Unterschied 10/11") {
    assertEqualsDouble(PlayerStats.expectation(1500, 1500), 0.5, 1e-9)
    assertEqualsDouble(PlayerStats.expectation(1650, 1500), 10.0 / 11.0, 1e-9)
  }

  test("TTR: Summe über alle Einzel, dann gerundet; nur Einzel mit TTR beider Spieler") {
    val ttr = Map(p1 -> 1500, p2 -> 1500, p3 -> 1650).get
    val rows = PlayerStats.compute(Seq(
      single("s1", p1, p2, (11, 5), (11, 5), (11, 5)),   // p1 gewinnt gegen gleich starken: +0,5
      single("s2", p3, p1, (11, 5), (11, 5), (11, 5)),   // p1 verliert gegen 150 stärkeren: −1/11
      single("s3", p1, p4, (11, 5), (11, 5), (11, 5)),   // p4 ohne TTR: zählt nicht
      Game("d", Seq(p1, p2), Seq(p3, p4), (3, 0), Seq()),// Doppel: zählt nicht
    ), ttr).map(r => r.player -> r).toMap
    assertEquals(rows(p1).ttrDelta, Some(math.round((0.5 - 1.0 / 11.0) * 16).toInt))   // 6,5 → 7
    assertEquals(rows(p1).ttrDelta, Some(7))
    assertEquals(rows(p2).ttrDelta, Some(-8))
    assertEquals(rows(p4).ttrDelta, None)
  }
