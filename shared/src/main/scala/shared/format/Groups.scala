package shared.format

import scala.collection.mutable.{ ArrayBuffer, HashMap, Map }
import shared.basic.Pickle.{ReadWriter => RW, macroRW, *}
import shared.model.*
import shared.basic.AppError
import StageHelper.*

case class GroupConfig(id: Int, name: String, size: Int, quali: Int, pos: Int)

case class GroupEntry(
  var valid:    Boolean, 
  var points:   (Int,Int), 
  var sets:     (Int,Int), 
  var ballDiff: (Int,Int),
  var balls:    Array[String]
) {
  
  def toResultEntry(pos: (Int, Int), sno: (String, String)) =  ResultEntry(valid, pos, sno, sets, balls)
  def invert = new GroupEntry(valid,(points._2,points._1),(sets._2,sets._1),(ballDiff._2,ballDiff._1),balls.map(invBall(_)))
}

object GroupEntry {
  given rw: RW[GroupEntry] = macroRW

  def apply(valid: Boolean): GroupEntry =  GroupEntry(false,(0,0),(0,0),(0,0),Array[String]())

  // def fromResultEntry(re: ResultEntry, noSets: Int): Either[AppError, GroupEntry] = 
  //   try Right( if   (re.valid) GroupEntry(true, getPoints(re.sets, noSets), re.sets, getBalls(re.balls, noSets), re.balls)
  //              else            GroupEntry(false))
  //   catch { case _: Throwable => Left(AppError("GroupEntry.fromResultEntry")) }
}


/**
 * Group Definition for Table Tennis.
 * Represents a group of participants playing in a competition stage.
 *
 * @param grId the group identifier
 * @param size the size of the group
 * @param quali number of qualifying players from this group
 * @param name the name of the group
 * @param noWinSets number of winning sets required
 * @param pants participants in the group
 * @param results matching matrix results between players
 * @param points calculated group points for each player
 * @param sets calculated sets won/lost for each player
 * @param balls calculated ball points won/lost for each player
 * @param drawPos start position of the group for the draw
 * @param fillCnt count of players currently filled in the group
 * @param avgRating average rating of the group
 * @param occu helper map for tracking player associations
 */
case class Group(
  grId: Int,
  size: Int,
  quali: Int,
  name: String,
  noWinSets: Int,
  var pants: Array[Pant],
  var results: Array[Array[GroupEntry]],
  var points: Array[(Int, Int)],
  var sets: Array[(Int, Int)],
  var balls: Array[(Int, Int)],
  var drawPos: Int = 0,
  var fillCnt: Int = 0,
  var avgRating: Int = 0,
  var occu: Map[String, Int] = Map[String, Int]().withDefaultValue(0)
)

object Group {
  given rw: RW[Group] = macroRW

  /**
   * Helper apply method to construct a Group with initialized default arrays.
   */
  def apply(grId: Int, size: Int, quali: Int, name: String, noWinSets: Int): Group =
    new Group(
      grId = grId,
      size = size,
      quali = quali,
      name = name,
      noWinSets = noWinSets,
      pants = Array.fill[Pant](size)(Pant(SNO.nn)),
      results = Array.fill[GroupEntry](size, size)(GroupEntry(false, (0,0), (0,0), (0,0), Array(""))),
      points = Array.fill[(Int, Int)](size)((0,0)),
      sets = Array.fill[(Int, Int)](size)((0,0)),
      balls = Array.fill[(Int, Int)](size)((0,0))
    )
}

/**
 * Companion object for Groups format providing initialization logic.
 */
object Groups {
  /**
   * Initializes a GroupsStage stage data structure with distributed groups.
   */
  def init(cfg: StageConfig, selectedPants: Seq[Pant], noWinSets: Int): StageData.GroupsStage = {
    val dist = shared.utils.DrawRules.calculateDistribution(cfg, selectedPants.length)
    var currentPants = selectedPants
    val buf = ArrayBuffer.empty[Group]
    dist.zipWithIndex.foreach { case (size, i) =>
      val groupPants = currentPants.take(size)
      currentPants = currentPants.drop(size)
      val g = Group(i + 1, size, 2, s"Gruppe ${i + 1}", noWinSets)
      groupPants.zipWithIndex.foreach { case (p, j) => g.pants(j) = p }
      buf += g
    }
    StageData.GroupsStage(buf)
  }
}
