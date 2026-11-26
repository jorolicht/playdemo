package shared.model

import shared.basic.Pickle.*

/**
 * Tests für die Bemerkung zu einem Spiel ([[MEntry.remark]]): Speicherung nach dem Abschluss "_" des kodierten
 * Spiels, ohne Bemerkung unverändertes Format, ältere Daten ohne Bemerkung lesbar.
 */
class MatchRemarkSuite extends munit.FunSuite:

  private val (a, b) = (SNO.single(PlayerId(1)), SNO.single(PlayerId(2)))

  private def gr: MEntry = MEntryGr.init(CompId(1), CompTyp.SINGLE, StageId(3), StageFormat.RR, 7, a, b, 1, 1, (1, 2), 3)
  private def ko: MEntry = MEntryKo.init(CompId(1), CompTyp.SINGLE, StageId(4), StageFormat.KO, a, b, 1, 3, 1, "", "", 0, (0, 0), 3)
  private def sw: MEntry = MEntrySw.init(CompId(1), CompTyp.SINGLE, StageId(5), StageFormat.SW, a, b, 1, 1, 1, 0, (0, 0), 3)

  private def roundTrip(m: MEntry): MEntry = read[MEntry](write(m))

  test("ohne Bemerkung: kodierter Text endet wie bisher mit \"^_\"") {
    for m <- Seq(gr, ko, sw) do
      assert(m.toTx.content.endsWith("^_"), m.toTx.content)
      assertEquals(roundTrip(m).remark, "")
  }

  test("Bemerkung bleibt beim Speichern und Laden erhalten (Gruppe, K.-o., Schweizer System)") {
    for m <- Seq(gr, ko, sw) do
      m.remark = "Protest von Spieler A, Schiedsrichter informiert"
      assert(m.toTx.content.endsWith("^_^Protest von Spieler A, Schiedsrichter informiert"), m.toTx.content)
      val back = roundTrip(m)
      assertEquals(back.remark, "Protest von Spieler A, Schiedsrichter informiert")
      assertEquals(back.toTx.content, m.toTx.content)
  }

  test("ältere Daten ohne Bemerkung werden gelesen") {
    val old = MEntryTx(CompId(1), CompTyp.SINGLE, StageId(3), StageFormat.RR,
                       "1^[14]^[39]^1^1^1^10^^8·9^1^^20261015110511^20261015110511^2^3^1^3^-5·3·6·8^_")
    val m = read[MEntry](write(old)(using MEntry.rwTx))
    assertEquals(m.remark, "")
    assertEquals(m.sets, (3, 1))
    assertEquals(m.toTx.content, old.content)
  }

  test("Bemerkung wird bereinigt: ohne Trennzeichen und Zeilenumbrüche, höchstens 200 Zeichen") {
    assertEquals(MEntry.cleanRemark("  a^b\nc\r\nd  "), "a b c d")
    assertEquals(MEntry.cleanRemark("x" * 250).length, MEntry.RemarkMaxLen)
    val m = gr
    m.remark = "Zeile 1\nZeile 2 ^ mit Dach"
    assertEquals(roundTrip(m).remark, "Zeile 1 Zeile 2   mit Dach")
  }

  test("Zurücksetzen eines Spiels löscht die Bemerkung") {
    val m = ko
    m.remark = "Netzroller-Diskussion"
    m.reset()
    assertEquals(m.remark, "")
  }
