package shared.format

import shared.model.*

type SwGroup = Group

/**
 * Companion object for Swiss System format providing initialization logic.
 */
object SwissSystem {
  /**
   * Initializes a SwissStage stage data structure with a single group.
   */
  def draw(selectedPants: Seq[Pant], noWinSets: Int, drawOption: DrawOption = DrawOption.Unknown): StageData.SwissStage = {
    val g = Group(1, selectedPants.length, 1, "Schweizer System", noWinSets)
    selectedPants.zipWithIndex.foreach { case (p, i) => g.pants(i) = p }
    StageData.SwissStage(g)
  }
}
