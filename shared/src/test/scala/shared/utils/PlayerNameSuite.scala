package shared.utils

/** Tests für die Namensanzeige ([[PlayerName]]). */
class PlayerNameSuite extends munit.FunSuite:

  test("voller Name: Nachname, Vorname") {
    assertEquals(PlayerName.full("Lichtenegger", "Robert"), "Lichtenegger, Robert")
    assertEquals(PlayerName.full(" Kahn ", " Oli "), "Kahn, Oli")
    assertEquals(PlayerName.full("Kahn", ""), "Kahn")
    assertEquals(PlayerName.full("", "Oli"), "Oli")
  }

  test("bis 20 Zeichen ungekürzt (Grenze genau 20)") {
    assertEquals(PlayerName.short("Lichtenegger", "Robert"), "Lichtenegger, Robert")   // 20 Zeichen
    assertEquals("Lichtenegger, Robert".length, 20)
  }

  test("länger als 20 Zeichen: Vorname auf ersten Buchstaben mit Punkt") {
    assertEquals(PlayerName.short("Lichtenegger", "Roberta"), "Lichtenegger, R.")
    assertEquals(PlayerName.short("Müller-Lüdenscheidt", "Hans Peter"), "Müller-Lüdenscheidt, H.")
  }

  test("ohne Vorname bleibt der Nachname auch bei Überlänge vollständig") {
    assertEquals(PlayerName.short("Sehrlangernachnamemitbindestrich", ""), "Sehrlangernachnamemitbindestrich")
  }
