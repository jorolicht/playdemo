package shared.utils

/** Tests für die CSV-Erzeugung ([[Csv]]). */
class CsvSuite extends munit.FunSuite:

  test("einfache Zellen bleiben unverändert, Zahlen auch negativ") {
    assertEquals(Csv.cell("Müller, Hans"), "Müller, Hans")
    assertEquals(Csv.cell("-8"), "-8")
    assertEquals(Csv.cell("3:1"), "3:1")
    assertEquals(Csv.cell(""), "")
  }

  test("Trennzeichen, Anführungszeichen und Zeilenumbrüche werden in Anführungszeichen gesetzt") {
    assertEquals(Csv.cell("A;B"), "\"A;B\"")
    assertEquals(Csv.cell("Der \"Kaiser\""), "\"Der \"\"Kaiser\"\"\"")
    assertEquals(Csv.cell("Zeile1\nZeile2"), "\"Zeile1\nZeile2\"")
  }

  test("Formel-Einschleusung wird verhindert") {
    assertEquals(Csv.cell("=HYPERLINK(\"x\")"), "\"'=HYPERLINK(\"\"x\"\")\"")
    assertEquals(Csv.cell("+1+1"), "'+1+1")
    assertEquals(Csv.cell("@SUM(A1)"), "'@SUM(A1)")
    assertEquals(Csv.cell("-1+1"), "'-1+1")
  }

  test("Dokument: BOM, Semikolon, CRLF") {
    assertEquals(Csv.document(Seq("Platz", "Name"), Seq(Seq("1", "Ass; Caro"))),
                 "﻿Platz;Name\r\n1;\"Ass; Caro\"\r\n")
  }
