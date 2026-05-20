package pages

import shared.model.*
import base.*

object Mockup extends BasePage with JsWrapper:
  def name = PageNameTyp("Mockup")

  def render(param: String = ""): Boolean = 
    // Seed Data
    val tourney = Tourney(
      id = 0,
      name = "Mock Stadtmeisterschaft 2026",
      organizer = "TTV Musterstadt",
      startDate = 20260515,
      endDate = 20260517,
      ident = "STM26",
      typ = TourneyTyp.TableTennis
    )

    val comp1 = Competition(CompId(1), "Herren A Einzel", CompTyp.SINGLE, "20260515", CompStatus.RUN)
    val comp2 = Competition(CompId(2), "Herren B Einzel", CompTyp.SINGLE, "20260515", CompStatus.CFG)
    val comp3 = Competition(CompId(3), "Damen Einzel", CompTyp.SINGLE, "20260516", CompStatus.FIN)
    
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

    // Seed RoundDB for all competitions in mockup
    services.RoundDB.rounds.clear()
    for (i <- 1 to 128) services.RoundDB.rounds += null
    
    // Rounds for Herren A
    services.RoundDB.rounds(0) = Round(RoundId(1), comp1.id, "A-Vorrunde", RoundCfg.VRGR, RoundStatus.FIN, false, 8, 8)
    services.RoundDB.rounds(1) = Round(RoundId(2), comp1.id, "A-Endrunde", RoundCfg.KO, RoundStatus.CFG, false, 4, 4)
    
    // Rounds for Herren B
    services.RoundDB.rounds(2) = Round(RoundId(3), comp2.id, "B-Vorrunde", RoundCfg.VRGR, RoundStatus.CFG, false, 12, 12)
    
    // Rounds for Damen
    services.RoundDB.rounds(3) = Round(RoundId(4), comp3.id, "D-Endrunde", RoundCfg.KO, RoundStatus.FIN, false, 4, 4)

    Global.currentSelection = Selection(Some(tourney), Some(comp1))
    comps.ContextHeader.render()
    
    loadPage(InfoTourney.name, "")
    true
