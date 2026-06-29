package shared.format

import scala.collection.mutable.ArrayBuffer
import shared.model.*
import shared.basic.Log

type RrGroup = Group

/**
 * Companion object for Round Robin format providing initialization logic.
 */
object RoundRobin {
  /**
   * Initializes a RoundRobinStage stage data structure with a single group.
   */
  def draw(stage: Stage, coTyp: CompTyp, cfg: StageConfig, selectedPants: Seq[Pant], drawOption: DrawOption = DrawOption.Unknown): StageData.RoundRobinStage = {
    val g = Group(1, selectedPants.length, 1, "Gruppe Jeder-gegen-Jeden", stage.noWinSets)
    selectedPants.zipWithIndex.foreach { case (p, i) => g.pants(i) = p }
    StageData.RoundRobinStage(g)
  }
}
