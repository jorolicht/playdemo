package shared.sport

/**
 * Tests für die Bezeichnungen aus dem Wertungssystem ([[SportConfig.formatName]], [[SportConfig.levelName]],
 * [[SportConfig.winVariants]], [[Sports.formatName]], [[Sports.sportName]]).
 */
class SportNamesSuite extends munit.FunSuite:

  private val tt = Sports.get("tt_002")

  test("Sportart und Formate je Sprache") {
    assertEquals(tt.sportName("de"), "Tischtennis")
    assertEquals(tt.sportName("en"), "Table Tennis")
    assertEquals(tt.formatName("single", "de"), Some("Einzel"))
    assertEquals(tt.formatName("team4", "en"), Some("Team of 4"))
    assertEquals(tt.formatName("unkn", "de"), None)
  }

  test("Stufen: Singular, Plural, Kurzform; Rückfall der Sprache auf en") {
    assertEquals(tt.levelName("set", "de"), Some(Name("Satz", "Sätze", "S")))
    assertEquals(tt.levelName("ball", "en").map(_.plural), Some("Points"))
    assertEquals(tt.levelName("match", "fr").map(_.one), Some("Match"))
    assertEquals(tt.levelName("leg", "de"), None)
    assertEquals(Name("Mixed").plural, "Mixed")
  }

  test("Gewinnsätze aus den Varianten, aufsteigend") {
    assertEquals(tt.winVariants.map((n, names) => (n, names.localized("de").one)),
                 Seq((2, "2 Gewinnsätze"), (3, "3 Gewinnsätze"), (4, "4 Gewinnsätze")))
  }

  test("Begegnung als oberste Stufe eines Mannschaftsformats") {
    assertEquals(tt.forFormat("team2").matchLevel.name.localized("de").one, "Begegnung")
    assertEquals(tt.forFormat("single").matchLevel.name.localized("de").one, "Spiel")
  }

  test("Sports: Format über den Schlüssel des Wettbewerbs, unbekannte Sportart ohne Namen") {
    assertEquals(Sports.formatName("", "double", "de"), "Doppel")
    assertEquals(Sports.formatName("tt_001", "team6", "en"), "Team of 6")
    assertEquals(Sports.formatName("tt_002", "xyz", "de"), "xyz")
    assertEquals(Sports.sportName("TT", "de"), Some("Tischtennis"))
    assertEquals(Sports.sportName("UNKNOWN", "de"), None)
  }
