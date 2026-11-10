package shared.format

import scala.collection.mutable.{ ArrayBuffer }
import shared.model.*
import shared.basic.*
import shared.sport.{ Level, Sports }

/**
 * Helper object providing utility methods for validating, parsing, and calculating
 * scores, sets, and points for a stage's matches.
 */
object StageHelper {

  /**
   * Spielstufe der Standard-Sportart mit der Anzahl der Gewinnsätze der Stage. Eine negative Anzahl steht bei
   * Mannschaften für "alle n Spiele" (siehe [[TeamEncounter.winSetsCode]]).
   *
   * @param noSets number of winning sets required (`Stage.noWinSets` bzw. `MEntry.winSets`)
   * @return match level of the sport configuration
   */
  def matchLevel(noSets: Int): Level =
    if noSets < 0 then TeamEncounter.levelFromCode(noSets)
    else Sports.default.withToWin(noSets).matchLevel

  /**
   * Stand bei einem Freilos: Der Gegner des Freiloses gewinnt mit der nötigen Anzahl.
   *
   * @param winSets `winSets` des Eintrags
   * @param aIsBye  true, wenn Seite A das Freilos ist
   * @return Stand (A, B)
   */
  def byeSets(winSets: Int, aIsBye: Boolean): (Int, Int) =
    val n = math.abs(winSets)
    if aIsBye then (0, n) else (n, 0)

  /** Satzstufe (eine Ebene unter dem Spiel) der Standard-Sportart. */
  def setLevel: Level = Sports.default.levelBelowMatch(1).get

  /**
   * Inverts a ball score representation (e.g. swaps positive/negative sign).
   *
   * @param x the string representation of a ball score
   * @return the inverted ball score string
   */
  def invBall(x: String): String = { x(0) match { case '-' => x.substring(1); case '+' => "-" + x.substring(1); case _ => "-" + x } } 

  /**
   * Parses a ball score string into a tuple of points (Player A, Player B).
   *
   * @param b the ball score string
   * @return a tuple containing points for player A and player B
   */
  def getBallFromStr(b: String): (Int, Int) = setLevel.parseShort(b).getOrElse((-1, -1))

  /**
   * Calculates the total sum of ball points won by each player in a match.
   *
   * @param balls array of ball score strings
   * @param noSets number of winning sets required
   * @return a tuple containing total points for player A and player B
   */
  def getBalls(balls: Array[String], noSets: Int): (Int, Int) = {
    var res    = (0,0)
    var nASets = 0
    var nBSets = 0
    for (i <- 0 to balls.length-1) {
      val bs = getBallFromStr(balls(i))
      if (bs != (-1,-1) & nASets < noSets & nBSets < noSets) {
        res = res + bs
        if (bs._1 > bs._2) nASets += 1
        if (bs._2 > bs._1) nBSets += 1
      }  
    }
    (res)
  } 

  /**
   * Checks if the given set score is valid for a match.
   *
   * @param sets a tuple containing set score (Player A, Player B)
   * @param noSets number of winning sets required
   * @return true if the sets score is valid, false otherwise
   */
  def validSets(sets: (Int, Int), noSets: Int): Boolean = matchLevel(noSets).isValid(sets)

  /**
   * Checks if the given ball points tuple represents a valid set outcome.
   *
   * @param balls a tuple containing points in a set (Player A, Player B)
   * @return true if the ball points represent a valid set outcome, false otherwise
   */
  def validBalls(balls: (Int, Int)): Boolean = setLevel.isValid(balls)

  /**
   * Determines the set score outcome (1,0 or 0,1) for a single set based on ball points.
   *
   * @param balls a tuple containing ball points of a set
   * @return a tuple representing the set won (1,0 or 0,1), or (0,0) if invalid
   */
  def getSets(balls: (Int, Int)): (Int, Int) = 
    if (!validBalls(balls)) (0,0) else {
      if      (balls._1 > balls._2) (1,0) 
      else if (balls._2 > balls._1) (0,1)
      else                          (0,0)
    }

