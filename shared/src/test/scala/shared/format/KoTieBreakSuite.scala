package shared.format

import scala.collection.mutable.ArrayBuffer
import shared.model.*
import shared.sport.{ Level, Sports }

/**
 * Tests für die Entscheidung unentschiedener Mannschaftsbegegnungen in K.-o.-Runden ([[TeamEncounter.koTieBreak]]).
 */
class KoTieBreakSuite extends munit.FunSuite:

  /** Gewertetes Spiel mit Satzergebnissen in Schnelleingabe (z. B. "5·-7·3"). */
  private def game(no: Int, result: String, noWinSets: Int = 3): SubGame =
    val sets = result.split('·').map(_.trim).filter(_.nonEmpty).foldLeft((0, 0)) { case ((a, b), s) =>
      if s.startsWith("-") then (a, b + 1) else (a + 1, b)
    }
    SubGame(no, false, SNO.single(PlayerId(no)), SNO.single(PlayerId(100 + no)), sets, result, MEntry.MS_FIN)

  /** Begegnung aus gewerteten Spielen. */
  private def enc(games: SubGame*): Encounter = Encounter(1, games = ArrayBuffer(games*))

  /** Braunschweiger System im K.-o.: alle 10 Spiele, kein Unentschieden, kein Entscheidungsspiel. */
  private val braunschweigKo = Level("encounter", Level.Fixed, count = 10)

  test("Braunschweiger System 5:5: bessere Satzdifferenz gewinnt") {
    Sports.using("tt_002") {
      // A gewinnt 5 Spiele 3:0, verliert 5 Spiele 2:3 → Sätze 25:15
      val gs = (1 to 5).map(i => game(i, "5·5·5")) ++ (6 to 10).map(i => game(i, "5·5·-5·-5·-5"))
      assertEquals(TeamEncounter.koTieBreak(enc(gs*), braunschweigKo, (5, 5), 3), Some(MEntry.TieBreakA))
    }
  }

  test("Braunschweiger System 5:5: gleiche Sätze, bessere Balldifferenz gewinnt") {
    Sports.using("tt_002") {
      // je 5 Siege 3:0; B gewinnt seine Sätze deutlicher (11:2 statt 11:9)
      val gs = (1 to 5).map(i => game(i, "9·9·9")) ++ (6 to 10).map(i => game(i, "-2·-2·-2"))
      assertEquals(TeamEncounter.koTieBreak(enc(gs*), braunschweigKo, (5, 5), 3), Some(MEntry.TieBreakB))
    }
  }

  test("Braunschweiger System: kein Gleichstand oder noch offene Spiele → keine Entscheidung") {
    Sports.using("tt_002") {
      val gs = (1 to 6).map(i => game(i, "5·5·5")) ++ (7 to 10).map(i => game(i, "-5·-5·-5"))
      assertEquals(TeamEncounter.koTieBreak(enc(gs*), braunschweigKo, (6, 4), 3), None)
      val open = (1 to 5).map(i => game(i, "5·5·5")) ++ (6 to 9).map(i => game(i, "-5·-5·-5")) :+
        SubGame(10, false, SNO.single(PlayerId(10)), SNO.single(PlayerId(110)), status = MEntry.MS_READY)
      assertEquals(TeamEncounter.koTieBreak(enc(open*), braunschweigKo, (5, 4), 3), None)
    }
  }

  test("Paarkreuz 7:7 (bis 8 Punkte) wird weiter über die Satzdifferenz entschieden") {
    Sports.using("tt_002") {
      val paarkreuzKo = Level("encounter", Level.BestOf, toWin = 8)
      val gs = (1 to 7).map(i => game(i, "-5·-5·-5")) ++ (8 to 14).map(i => game(i, "5·5·-5·5"))
      assertEquals(TeamEncounter.koTieBreak(enc(gs*), paarkreuzKo, (7, 7), 3), Some(MEntry.TieBreakB))
    }
  }

  test("System mit Entscheidungsspiel (Swaythling-Cup): keine Entscheidung über Sätze") {
    Sports.using("tt_002") {
      val swaythlingKo = Level("encounter", Level.Fixed, count = 4, decider = true)
      val gs = Seq(game(1, "5·5·5"), game(2, "5·5·5"), game(3, "-5·-5·-5"), game(4, "-5·-5·-5"))
      assertEquals(TeamEncounter.koTieBreak(enc(gs*), swaythlingKo, (2, 2), 3), None)
    }
  }

  test("Konfiguration: Braunschweiger System hat im K.-o. kein Unentschieden und kein Entscheidungsspiel") {
    val lv = Sports.get("tt_002").encounter("team4", "braunschweig4").get.levelFor("", ko = true)
    assertEquals((lv.kind, lv.count, lv.allowDraw, lv.decider), (Level.Fixed, 10, false, false))
  }
