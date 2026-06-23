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
    val sortedActivePants = selectedPants.sortBy(-_.rating)
    val updatedPants = if (sortedActivePants.length % 2 == 1) {
      sortedActivePants :+ Pant(id = SNO.bye(sortedActivePants.length), name = "")
    } else {
      sortedActivePants
    }
    val g = Group(1, updatedPants.length, 1, "Schweizer System", noWinSets)
    updatedPants.zipWithIndex.foreach { case (p, i) =>
      val pCopy = p.copy()
      pCopy.place = (i + 1, 0)
      g.pants(i) = pCopy
    }
    StageData.SwissStage(g)
  }
}
