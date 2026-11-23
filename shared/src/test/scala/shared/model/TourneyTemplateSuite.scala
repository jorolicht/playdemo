package shared.model

import shared.basic.Pickle.*

/**
 * Tests für [[TourneyTemplate]] („Turnier kopieren“) mit dem Demo-Turnier (wp-plugin/data/demo_tourney.json).
 */
class TourneyTemplateSuite extends munit.FunSuite:

  /** Demo-Turnier aus der Datei des Plugins. */
  private def demo: Tourney =
    val file = Seq("wp-plugin/data/demo_tourney.json", "../wp-plugin/data/demo_tourney.json").map(java.io.File(_)).find(_.exists).get
    val json = ujson.read(scala.io.Source.fromFile(file, "UTF-8").mkString)
    read[Tourney](json("tourney")("tourney"))

  test("Datumsrechnung: Hin- und Rückweg, Schaltjahr, Verschieben mit Uhrzeit") {
    for ymd <- Seq(19700101, 20240229, 20261123, 20301231, 21000301) do
      assertEquals(TourneyTemplate.fromDays(TourneyTemplate.toDays(ymd)), ymd)
    assertEquals(TourneyTemplate.toDays(20240301) - TourneyTemplate.toDays(20240228), 2L)
    assertEquals(TourneyTemplate.shiftIso("2030-04-01 09:00:00", 364), "2031-03-31 09:00:00")
    assertEquals(TourneyTemplate.shiftIso("", 364), "")
    assert(!TourneyTemplate.validYmd(20260230))
  }

  test("Kopie: Stammdaten übernommen, Spieler, Vereine, Ergebnisse und Spielphasen leer") {
    val src = demo
    val c = TourneyTemplate.copy(src, "Meisterschaft 2031", 20310331)
    assertEquals(c.name, "Meisterschaft 2031")
    assertEquals(c.wpId, 0)
    assertEquals(c.startDate, 20310331)
    assertEquals(TourneyTemplate.toDays(c.endDate) - TourneyTemplate.toDays(c.startDate),
                 TourneyTemplate.toDays(src.endDate) - TourneyTemplate.toDays(src.startDate))
    assertEquals(c.organizer, src.organizer)
    assertEquals(c.address, src.address)
    assertEquals(c.contact, src.contact)
    assert(c.players.isEmpty && c.clubs.isEmpty)
    assert(c.stages.forall(_ == null))
    val comps = c.competitions.filter(_ != null)
    assertEquals(comps.map(_.name).toSeq, src.competitions.filter(c => c != null && !c.deleted).map(_.name).toSeq)
    assert(comps.forall(x => x.status == CompStatus.CFG && x.pants1Stage.isEmpty && x.startStage.isEmpty && x.teams.isEmpty))
  }

  test("Kopie: Startzeiten der Wettbewerbe um den Abstand der Startdaten verschoben, Uhrzeit bleibt") {
    val src = demo
    val days = 364
    val c = TourneyTemplate.copy(src, "X", TourneyTemplate.fromDays(TourneyTemplate.toDays(src.startDate) + days))
    for (o, n) <- src.competitions.filter(_ != null).zip(c.competitions.filter(_ != null)) do
      assertEquals(n.startDate, TourneyTemplate.shiftIso(o.startDate, days))
      assertEquals(n.startDate.drop(10), o.startDate.drop(10))
  }

  test("Phasenplan: Spielphasen mit Spielsystem und Vorgänger") {
    val src = demo
    val c = TourneyTemplate.copy(src, "X", src.startDate)
    val herren = c.competitions.find(x => x != null && x.name == "S-Klasse Herren Einzel").get
    assertEquals(herren.stagePlan.map(_.name), List("Vorrunde", "Endrunde"))
    assertEquals(herren.stagePlan.map(_.pref), List(-1, 0))
    assertEquals(herren.stagePlan(1).config, "KO")
    val ohne = c.competitions.find(x => x != null && x.name == "B-Klasse Damen Einzel").get
    assertEquals(ohne.stagePlan, Nil)
  }

  test("Phasenplan: gekoppelte Phasen nur einmal, ohne Zusatz im Namen") {
    val src = demo
    val comp = src.competitions.find(x => x != null && x.name == "B-Klasse Damen Einzel").get
    val first = src.addStage(comp.id, None, "Liga", StageConfig.DRR, 4, 4, doSync = false).toOption.get
    src.addLinkedStage(first, LinkKind.DRR, "Liga – Hinrunde", "Liga – Rückrunde", doSync = false)
    val plan = TourneyTemplate.plan(src, src.competitions.find(x => x != null && x.id == comp.id).get)
    assertEquals(plan.map(_.name), List("Liga"))
    assertEquals(plan.head.config, "DRR")
  }

  test("nextPlan: nächste noch nicht angelegte Phase") {
    val c = Competition(CompId(1), "C", CompTyp.SINGLE, CompCategory.TT, "", CompStatus.CFG,
                        stagePlan = List(StagePlan("Vorrunde", "GRPS4"), StagePlan("Endrunde", "KO", pref = 0)))
    assertEquals(TourneyTemplate.nextPlan(c, Seq.empty).map(_.name), Some("Vorrunde"))
    val vr = Stage(StageId(1), CompId(1), "Vorrunde – Hinrunde", StageConfig.DRR, StageStatus.CFG, false, 0, 0,
                   StageData.GroupsStage(scala.collection.mutable.ArrayBuffer.empty))
    assertEquals(TourneyTemplate.nextPlan(c, Seq(vr)).map(_.name), Some("Endrunde"))
  }

  test("Einzelwettbewerb (SIMPLE) bleibt SIMPLE, ClickTT-Kennung entfällt") {
    val src = demo
    src.ident = "SIMPLE"
    assertEquals(TourneyTemplate.copy(src, "X", src.startDate).ident, "SIMPLE")
    src.ident = "ABC123"
    assertEquals(TourneyTemplate.copy(src, "X", src.startDate).ident, "")
  }
