package shared.model

import upickle.default._
import upickle.default.{ReadWriter => RW, macroRW}

import shared.model.{ MEntry, MEntryGr, MEntryKo, MEntryBase }
import shared.basic.AppError


/** 
 * Result entry for group and ko matches   
 */
case class ResultEntry(
  var valid: Boolean, 
  var pos:   (Int,Int),                // KO:     pos._1 = rnd, pos._2 = match number
                                       // GROUP:  pos._1 = 1..size, pos._2 = 1..size (wgw)
  var sno:   (String, String),
  var sets:  (Int,Int),                // sets and balls with
  var balls: Array[String]             // view from player A
) {
  override def toString = s"ResultEntry valid:${valid} pos:${pos} sno:${sno} sets:${sets} balls:${balls.mkString(":")}" 
}

object ResultEntry {
  implicit def rw: RW[ResultEntry] = macroRW

  // def fromMatchEntry(mEntry: MEntry): ResultEntry = {
  //   mEntry.coPhTyp match {
  //     case CompPhaseTyp.GR => {
  //       val m = mEntry.asInstanceOf[MEntryGr]
  //       ResultEntry(m.status >= 2 & m.validSets(), m.wgw, (m.stNoA,m.stNoB), m.sets, m.result.split('·'))
  //     }  
  //     case CompPhaseTyp.KO => {
  //       val m = mEntry.asInstanceOf[MEntryKo]
  //       ResultEntry(m.status >= 2 & m.validSets(), (m.round, m.maNo), (m.stNoA,m.stNoB), m.sets, m.result.split('·'))
  //     }  
  //     case _      => ResultEntry(false, (0,0), ("",""), (0,0), Array(""))
  //   }
  // }

  // def decSeq(reEntStr: String): Either[AppError, Seq[ResultEntry]] = {
  //   try Right(read[Seq[ResultEntry]](reEntStr))  
  //   catch { case _: Throwable => Left(AppError("err0147.decode.ResultEntrys", reEntStr.take(20), "", "ResultEntry.decSeq")) }
  // }

}
