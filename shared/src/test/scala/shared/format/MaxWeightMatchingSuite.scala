package shared.format

import shared.format.MaxWeightMatching.Edge

/**
 * Tests für [[MaxWeightMatching]]: Vergleich mit einer vollständigen Suche auf zufälligen Graphen sowie Randfälle.
 */
class MaxWeightMatchingSuite extends munit.FunSuite:

  /** Vollständige Suche: bestes (Kantenzahl, Gewicht) bzw. bestes Gewicht. */
  private def brute(n: Int, es: Seq[Edge], maxCard: Boolean): (Int, Long) =
    val w = Array.fill(n, n)(Option.empty[Long])
    es.foreach(e => { w(e.i)(e.j) = Some(e.weight); w(e.j)(e.i) = Some(e.weight) })
    var best = (0, 0L)
    def better(a: (Int, Long), b: (Int, Long)) =
      if maxCard then a._1 > b._1 || (a._1 == b._1 && a._2 > b._2) else a._2 > b._2
    def rec(used: Array[Boolean], cnt: Int, sum: Long): Unit =
      val v = used.indexWhere(!_)
      if v < 0 then { if better((cnt, sum), best) then best = (cnt, sum) }
      else
        used(v) = true
        rec(used, cnt, sum)
        for u <- v + 1 until n if !used(u); x <- w(v)(u) do
          used(u) = true; rec(used, cnt + 1, sum + x); used(u) = false
        used(v) = false
    rec(Array.fill(n)(false), 0, 0L)
    best

  /** Kantenzahl und Gewicht eines Ergebnisses, nachdem es auf Gültigkeit geprüft wurde. */
  private def evaluate(es: Seq[Edge], mate: Array[Int]): (Int, Long) =
    val wmap = es.flatMap(e => Seq((e.i, e.j) -> e.weight, (e.j, e.i) -> e.weight)).toMap
    mate.indices.foreach { v =>
      if mate(v) != -1 then
        assertEquals(mate(mate(v)), v, s"Partner von $v nicht symmetrisch")
        assert(wmap.contains((v, mate(v))), s"Paar ($v, ${mate(v)}) ist keine Kante")
    }
    val pairs = mate.indices.filter(v => mate(v) > v)
    (pairs.length, pairs.map(v => wmap((v, mate(v)))).sum)

  test("stimmt auf 3000 Zufallsgraphen mit der vollständigen Suche überein") {
    val rnd = new scala.util.Random(1)
    for _ <- 1 to 3000 do
      val n = 2 + rnd.nextInt(10)
      val dense = rnd.nextBoolean()
      val es = for i <- 0 until n; j <- i + 1 until n if dense || rnd.nextDouble() < 0.45
        yield Edge(i, j, (rnd.nextInt(40) - 10).toLong * (if rnd.nextInt(5) == 0 then 1000000L else 1L))
      if es.nonEmpty then
        val nv = es.map(e => math.max(e.i, e.j)).max + 1
        for maxCard <- Seq(true, false) do
          val got = evaluate(es, MaxWeightMatching.compute(es, maxCard))
          val exp = brute(nv, es, maxCard)
          if maxCard then assertEquals(got, exp, s"edges=$es")
          else assertEquals(got._2, exp._2, s"edges=$es")
  }

  test("vollständiger Graph mit gerader Knotenzahl ergibt ein perfektes Matching") {
    val rnd = new scala.util.Random(2)
    val n = 64
    val es = for i <- 0 until n; j <- i + 1 until n yield Edge(i, j, rnd.nextInt(1000000).toLong - 500000)
    val mate = MaxWeightMatching.compute(es, maxCardinality = true)
    assert(mate.forall(_ >= 0))
  }

  test("Randfälle: keine Kanten, eine Kante, Dreieck") {
    assertEquals(MaxWeightMatching.compute(Seq.empty).toSeq, Seq.empty[Int])
    assertEquals(MaxWeightMatching.compute(Seq(Edge(0, 1, 5))).toSeq, Seq(1, 0))
    val tri = MaxWeightMatching.compute(Seq(Edge(0, 1, 1), Edge(1, 2, 3), Edge(0, 2, 2)))
    assertEquals(tri.toSeq, Seq(-1, 2, 1))
  }

  test("128 Knoten vollständig in unter einer Sekunde") {
    val rnd = new scala.util.Random(3)
    val n = 128
    val es = for i <- 0 until n; j <- i + 1 until n yield Edge(i, j, rnd.nextInt(1000000).toLong)
    val t0 = System.nanoTime()
    val mate = MaxWeightMatching.compute(es, maxCardinality = true)
    assert((System.nanoTime() - t0) / 1000000 < 1000)
    assert(mate.forall(_ >= 0))
  }
