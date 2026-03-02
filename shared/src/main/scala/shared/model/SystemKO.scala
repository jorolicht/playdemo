package shared.model

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import upickle.default._
import upickle.default.{ReadWriter => RW, macroRW}

case class KoRound(
  id: Int,
  name: String,
  coId: Long,
  noWinSets: Int,
  rnds: Int = 0
):
  var size: Int = 0
  var pants: ArrayBuffer[Pant] = ArrayBuffer.empty
  var results: ArrayBuffer[ResultEntry] = ArrayBuffer.empty
  var sno2pos: scala.collection.mutable.Map[String, Int] = scala.collection.mutable.Map.empty

object KoRound:
  implicit def rw: RW[KoRound] = macroRW
