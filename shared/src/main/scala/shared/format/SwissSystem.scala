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
    val updatedPants = if (selectedPants.length % 2 == 1) {
      selectedPants :+ Pant(id = SNO.bye(selectedPants.length), name = "")
    } else {
      selectedPants
    }
    val g = Group(1, updatedPants.length, 1, "Schweizer System", noWinSets)
    updatedPants.zipWithIndex.foreach { case (p, i) => g.pants(i) = p }
    StageData.SwissStage(g)
  }
}
