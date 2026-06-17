package shared.format

import shared.model.*

type RrGroup = Group

/**
 * Companion object for Round Robin format providing initialization logic.
 */
object RoundRobin {
  /**
   * Initializes a RoundRobinStage stage data structure with a single group.
   */
  def draw(selectedPants: Seq[Pant], noWinSets: Int, stageOption: StageOption = StageOption.Unknown): StageData.RoundRobinStage = {
    val g = Group(1, selectedPants.length, 1, "Gruppe 1", noWinSets)
    selectedPants.zipWithIndex.foreach { case (p, i) => g.pants(i) = p }
    StageData.RoundRobinStage(g)
  }
}
