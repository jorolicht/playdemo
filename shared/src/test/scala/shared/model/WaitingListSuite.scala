package shared.model

import scala.collection.mutable.ArrayBuffer
import shared.basic.Pickle.*

/**
 * Tests für Höchstzahl und Warteliste eines Wettbewerbs ([[Competition.maxPants]], [[Competition.promoteWaiting]]).
 */
class WaitingListSuite extends munit.FunSuite:

  /** Einzel-Meldung mit Status. */
  private def pant(id: Int, status: PantStatus): Pant =
    Pant(SNO.single(PlayerId(id)), s"Spieler $id", status = status, active = status != PantStatus.WAIT)

  /** Wettbewerb mit Höchstzahl und Meldungen. */
  private def comp(max: Int, pants: Pant*): Competition =
    Competition(CompId(1), "Herren", CompTyp.SINGLE, CompCategory.UNKNOWN, "", CompStatus.CFG,
                pants1Stage = ArrayBuffer(pants*), maxPants = max)

  test("Plätze: Warteliste und abgelehnte Meldungen belegen keinen Platz") {
    val c = comp(3, pant(1, PantStatus.REGI), pant(2, PantStatus.PLAY), pant(3, PantStatus.WAIT), pant(4, PantStatus.RJEC))
    assertEquals(c.placesTaken, 2)
    assert(!c.isFull)
    c.maxPants = 2
    assert(c.isFull)
    c.maxPants = 0
    assert(!c.isFull, "ohne Höchstzahl nie voll")
  }

  test("Warteliste: Position in Anmeldereihenfolge") {
    val c = comp(1, pant(1, PantStatus.REGI), pant(5, PantStatus.WAIT), pant(3, PantStatus.WAIT))
    assertEquals(c.waitPosition(SNO.single(PlayerId(5))), 1)
    assertEquals(c.waitPosition(SNO.single(PlayerId(3))), 2)
    assertEquals(c.waitPosition(SNO.single(PlayerId(1))), 0)
  }

  test("Nachrücken: nur so viele wie Plätze frei, in Reihenfolge, Status REGI und aktiv") {
    val c = comp(2, pant(1, PantStatus.REGI), pant(5, PantStatus.WAIT), pant(3, PantStatus.WAIT))
    assertEquals(c.promoteWaiting().map(_.id), List(SNO.single(PlayerId(5))))
    val p5 = c.pants1Stage.find(_.id == SNO.single(PlayerId(5))).get
    assertEquals(p5.status, PantStatus.REGI)
    assert(p5.active)
    assertEquals(c.waitPosition(SNO.single(PlayerId(3))), 1)
    assertEquals(c.promoteWaiting(), Nil, "voll: niemand rückt nach")
  }

  test("Nachrücken: nach Abmeldung bzw. höherer Höchstzahl, ohne Höchstzahl alle") {
    val c = comp(1, pant(1, PantStatus.REGI), pant(2, PantStatus.WAIT), pant(3, PantStatus.WAIT), pant(4, PantStatus.WAIT))
    c.pants1Stage.remove(0)
    assertEquals(c.promoteWaiting().map(_.id.toString), List(SNO.single(PlayerId(2)).toString))
    c.maxPants = 3
    assertEquals(c.promoteWaiting().size, 2)
    c.pants1Stage += pant(5, PantStatus.WAIT)
    c.pants1Stage += pant(6, PantStatus.WAIT)
    c.maxPants = 0
    assertEquals(c.promoteWaiting().size, 2)
    assertEquals(c.waitingList, Seq())
  }

  test("JSON: maxPants fehlt in alten Daten → 0") {
    val c = comp(4)
    val json = ujson.read(write(c))
    json.obj.remove("maxPants")
    assertEquals(read[Competition](json).maxPants, 0)
    assertEquals(read[Competition](write(c)).maxPants, 4)
  }
