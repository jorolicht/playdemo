package shared.format

import shared.model.*

/**
 * Tests für die Auslosung im Schweizer System ([[SwissMatcher.computePairings]]).
 */
class SwissMatcherSuite extends munit.FunSuite:

  private def p(id: Int, club: Int, rating: Int, score: Int) =
    SwissPlayer(id, club, rating, score, SNO.single(PlayerId(id)))

  private def pairsOf(ps: Seq[SwissPair]): Set[Set[Int]] = ps.map(x => Set(x.id._1, x.id._2)).toSet

  private def allPlacedOnce(ps: Seq[SwissPair], ids: Seq[Int]): Unit =
    assertEquals(ps.flatMap(x => Seq(x.id._1, x.id._2)).filter(_ > 0).sorted, ids.sorted)

  private val field = Seq(
    p(1, 1, 1800, 2), p(2, 2, 1500, 2),
    p(3, 3, 1700, 1), p(4, 4, 1650, 1), p(5, 5, 1400, 1), p(6, 6, 1300, 1),
    p(7, 7, 1600, 0), p(8, 8, 1200, 0))

  test("Paarungen innerhalb der Punktgruppen") {
    val score = field.map(x => x.id -> x.scoreGroup).toMap
    val res = SwissMatcher.computePairings(field, Set.empty)
    allPlacedOnce(res, field.map(_.id))
    assert(res.forall(x => score(x.id._1) == score(x.id._2)), res.toString)
  }

  test("keine Wiederholung einer Paarung") {
    val res = SwissMatcher.computePairings(field, Set((1, 2), (2, 1)))
    allPlacedOnce(res, field.map(_.id))
    assert(!pairsOf(res).contains(Set(1, 2)), res.toString)
  }

  test("Wiederholung nur, wenn sie unvermeidbar ist") {
    val res = SwissMatcher.computePairings(Seq(p(1, 1, 1500, 1), p(2, 2, 1500, 1)), Set((1, 2), (2, 1)))
    assertEquals(res.map(_.id), Seq((1, 2)))
  }

  test("Vereinsschutz bei gleicher Punktzahl") {
    val res = SwissMatcher.computePairings(Seq(p(1, 9, 1500, 1), p(2, 9, 1490, 1), p(3, 5, 1480, 1), p(4, 6, 1470, 1)), Set.empty)
    assert(!pairsOf(res).contains(Set(1, 2)), res.toString)
  }

  test("ohne Vereinsangabe gelten Spieler nicht als gleicher Verein") {
    val w0 = SwissMatcher.calculateWeight(p(1, 0, 1500, 1), p(2, 0, 1500, 1), Set.empty)
    assertEquals(w0, 0L)
  }

  test("Freilos an den schwächsten Spieler der untersten Punktgruppe, kein zweites Freilos") {
    val d = Seq(p(1, 1, 1800, 2), p(2, 2, 1500, 1), p(3, 3, 1700, 1), p(4, 4, 1650, 0), p(5, 5, 1100, 0))
    val first = SwissMatcher.computePairings(d, Set.empty)
    assert(first.exists(_.id == (5, -1)), first.toString)
    val second = SwissMatcher.computePairings(d, Set((5, -1)))
    assert(second.exists(_.id == (4, -1)), second.toString)
    assertEquals(second.count(_.id._2 == -1), 1)
    allPlacedOnce(second, d.map(_.id))
  }

  test("ein einzelner Spieler erhält das Freilos, leeres Feld ergibt keine Paarung") {
    assertEquals(SwissMatcher.computePairings(Seq(p(1, 1, 1500, 0)), Set.empty).map(_.id), Seq((1, -1)))
    assertEquals(SwissMatcher.computePairings(Seq.empty, Set.empty), Seq.empty)
  }

  test("101 Spieler: alle genau einmal, deterministisch") {
    val r = new scala.util.Random(3)
    val big = (1 to 101).map(i => p(i, r.nextInt(20) + 1, 1000 + r.nextInt(1200), r.nextInt(5)))
    val hist = (1 to 300).map(_ => (r.nextInt(101) + 1, r.nextInt(101) + 1)).toSet
    val a = SwissMatcher.computePairings(big, hist)
    allPlacedOnce(a, 1 to 101)
    assertEquals(SwissMatcher.computePairings(big, hist), a)
  }
