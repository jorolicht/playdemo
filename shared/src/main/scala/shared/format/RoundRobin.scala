package shared.format

import scala.collection.mutable.ArrayBuffer
import shared.model.*
import shared.basic.Log
import shared.sport.Sports

type RrGroup = Group

/**
 * Companion object for Round Robin format providing initialization logic.
 */
object RoundRobin {
  /**
   * Initializes a RoundRobinStage stage data structure with a single group.
   */
  def draw(stage: Stage, coTyp: CompTyp, cfg: StageConfig, selectedPants: Seq[Pant], drawOption: DrawOption = DrawOption.Unknown): StageData.RoundRobinStage = {
    val g = Group(1, selectedPants.length, 1, "Gruppe Jeder-gegen-Jeden", TeamEncounter.matchWinSets(stage, coTyp))
    g.format = coTyp.formatId
    selectedPants.zipWithIndex.foreach { case (p, i) => g.pants(i) = p }
    StageData.RoundRobinStage(g)
  }

  /**
   * Gruppe der Rückrunde einer Doppelten Runde: gleiche Teilnehmer in gleicher Reihenfolge, ohne Ergebnisse.
   *
   * @param g Gruppe der Hinrunde
   * @return neue Gruppe (Teilnehmer als Kopie, Platzierungen zurückgesetzt)
   */
  def returnGroup(g: Group): Group = {
    val r = Group(g.grId, g.size, g.quali, g.name, g.noWinSets)
    r.format = g.format
    r.pants  = g.pants.map(p => if (p == null) p else p.copy(place = (0, 0)))
    r
  }

  /**
   * Spiele der Rückrunde einer Doppelten Runde: dieselben Paarungen wie in der Hinrunde mit vertauschtem
   * Heim/Gast (A/B). Spielnummern, Runden und Abhängigkeiten bleiben gleich, Ergebnisse sind leer.
   *
   * @param first   frisch angelegte (noch ungespielte) Spiele der Hinrunde
   * @param stageId ID der Rückrunde
   * @return Spiele der Rückrunde
   */
  def mirrorMatches(first: Seq[MEntry], stageId: StageId): Seq[MEntry] =
    first.collect { case m: MEntryGr =>
      m.copy(stageId = stageId, stNoA = m.stNoB, stNoB = m.stNoA, wgw = (m.wgw._2, m.wgw._1),
             playfield = "", info = "", startTime = "", endTime = "", sets = (0, 0), result = "")
    }

  /**
   * Gesamttabelle einer Doppelten Runde aus Hin- und Rückrunde. Die Ergebnisse beider Stages werden je
   * Teilnehmer zusammengelegt und mit den Entscheidungskriterien des Wertungssystems neu berechnet; der direkte
   * Vergleich zählt damit Hin- und Rückspiel.
   *
   * @param first  Hinrunde
   * @param second Rückrunde (gleiche Teilnehmer in gleicher Reihenfolge)
   * @return temporäre Gruppe mit Summen und Platzierungen, None wenn die Stages nicht zusammenpassen
   */
  def combinedTable(first: Stage, second: Stage): Option[Group] =
    (first.data, second.data) match {
      case (StageData.RoundRobinStage(g1), StageData.RoundRobinStage(g2)) if g1.size == g2.size && g1.size > 0 =>
        val g = Group(g2.grId, g2.size, g2.quali, g2.name, g2.noWinSets)
        g.format  = g2.format
        g.pants   = g2.pants.map(p => if (p == null) p else p.copy())
        g.results = Array.tabulate(g.size)(i => g1.results(i) ++ g2.results(i))
        Sports.using(second.scoring)(g.calc()) match {
          case Left(err) => Log.error(s"RoundRobin.combinedTable: ${err.toString}"); None
          case Right(_)  => Some(g)
        }
      case _ => None
    }
}
