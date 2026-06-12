package shared.format

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import shared.basic.Pickle.{ReadWriter => RW, macroRW, *}
import shared.model.*

/**
 * Represents a knockout stage (single elimination format) of a competition.
 *
 * @param id the identifier of the knockout stage
 * @param name the name of the knockout stage
 * @param coId the competition ID associated with this stage
 * @param noWinSets number of winning sets required
 * @param stages number of sub-stages or rounds within the KO stage
 */
case class KoStage(
  id: Int,
  name: String,
  coId: Long,
  noWinSets: Int,
  stages: Int = 0
):
  var size: Int = 0
  var pants: ArrayBuffer[Pant] = ArrayBuffer.empty
  var results: ArrayBuffer[ResultEntry] = ArrayBuffer.empty
  var sno2pos: scala.collection.mutable.Map[String, Int] = scala.collection.mutable.Map.empty

/**
 * Companion object for [[KoStage]] providing serialization.
 */
object KoStage:
  implicit def rw: RW[KoStage] = macroRW
