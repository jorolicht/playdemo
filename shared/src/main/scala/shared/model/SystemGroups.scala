package shared.model

import scala.collection.mutable.{ ArrayBuffer, HashMap, Map }

import shared.basic.Pickle.{ReadWriter => RW, macroRW, *}

import shared.model.MEntryGr
import shared.model.Pant
import shared.basic.AppError


case class GroupConfig(id: Int, name: String, size: Int, quali: Int, pos: Int)

case class GroupEntry(
  var valid:    Boolean, 
  var points:   (Int,Int), 
  var sets:     (Int,Int), 
  var ballDiff: (Int,Int),
  var balls:    Array[String]
) {
  
  // def toResultEntry(pos: (Int, Int), sno: (String, String)) =  ResultEntry(valid, pos, sno, sets, balls)
  // def invert = new GroupEntry(valid,(points._2,points._1),(sets._2,sets._1),(ballDiff._2,ballDiff._1),balls.map(invBall(_)))
}

object GroupEntry {

  def apply(valid: Boolean): GroupEntry =  GroupEntry(false,(0,0),(0,0),(0,0),Array[String]())

  // def fromResultEntry(re: ResultEntry, noSets: Int): Either[AppError, GroupEntry] = 
  //   try Right( if   (re.valid) GroupEntry(true, getPoints(re.sets, noSets), re.sets, getBalls(re.balls, noSets), re.balls)
  //              else            GroupEntry(false))
  //   catch { case _: Throwable => Left(AppError("GroupEntry.fromResultEntry")) }
}


/*
 * Group Definition for Table Tennis
 * val tup2     = """\((\d+),(\d+)\)""".r 
 */
class Group(val grId: Int, val size: Int, val quali: Int, val name: String, noWinSets: Int) {
  var pants      = Array.fill[Pant](size) (Pant(SNO.nn))                     
  val results    = Array.fill[GroupEntry](size, size) (GroupEntry(false, (0,0), (0,0), (0,0), Array("")))
  var points     = Array.fill[(Int, Int)](size) ((0,0))
  var sets       = Array.fill[(Int, Int)](size) ((0,0))
  var balls      = Array.fill[(Int, Int)](size) ((0,0))

  // var points     = Array.ofDim[(Int, Int)](size)
  // var sets       = Array.ofDim[(Int, Int)](size)
  // var balls      = Array.ofDim[(Int, Int)](size) 

  // helper info
  var drawPos       = 0        // start position of group for draw 
  var fillCnt: Int  = 0 
  var avgRating:Int = 0
  var occu: Map[String, Int] = Map[String, Int]().withDefaultValue(0)
}
