package shared.format

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import shared.basic.Pickle.{ReadWriter => RW, macroRW, *}
import shared.basic.Log
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
  var stages: Int = 0,
  var size: Int = 0,
  var pants: ArrayBuffer[Pant] = ArrayBuffer.empty,
  var results: ArrayBuffer[ResultEntry] = ArrayBuffer.empty,
  var sno2pos: scala.collection.mutable.Map[String, Int] = scala.collection.mutable.Map.empty
):

  def rnds: Int = if (size >= 2) (scala.math.log(size) / scala.math.log(2)).round.toInt else 0

  def initDraw_Grp(participants: ArrayBuffer[Pant], dInfo: ArrayBuffer[(String, Int, Int, Int)]): Either[shared.basic.AppError, Int] = {
    var drawInfo = ArrayBuffer[(String, Int, Int, Int)]()

    def changeUpDown(invert: Boolean, value: Boolean): Boolean = if (invert) !value else value

    size = KoRound.getSize(participants.size)
    stages = KoRound.getNoRounds(size)

    if (size == 0 || stages == 0) {
      Log.error(s"initDraw_Grp -> invalid size or stages") 
      Left(shared.basic.AppError("err0243.systemKO.draw.size"))
    } else {
      val upDownScheme = List.fill(size/4)(List(true, false, false, true)).flatten.toArray
      val settingPositions = KoRound.genSettingPositions(size)

      val upDownMap = HashMap[(Int,Int), Boolean]()
      val minPos = if (dInfo.nonEmpty) dInfo.minBy(_._3)._3 else 1

      dInfo.zip(upDownScheme).foreach { case (item, updo) => if (item._3 == minPos) upDownMap += ((item._2, minPos) -> updo) } 
      
      dInfo.zip(upDownScheme).foreach { case (item, updo) => 
        if (!upDownMap.contains((item._2, minPos))) {
          upDownMap += ((item._2, item._3) -> updo)
          Log.error(s"initDraw_Grp -> upDownMap does not contain all values e.g. ${item._2} ${minPos}")  
        } else {
          upDownMap += ( (item._2, item._3) -> changeUpDown( (item._3-minPos)%2 == 1, upDownMap((item._2, minPos))) )   
        }
      } 

      val pantsWithDInfo = participants.zip(dInfo)
      for(i <- 0 until pantsWithDInfo.size) {
        pantsWithDInfo(i)._1.qInfo = s"${pantsWithDInfo(i)._2._1} [${pantsWithDInfo(i)._2._3}]" 
      }

      pants              = ArrayBuffer.tabulate(size)(i => Pant(SNO.bye(i), name = "")) 
      drawInfo           = ArrayBuffer.fill(size) (("", 0, 0, 0)) 

      val (upList, downList) = pantsWithDInfo.partition(x => upDownMap.getOrElse((x._2._2, x._2._3), true))
      
      val upListBuf = upList.to(ArrayBuffer)
      val downListBuf = downList.to(ArrayBuffer)

      for (i <- 0 until size) {
        val pos = settingPositions(i+1)
        val up  = (pos <= size/2)

        if (up && upListBuf.nonEmpty) { 
          pants(pos-1) = upListBuf(0)._1
          drawInfo(pos-1) = upListBuf(0)._2
          upListBuf.remove(0)
        } else if (!up && downListBuf.nonEmpty) { 
          pants(pos-1) = downListBuf(0)._1
          drawInfo(pos-1) = downListBuf(0)._2
          downListBuf.remove(0) 
        } else if (up && downListBuf.nonEmpty) { 
          pants(pos-1) = downListBuf(0)._1
          drawInfo(pos-1) = downListBuf(0)._2
          downListBuf.remove(0) 
        } else if (!up && upListBuf.nonEmpty) {  
          pants(pos-1) = upListBuf(0)._1
          drawInfo(pos-1) = upListBuf(0)._2
          upListBuf.remove(0)
        }
      }

      if (upListBuf.nonEmpty || downListBuf.nonEmpty) {
        Log.error(s"initDraw -> upList.size or downList.size > 0") 
        Left(shared.basic.AppError("err0244.systemKO.draw.updown"))
      } else {  
        sno2pos = scala.collection.mutable.Map[String, Int]()
        for (i <- 0 until size) sno2pos += (pants(i).id.toString -> i) 
        Right(size) 
      }
    }
  }

  def initDraw_Rating(participants: ArrayBuffer[Pant]): Either[shared.basic.AppError, Int] = {
    import shared.model.DrawTree
    
    size = KoRound.getSize(participants.size)
    stages = KoRound.getNoRounds(size)
    
    val dT = DrawTree.get[String](1, size, "")

    if (size == 0 || !DrawTree.isPowerOfTwo(size) || stages == 0) {
      Log.error(s"initDraw_Rating -> invalid size: ${size} or stages: ${stages}") 
      Left(shared.basic.AppError("err0243.systemKO.draw.size"))
    } else {
      pants = ArrayBuffer.tabulate(size)(i => Pant(SNO.bye(i), name = ""))
      val settingPositions = KoRound.genSettingPositions(size)
      for (i <- 1 to size by 2) {
        val pant1 = if (i - 1 < participants.size) participants(i-1) else Pant(SNO.bye(i-1), name = "")
        val pant2 = if (i < participants.size) participants(i) else Pant(SNO.bye(i), name = "")
        val pos1 = settingPositions(i)
        val pos2 = settingPositions(i+1)  

        val occEven = dT.getOcc(dT, pos1, pant1.club) + dT.getOcc(dT, pos2, pant2.club)
        val occOdd  = dT.getOcc(dT, pos1, pant2.club) + dT.getOcc(dT, pos2, pant1.club)

        if (dT.check4BestDrawPos(dT, pos1, pant1.club, pos2, pant2.club)) {
          pants(pos1-1) = pant1
          pants(pos2-1) = pant2 
          dT.addItem(dT, pos1, pant1.club)
          dT.addItem(dT, pos2, pant2.club)
        } else {
          pants(pos1-1) = pant2
          pants(pos2-1) = pant1
          dT.addItem(dT, pos1, pant2.club)
          dT.addItem(dT, pos2, pant1.club)          
        }
      }
      DrawTree.treePrint(Some(dT))
      sno2pos = scala.collection.mutable.Map[String, Int]()
      for (i <- 0 until size) sno2pos += (pants(i).id.toString -> i) 
      Right(size) 
    }
  }

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
   *
   * @param stage the stage context containing configuration and metadata
   * @param coTyp the competition type
   * @param cfg the stage config representing format options
   * @param selectedPants the list of selected players/participants for the draw
   * @param drawOption options for drawing (e.g. initial start or after a previous group stage)
   * @return the initialized KnockoutStage stage data
   */
  def draw(stage: Stage, coTyp: CompTyp, cfg: StageConfig, selectedPants: Seq[Pant], drawOption: DrawOption = DrawOption.Unknown): StageData.KnockoutStage = {
    val state = KoStage(stage.id.value, stage.name, stage.coId.value.toLong, stage.noWinSets)

    val participants = selectedPants.to(ArrayBuffer)
    val drawRes = drawOption match {
      case DrawOption.KoAfterGrp =>
        val dInfo = participants.map { p =>
          val parts = p.qInfo.split(";")
          if (parts.length >= 3) {
            (parts(0), parts(1).toInt, parts(2).toInt, 0)
          } else {
            ("", 0, 0, 0)
          }
        }
        state.initDraw_Grp(participants, dInfo)
      case _ =>
        state.initDraw_Rating(participants)
    }

    drawRes match {
      case Left(err) =>
        Log.error(s"Drawing failed: ${err.msg}")
      case Right(_) =>
        // Success
    }

    val koStage: StageData.KnockoutStage = StageData.KnockoutStage(state)

    initKoMatches(stage.coId, coTyp, stage.id, cfg.format, stage.noWinSets, state) match {
      case Right(koMatches) =>
        stage.matches.clear()
        stage.matches ++= koMatches
        stage.data = koStage
        koStage
      case Left(err) =>
        Log.error(s"Error initializing knockout matches: ${err.msg}")
        stage.data = koStage
        koStage
    }
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
                MEntryKo.init(coId, coTyp, stageId, stageFormat, pantA, pantB, gameNo, r, m, "", "", MEntry.MS_FIX, (noWinSets, 0), noWinSets, result = "")
              case (true, false)  =>
                byeCount = byeCount + 1
                MEntryKo.init(coId, coTyp, stageId, stageFormat, pantA, pantB, gameNo, r, m, "", "", MEntry.MS_FIX, (0, noWinSets), noWinSets, result = "")
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
      
      // propagate bye matches
      var changed = true
      while (changed) {
        changed = false
        matchesBuf.foreach {
          case m: MEntryKo =>
            if (m.finished) {
              val winnerSno = m.getWinner()
              val loserSno = m.getLooser()
              
              if (winnerSno != SNO.nn) {
                val (nextGameNo, nextPos) = m.getWinPos()
                if (nextGameNo > 0) {
                  matchesBuf.find(_.gameNo == nextGameNo).foreach {
                    case nm: MEntryKo =>
                      val currentSno = if (nextPos == 0) nm.stNoA else nm.stNoB
                      if (currentSno != winnerSno) {
                        nm.setPant(nextPos, winnerSno)
                        changed = true
                      }
                    case _ =>
                  }
                }
              }
              
              if (loserSno != SNO.nn) {
                val (nextLooGameNo, nextLooPos) = m.getLooPos()
                if (nextLooGameNo > 0) {
                  matchesBuf.find(_.gameNo == nextLooGameNo).foreach {
                    case nm: MEntryKo =>
                      val currentSno = if (nextLooPos == 0) nm.stNoA else nm.stNoB
                      if (currentSno != loserSno) {
                        nm.setPant(nextLooPos, loserSno)
                        changed = true
                      }
                    case _ =>
                  }
                }
              }
            }
            
            if (m.stNoA.isNN || m.stNoB.isNN) {
              val targetStatus = MEntry.MS_MISS
              if (m.status != targetStatus || m.sets != (0, 0) || m.result != "") {
                m.status = targetStatus
                m.setSets((0, 0))
                m.setResult("")
                changed = true
              }
            } else if (!m.finished && m.status != MEntry.MS_RUN) {
              val aReady = m.stNoA != SNO.nn
              val bReady = m.stNoB != SNO.nn
              val targetStatus = if (aReady && bReady) {
                if (m.stNoA.isBye || m.stNoB.isBye) {
                  val (setsA, setsB) = if (m.stNoA.isBye) (0, m.winSets) else (m.winSets, 0)
                  m.setSets((setsA, setsB))
                  m.setResult("")
                  MEntry.MS_FIX
                } else {
                  MEntry.MS_READY
                }
              } else {
                MEntry.MS_MISS
              }
              if (m.status != targetStatus) {
                m.status = targetStatus
                changed = true
              }
            }
          case _ =>
        }
      }

      if (err.isDummy) Right(matchesBuf.toSeq) else Left(err)
    } catch {
      case NonFatal(e) =>
        Left(shared.basic.AppError("stage.initKoMatches.failed", e.getMessage))
    }
  }

  def swapPlayers(stage: Stage, sno1: SNO, sno2: SNO): Unit = {
    stage.data match {
      case StageData.KnockoutStage(state) =>
        val idx1 = state.pants.indexWhere(p => p != null && p.id == sno1)
        val idx2 = state.pants.indexWhere(p => p != null && p.id == sno2)
        if (idx1 != -1 && idx2 != -1) {
          val temp = state.pants(idx1)
          state.pants(idx1) = state.pants(idx2)
          state.pants(idx2) = temp
          
          // Rebuild sno2pos map
          state.sno2pos = scala.collection.mutable.Map[String, Int]()
          for (i <- 0 until state.size) state.sno2pos += (state.pants(i).id.toString -> i)
        }
      case _ =>
    }
  }
}