  /**
   * Calculates the sets won by each player in a match.
   *
   * @param balls array of ball score strings
   * @param noSets number of winning sets required
   * @return a tuple representing sets won by player A and player B
   */
  def getSets(balls: Array[String], noSets: Int): (Int, Int) = {
    var nASets = 0
    var nBSets = 0
    for (i <- 0 to balls.length-1) {
      val bs = getBallFromStr(balls(i))
      if (bs != (-1,-1) & nASets < noSets & nBSets < noSets) {
        if (bs._1 > bs._2) nASets += 1
        if (bs._2 > bs._1) nBSets += 1
      }  
    }
    (nASets, nBSets)
  }

  /**
   * Calculates points awarded to each player for the match based on ball scores.
   *
   * @param balls array of ball score strings
   * @param noSets number of winning sets required
   * @return a tuple representing points won (1,0 or 0,1 or 0,0)
   */
  def getPoints(balls: Array[String], noSets: Int): (Int, Int) = getPoints(getSets(balls, noSets), noSets)

  /**
   * Calculates points awarded to each player for the match based on sets score.
   *
   * @param sets a tuple containing sets won by each player
   * @param noSets number of winning sets required
   * @return a tuple representing points won (1,0 or 0,1 or 0,0)
   */
  def getPoints(sets: (Int, Int), noSets: Int): (Int, Int) =
    matchLevel(noSets).outcome(sets) match
      case 1  => (1, 0)
      case -1 => (0, 1)
      case _  => (0, 0)

  def convertToExcelColumn(num: Int): String = {
    if (num < 1) return "" // Wenn die Eingabezahl kleiner als 1 ist, geben wir einen leeren String zurück    
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    def convertToExcelColumnHelper(n: Int): StringBuilder = {
      var num = n
      val result = new StringBuilder
      
      while (num > 0) {
        num -= 1 // Da wir die Zahl um 1 reduzieren, muss der Modulo-Rest angepasst werden
        val remainder = num % 26
        result.append(alphabet(remainder))
        num /= 26
      }      
      result.reverse
    }
    convertToExcelColumnHelper(num).toString()
  }


  def cvrt2ExcelCol(grpId: Int): String = {
    if (grpId < 1) return "?" // Wenn die Eingabezahl kleiner als 1 ist, geben wir einen Fragezeichen-String zurück
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    
    def buildColumnName(n: Int): String = {
      var num = n
      var name = new StringBuilder
      
      while (num > 0) {
        num -= 1 // Da wir die Zahl um 1 reduzieren, muss der Modulo-Rest angepasst werden
        val remainder = num % 26
        name.append(alphabet(remainder))
        num /= 26
      } 
      name.reverse.toString()
    }      
    buildColumnName(grpId)
  }


  // target function for descrete optimization
  def getMinOccBestAvg(pant: Pant, grps: ArrayBuffer[Group], pantSize: Int, maxRating: Int, maxGrpSize: Int, pantAvgRating: Int): ArrayBuffer[Long] = {
    val result = ArrayBuffer.fill[Long](grps.size)(0)

    for (i <- 0 until grps.size) {
      result(i) = {
        if (grps(i).fillCnt == grps(i).size) {
          0L 
        } else {
          // calculate improvement of average rating 
          val curDiffRating = if (grps(i).avgRating==0) 0 else (pantAvgRating - grps(i).avgRating).abs 
          val newDiffRating = (pantAvgRating - ((grps(i).avgRating * grps(i).fillCnt + pant.getEffRating(pantAvgRating)) / (grps(i).fillCnt+1))).abs
          val improveRating = maxRating + (curDiffRating - newDiffRating)

          // calculate free level
          val freeGrpLevel = (grps(i).size - grps(i).fillCnt)
          //val freeGrpLevel = (maxGrpSize - grps(i).cnt)

          // calculate occu level
          val occuLevel    = (pantSize - grps(i).occu(pant.club)) 
          Log.debug(s"Pant: ${pant} improvementRating: ${improveRating} freeGrpLevel: ${freeGrpLevel } occuLevel: ${occuLevel}")

          ((1000*occuLevel) + freeGrpLevel) * (2 * maxRating) + improveRating 
        }
      }
    }
    result
  }


}
