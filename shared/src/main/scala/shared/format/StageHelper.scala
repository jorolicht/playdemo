package shared.format

import shared.basic.*

/**
 * Helper object providing utility methods for validating, parsing, and calculating
 * scores, sets, and points for a stage's matches.
 */
object StageHelper {

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
  def getBallFromStr(b: String): (Int, Int) = {
    if      (b == "")              (-1,-1)  
    else if (b == "+0" | b == "0") (11,0) 
    else if (b == "-0")            (0,11)  
    else b.toIntOption.getOrElse(0) match {  
      case a if   10 to 500 contains a => (a+2, a)
      case b if    1 to   9 contains b => (11, b)
      case c if   -9 to  -1 contains c => (-c, 11)
      case d if -500 to -10 contains d => (-d, 2 - d) 
      case _                           => (-1,-1)
    }
  }

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
  def validSets(sets: (Int, Int), noSets: Int): Boolean = {
    (sets._1 == noSets & sets._2 < noSets) | (sets._1 < noSets & sets._2 == noSets)
  } 

  /**
   * Checks if the given ball points tuple represents a valid set outcome.
   *
   * @param balls a tuple containing points in a set (Player A, Player B)
   * @return true if the ball points represent a valid set outcome, false otherwise
   */
  def validBalls(balls: (Int, Int)): Boolean =  
    ( balls._1 >= (balls._2 + 2) & balls._1 == 11 & balls._2 >=0 ) |
    ( balls._1 == (balls._2 + 2) & balls._1 >  11)                 |
    ( balls._2 >= (balls._1 + 2) & balls._2 == 11 & balls._1 >=0 ) |
    ( balls._2 == (balls._1 + 2) & balls._2 >  11) 

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
  def getPoints(sets: (Int, Int), noSets: Int): (Int, Int) = {
    if      (sets._1 == noSets & sets._2 < noSets) { (1,0) }
    else if (sets._2 == noSets & sets._1 < noSets) { (0,1) } 
    else                                           { (0,0) }
  } 

}
