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
    val opt = if (drawOption == DrawOption.Unknown) DrawOption.SwUpperLower else drawOption
    val pairedPants = getPairings(g.pants.toSeq, opt)
    for (i <- 0 until g.pants.length) {
      if (i < pairedPants.length) g.pants(i) = pairedPants(i)
    }
    StageData.SwissStage(g)
  }

  def getPairings(pants: Seq[Pant], option: DrawOption): Seq[Pant] = {
    val activePants = pants.filter(p => p != null && p.id != SNO.nn && !p.id.isBye).sortBy(_.place._1)
    val byes = pants.filter(p => p != null && (p.id.isBye || p.id.isNN))
    
    // Helper to pair a subgroup upper vs lower half
    def pairHalf(players: Seq[Pant]): Seq[Pant] = {
      val half = (players.length + 1) / 2
      val (h1, h2) = players.splitAt(half)
      val result = scala.collection.mutable.ArrayBuffer[Pant]()
      for (i <- 0 until half) {
        if (i < h1.length) result += h1(i)
        if (i < h2.length) result += h2(i)
      }
      result.toSeq
    }

    option match {
      case DrawOption.SwUpperLower =>
        pairHalf(activePants) ++ byes
        
      case DrawOption.SwAccel2 =>
        val halfSize = (activePants.length + 1) / 2
        val (g1, g2) = activePants.splitAt(halfSize)
        pairHalf(g1) ++ pairHalf(g2) ++ byes
        
      case DrawOption.SwAccel3 =>
        val size1 = (activePants.length + 2) / 3
        val size2 = (activePants.length - size1 + 1) / 2
        val g1 = activePants.take(size1)
        val g2 = activePants.slice(size1, size1 + size2)
        val g3 = activePants.drop(size1 + size2)
        pairHalf(g1) ++ pairHalf(g2) ++ pairHalf(g3) ++ byes
        
      case DrawOption.SwTopBottom =>
        val result = scala.collection.mutable.ArrayBuffer[Pant]()
        val n = activePants.length
        for (i <- 0 until n / 2) {
          result += activePants(i)
          result += activePants(n - i - 1)
        }
        if (n % 2 == 1) {
          result += activePants(n / 2)
        }
        result.toSeq ++ byes
        
      case DrawOption.SwRandom =>
        val shuffledActive = scala.util.Random.shuffle(activePants)
        shuffledActive ++ byes
        
      case _ =>
        // SwManual / Unknown -> keep current order
        pants
    }
  }
}