object KoRound {
  def getSize(cntPlayer: Int): Int = cntPlayer match {
    case 2                          =>   2
    case x if (3  <= x && x <= 4)   =>   4
    case x if (5  <= x && x <= 8)   =>   8
    case x if (9  <= x && x <= 16)  =>  16
    case x if (17 <= x && x <= 32)  =>  32
    case x if (33 <= x && x <= 64)  =>  64
    case x if (65 <= x && x <= 128) => 128
    case _                          =>   0
  }

  def getNoRounds(noPlayers: Int): Int = {
    noPlayers match {  
      case a if 65 to 128 contains a => 7
      case b if 33 to  64 contains b => 6
      case c if 17 to  32 contains c => 5
      case d if  9 to  16 contains d => 4
      case e if  5 to   8 contains e => 3
      case f if  3 to   4 contains f => 2
      case g if  1 to   2 contains g => 1
      case _                         => 0
    }
  }

  def genPosField(posField: Array[Int], lowerBound: Int, upperBound: Int): Unit = {
    def firstLastSum(baseFieldSize: Int, fieldSize:Int): Int = {
      var value = 3
      var step = 2
      var tmp = baseFieldSize
      while(tmp > fieldSize) {
        value = value + step
        step = 2 * step
        tmp = tmp / 2
      }  
      value
    }  

    val fSize = posField.length - 1
    val curSize = upperBound - lowerBound + 1

    if (posField(lowerBound) == 0 && posField(upperBound) == 0) {
      posField(lowerBound) = 1 // START
      genPosField(posField, lowerBound, upperBound)
    } else if (posField(lowerBound) > 0) {
      posField(upperBound) = firstLastSum(fSize, curSize) - posField(lowerBound)
    } else if (posField(upperBound) > 0) {  
      posField(lowerBound) = firstLastSum(fSize, curSize) - posField(upperBound)
    }

    if (curSize > 2) {
       genPosField(posField, lowerBound,  lowerBound + (curSize / 2) - 1 )
       genPosField(posField, lowerBound + (curSize / 2), upperBound)
    }
  }  

  def genSettingPositions(fSize: Int): Array[Int] = {
    val posField = Array.fill(fSize+1)(0)
    val setPositions = Array.fill(fSize+1)(0)
    genPosField(posField, 1, fSize)
    for (i<-1 to fSize) { setPositions(posField(i)) = i }
    setPositions 
  }
}
