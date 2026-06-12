package pages

import shared.model.*
import base.*

object Mockup extends BasePage with JsWrapper:
  def name = PageNameTyp("Mockup")

  def render(param: String = ""): Boolean = 
    // Seed Data
    val tourney = Tourney(
      wpId = 0,
      name = "Mock Stadtmeisterschaft 2026",
      organizer = "TTV Musterstadt",
      startDate = 20260515,
      endDate = 20260517,
      ident = "STM26",
      category = CompCategory.TT
    )

    val comp1 = Competition(CompId(1), "Herren A Einzel", CompTyp.SINGLE, CompCategory.TT, "2026-05-15 09:00:00", CompStatus.RUN)
    val comp2 = Competition(CompId(2), "Herren B Einzel", CompTyp.SINGLE, CompCategory.TT, "2026-05-15 11:00:00", CompStatus.CFG)
    val comp3 = Competition(CompId(3), "Damen Einzel", CompTyp.SINGLE, CompCategory.TT, "2026-05-16 14:00:00", CompStatus.FIN)
    
    // Add participants to comp3 for ResultList testing
    comp3.pants += Pant(SNO.single(PlayerId(10)), "Siegfried, Siggi", "TTV Winner", 1800, place = (1, 0), status = PantStatus.FINI)
    comp3.pants += Pant(SNO.single(PlayerId(11)), "Zweiter, Zenzi", "SV Silber", 1750, place = (2, 0), status = PantStatus.FINI)
    comp3.pants += Pant(SNO.single(PlayerId(12)), "Dritter, Dieter", "TTC Bronze", 1700, place = (3, 0), status = PantStatus.FINI)
    
    // Seed CompetitionDB for dropdown mockup
    services.CompetitionDB.competitions.clear()
    for (i <- 1 to 64) services.CompetitionDB.competitions += null
    services.CompetitionDB.competitions(0) = comp1
    services.CompetitionDB.competitions(1) = comp2
    services.CompetitionDB.competitions(2) = comp3

    // Seed StageDB for all competitions in mockup
    services.StageDB.stages.clear()
    for (i <- 1 to 128) services.StageDB.stages += null
    
    // Stages for Herren A
    services.StageDB.stages(0) = Stage(StageId(1), comp1.id, "A-Vorrunde", StageConfig.VRGR, StageStatus.FIN, false, 8, 8)
    services.StageDB.stages(1) = Stage(StageId(2), comp1.id, "A-Endrunde", StageConfig.KO, StageStatus.CFG, false, 4, 4)
    
    // Stages for Herren B
    services.StageDB.stages(2) = Stage(StageId(3), comp2.id, "B-Vorrunde", StageConfig.VRGR, StageStatus.CFG, false, 12, 12)
    
    // Stages for Damen
    services.StageDB.stages(3) = Stage(StageId(4), comp3.id, "D-Endrunde", StageConfig.KO, StageStatus.FIN, false, 4, 4)

    Global.currentSelection = Selection(Some(tourney), Some(comp1))
    comps.ContextHeader.render()
    
    loadPage(TourneyInfo.name, "")
    true
