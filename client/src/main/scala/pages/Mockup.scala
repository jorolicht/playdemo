package pages

import shared.model.*
import base.*

object Mockup extends BasePage with JsWrapper:
  def name = PageNameTyp("Mockup")

  def render(param: String = ""): Boolean = 
    // Seed Data
    val tourney = Tourney.default.copy(
      id = 0,
      name = "Mock Stadtmeisterschaft 2026",
      organizer = "TTV Musterstadt",
      startDate = 20260515,
      endDate = 20260517,
      ident = "STM26",
      typ = TourneyTyp.TableTennis
    )

    val comp1 = Competition.dummy.copy(id = CompId(1), name = "Herren A Einzel", typ = CompTyp.SINGLE, startDate = "20260515", status = CompStatus.RUN)
    val comp2 = Competition.dummy.copy(id = CompId(2), name = "Herren B Einzel", typ = CompTyp.SINGLE, startDate = "20260515", status = CompStatus.CFG)
    val comp3 = Competition.dummy.copy(id = CompId(3), name = "Damen Einzel", typ = CompTyp.SINGLE, startDate = "20260516", status = CompStatus.FIN)
    
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
    services.RoundDB.rounds(0) = Round.dummy.copy(id = RoundId(1), coId = comp1.id, name = "A-Vorrunde", rndCfg = RoundCfg.VRGR, status = RoundStatus.FIN, size = 8, noPlayers = 8)
    services.RoundDB.rounds(1) = Round.dummy.copy(id = RoundId(2), coId = comp1.id, name = "A-Endrunde", rndCfg = RoundCfg.KO, status = RoundStatus.CFG, size = 4, noPlayers = 4)
    
    // Rounds for Herren B
    services.RoundDB.rounds(2) = Round.dummy.copy(id = RoundId(3), coId = comp2.id, name = "B-Vorrunde", rndCfg = RoundCfg.VRGR, status = RoundStatus.CFG, size = 12, noPlayers = 12)
    
    // Rounds for Damen
    services.RoundDB.rounds(3) = Round.dummy.copy(id = RoundId(4), coId = comp3.id, name = "D-Endrunde", rndCfg = RoundCfg.KO, status = RoundStatus.FIN, size = 4, noPlayers = 4)

    Global.currentSelection = Selection(Some(tourney), Some(comp1))
    comps.ContextHeader.render()
    
    loadPage(InfoTourney.name, "")
    true
