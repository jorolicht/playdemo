package shared.utils

import shared.utils.MedalTable.{ Placing, Row }

/** Tests für den Medaillenspiegel ([[MedalTable.compute]]). */
class MedalTableSuite extends munit.FunSuite:

  private val a = (1, "TTC Alpha")
  private val b = (2, "SV Beta")
  private val c = (3, "TV Gamma")

  test("Gold, Silber, Bronze je Verein; Platz 4 und unplatzierte zählen nicht") {
    val rows = MedalTable.compute(Seq(
      Placing(1, Seq(a)), Placing(2, Seq(b)), Placing(3, Seq(c)), Placing(4, Seq(a)), Placing(0, Seq(b)),
    ))
    assertEquals(rows.map(r => (r.rank, r.club, r.gold, r.silver, r.bronze)),
                 Seq((1, "TTC Alpha", 1, 0, 0), (2, "SV Beta", 0, 1, 0), (3, "TV Gamma", 0, 0, 1)))
  }

  test("Reihenfolge: Gold vor Silber vor Bronze, nicht nach Gesamtzahl") {
    val rows = MedalTable.compute(Seq(
      Placing(1, Seq(a)),
      Placing(2, Seq(b)), Placing(2, Seq(b)), Placing(3, Seq(b)),
    ))
    assertEquals(rows.map(_.club), Seq("TTC Alpha", "SV Beta"))
    assertEquals(rows.map(_.total), Seq(1, 3))
  }

  test("geteilter dritter Platz: beide erhalten Bronze") {
    val rows = MedalTable.compute(Seq(Placing(3, Seq(a)), Placing(3, Seq(b))))
    assertEquals(rows.map(r => (r.club, r.bronze)), Seq(("SV Beta", 1), ("TTC Alpha", 1)))
  }

  test("Gleichstand ergibt gleichen Rang, danach wird übersprungen (1, 1, 3)") {
    val rows = MedalTable.compute(Seq(Placing(1, Seq(a)), Placing(1, Seq(b)), Placing(2, Seq(c))))
    assertEquals(rows.map(r => (r.rank, r.club)), Seq((1, "SV Beta"), (1, "TTC Alpha"), (3, "TV Gamma")))
  }

  test("Doppel aus zwei Vereinen: jeder Verein erhält die Medaille, gleicher Verein nur einmal") {
    val rows = MedalTable.compute(Seq(Placing(1, Seq(a, b)), Placing(2, Seq(c, c))))
    assertEquals(rows.map(r => (r.club, r.gold, r.silver)), Seq(("SV Beta", 1, 0), ("TTC Alpha", 1, 0), ("TV Gamma", 0, 1)))
  }

  test("ohne Verein wird nicht gewertet; ohne ID wird nach Name zusammengefasst") {
    val rows = MedalTable.compute(Seq(
      Placing(1, Seq((0, ""))), Placing(1, Seq()), Placing(2, Seq((0, "Gast"))), Placing(3, Seq((0, " gast "))),
    ))
    assertEquals(rows, Seq(Row(1, 0, "Gast", 0, 1, 1)))
  }

  test("keine Platzierungen: leerer Spiegel") {
    assertEquals(MedalTable.compute(Seq()), Seq())
  }
