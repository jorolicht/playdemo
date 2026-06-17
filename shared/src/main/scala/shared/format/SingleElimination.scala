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

  def rnds: Int = if (size >= 2) (scala.math.log(size) / scala.math.log(2)).round.toInt else 0

/**
 * Companion object for [[KoStage]] providing serialization.
 */
object KoStage:
  implicit def rw: RW[KoStage] = macroRW

/**
 * Companion object for Single Elimination format providing initialization logic.
 */
object SingleElimination {
  /**
   * Initializes a KnockoutStage stage data structure with a new KoStage.
   */
  def draw(id: Int, name: String, coId: Long, noWinSets: Int, selectedPants: Seq[Pant], drawOption: DrawOption = DrawOption.Unknown): StageData.KnockoutStage = {
    val state = KoStage(id, name, coId, noWinSets)
    state.size = selectedPants.length
    selectedPants.foreach(p => state.pants += p)
    StageData.KnockoutStage(state)
  }

  /**
   * Initializes matches for a Knockout/SingleElimination system.
   */
  def initKoMatches(
    coId: CompId,
    coTyp: CompTyp,
    stageId: StageId,
    stageFormat: StageFormat,
    noWinSets: Int,
    ko: KoStage
  ): Either[shared.basic.AppError, Seq[MEntry]] = {
    import scala.util.control.NonFatal
    val matchesBuf = ArrayBuffer[MEntry]()
    var err = shared.basic.AppError.dummy
    var gameNo = 0
    var byeCount = 0

    val rnds = ko.rnds
    try {
      for (r <- rnds to 0 by -1) {
        val matchesPerRound = if (r == 0) 1 else scala.math.pow(2, r - 1).toInt
        for (m <- 1 to matchesPerRound) {
          gameNo = gameNo + 1
          if (r == rnds) {
            // first/highest round initialize with participants
            val pantNo = (m - 1) * 2
            val pantA = if (pantNo < ko.pants.length) ko.pants(pantNo).id else SNO.nn
            val pantB = if (pantNo + 1 < ko.pants.length) ko.pants(pantNo + 1).id else SNO.nn
            val byeStatus = (pantA.isBye, pantB.isBye)
            val mtch = byeStatus match {
              case (false, false) => MEntryKo.init(coId, coTyp, stageId, stageFormat, pantA, pantB, gameNo, r, m, "", "", MEntry.MS_READY, (0,0), noWinSets)
              case (false, true)  =>
                byeCount = byeCount + 1
                MEntryKo.init(coId, coTyp, stageId, stageFormat, pantA, pantB, gameNo, r, m, "", "", MEntry.MS_FIX, (noWinSets, 0), noWinSets)
              case (true, false)  =>
                byeCount = byeCount + 1
                MEntryKo.init(coId, coTyp, stageId, stageFormat, pantA, pantB, gameNo, r, m, "", "", MEntry.MS_FIX, (0, noWinSets), noWinSets)
              case (true, true)   =>
                err = shared.basic.AppError("initKoMatches_invalid_ko_match", "both players are bye")
                MEntryKo.init(coId, coTyp, stageId, stageFormat, pantA, pantB, gameNo, r, m, "", "", MEntry.MS_UNKN, (0,0), noWinSets)
            }
            matchesBuf += mtch
          } else {
            matchesBuf += MEntryKo.init(coId, coTyp, stageId, stageFormat, SNO.nn, SNO.nn, gameNo, r, m, "", "", MEntry.MS_MISS, (0,0), noWinSets)
          }
        }
      }
      if (err.isDummy) Right(matchesBuf.toSeq) else Left(err)
    } catch {
      case NonFatal(e) =>
        Left(shared.basic.AppError("stage.initKoMatches.failed", e.getMessage))
    }
  }
}
