package shared.format

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import shared.basic.Pickle.{ReadWriter => RW, macroRW, *}
import shared.basic.Log
import shared.model.*

case class SwissPlayer(id: Int, club: Int, rating: Int, scoreGroup: Int, sno: SNO) {
  override def toString: String = s"SwissPlayer(id=$id, club=$club, rating=$rating, scoreGroup=$scoreGroup, sno=$sno)"
}

case class SwissPair(id : (Int, Int), sets: (Int,Int), points: (Int,Int))

object SwissPair:
  given rw: RW[SwissPair] = macroRW

object SwissPlayer:
  given rw: RW[SwissPlayer] = macroRW

/**
 * Represents a swiss stage of a competition.
 *
 * @param id the identifier of the swiss stage
 * @param name the name of the swiss stage
 * @param coId the competition ID associated with this stage
 * @param noWinSets number of winning sets required
 * @param size the size configuration (number of players/participants)
 */
case class SwissSys(
  val id:         Long,
  val name:       String,
  val coId:       Int,
  val noWinSets:  Int,
  var size:       Int = 0,
  var pairing:    ArrayBuffer[ArrayBuffer[SwissPair]] = ArrayBuffer.empty,
  var swPants:    ArrayBuffer[SwissPlayer] = ArrayBuffer.empty,
  var sno2pos:    scala.collection.mutable.Map[SNO, Int] = scala.collection.mutable.Map.empty
)

/**
 * Companion object for Swiss System format providing initialization logic.
 */
object SwissSys:
  given rw: RW[SwissSys] = macroRW

  /**
   * Initializes a Swiss System stage data structure with a new SwissSys instance.
   *
   * @param stage the stage context containing configuration and metadata
   * @param coTyp the competition type
   * @param cfg the stage config representing format options
   * @param selectedPants the list of selected players/participants for the draw
   * @param drawOption options for drawing (e.g. swiss start options)
   * @return the initialized SwissSys stage data
   */
  def draw(stage: Stage, coTyp: CompTyp, cfg: StageConfig, selectedPants: Seq[Pant], drawOption: DrawOption = DrawOption.Unknown): StageData.SwissStage = {
    val state = SwissSys(stage.id.value, stage.name, stage.coId.value, stage.noWinSets)
    val sortedActivePants = selectedPants.sortBy(-_.rating)

    state.swPants = ArrayBuffer.tabulate(sortedActivePants.length)(i => {
      val p = sortedActivePants(i)
      if (p.id.isBye || p.id.isNN) {
        SwissPlayer(i, 0, 0, 0, p.id)
      } else {
        SwissPlayer(i, p.clubId, p.rating, 0, p.id)
      }
    })
    for (i <- 0 until sortedActivePants.size) state.sno2pos += (state.swPants(i).sno -> i) 
    state.size = state.swPants.length

    //Initialisze the pairing structure with empty pairs for round 1
    state.pairing = ArrayBuffer(ArrayBuffer.empty[SwissPair])
    // Initialize the pairing according to the draw option
    state.pairing(0) = initPairing(state.swPants, drawOption)


    //Debug Ausgabe von swPants
    for (swPlayer <- state.swPants) {
      Log.debug(s"SwissPlayer: ${swPlayer.id}, Club: ${swPlayer.club}, Rating: ${swPlayer.rating}, ScoreGroup: ${swPlayer.scoreGroup}")
    }

    StageData.SwissStage(state)
  }


  def swapPlayers(stage: Stage, sno1: SNO, sno2: SNO): Unit = {
    stage.data match {
      case StageData.SwissStage(state) =>
        val idx1 = state.swPants.indexWhere(p => p != null && p.sno == sno1)
        val idx2 = state.swPants.indexWhere(p => p != null && p.sno == sno2)
        if (idx1 != -1 && idx2 != -1) {
          val p1 = state.swPants(idx1)
          val p2 = state.swPants(idx2)
          state.swPants(idx1) = p1.copy(sno = p2.sno)
          state.swPants(idx2) = p2.copy(sno = p1.sno)
          
          // Rebuild sno2pos map
          state.sno2pos.clear()
          for (i <- 0 until state.size) state.sno2pos += (state.swPants(i).sno -> i)
        }
      case _ =>
    }
  }

  def swapPairing(stage: Stage, round: Int, sno1: SNO, sno2: SNO): Unit = {
    stage.data match {
      case StageData.SwissStage(state) =>
        if (round - 1 >= 0 && round - 1 < state.pairing.length) {
          val pairs = state.pairing(round - 1)
          
          val pIdx1 = state.swPants.indexWhere(p => p != null && p.sno == sno1)
          val pIdx2 = state.swPants.indexWhere(p => p != null && p.sno == sno2)
          
          val searchIdx1 = if (sno1.isBye) -1 else pIdx1
          val searchIdx2 = if (sno2.isBye) -1 else pIdx2
          
          var pairIdx1 = -1
          var isPos1A = true
          var pairIdx2 = -1
          var isPos2A = true
          
          for (i <- pairs.indices) {
            val pair = pairs(i)
            if (pair.id._1 == searchIdx1) {
              pairIdx1 = i
              isPos1A = true
            } else if (pair.id._2 == searchIdx1) {
              pairIdx1 = i
              isPos1A = false
            }
            
            if (pair.id._1 == searchIdx2) {
              pairIdx2 = i
              isPos2A = true
            } else if (pair.id._2 == searchIdx2) {
              pairIdx2 = i
              isPos2A = false
            }
          }
          
          if (pairIdx1 != -1 && pairIdx2 != -1) {
            val pair1 = pairs(pairIdx1)
            val pair2 = pairs(pairIdx2)
            
            val newId1 = if (isPos1A) (searchIdx2, pair1.id._2) else (pair1.id._1, searchIdx2)
            val newId2 = if (isPos2A) (searchIdx1, pair2.id._2) else (pair2.id._1, searchIdx1)
            
            pairs(pairIdx1) = pair1.copy(id = newId1)
            pairs(pairIdx2) = pair2.copy(id = newId2)
          }
        }
      case _ =>
    }
  }

  // Updates the pairing for a specific round > 1 in the Swiss System stage.
  def setParing(stage: Stage, rnd: Int, newPairing: ArrayBuffer[SwissPair]): Unit = {
    println(s"setParing: rnd=$rnd, newPairing=$newPairing")
    stage.data match {
      case StageData.SwissStage(state) =>
        if (rnd - 1 < state.pairing.length) {
          state.pairing(rnd - 1) = newPairing
        } else {
          // If the round does not exist yet, we can add it
          while (state.pairing.length < rnd) {
            state.pairing += ArrayBuffer.empty[SwissPair]
          }
          state.pairing(rnd - 1) = newPairing
        }
      case _ =>
    }
  }


  // intializes the pairing for the first round based on the draw option
  def initPairing(swPants: ArrayBuffer[SwissPlayer], drawOption: DrawOption): ArrayBuffer[SwissPair] = {
    val pairs = ArrayBuffer.empty[SwissPair]
    val activePlayers = swPants.filter(p => p.sno != SNO.nn && !p.sno.isBye)

    drawOption match {
      case DrawOption.SwUpperLower =>
        val half = (activePlayers.length + 1) / 2
        val (upper, lower) = activePlayers.splitAt(half)
        for (i <- upper.indices) {
          if (i < lower.length) {
            pairs += SwissPair((upper(i).id, lower(i).id), (0, 0), (0, 0))
          } else {
            pairs += SwissPair((upper(i).id, -1), (0, 0), (0, 0)) // Bye for the last player in upper half
          }
        }

      case DrawOption.SwAccel =>
        val halfSize = (activePlayers.length + 1) / 2
        val (g1, g2) = activePlayers.splitAt(halfSize)
        for (i <- g1.indices) {
          if (i < g2.length) {
            pairs += SwissPair((g1(i).id, g2(i).id), (0, 0), (0, 0))
          } else {
            pairs += SwissPair((g1(i).id, -1), (0, 0), (0, 0)) // Bye for the last player in g1
          }
        }

      case DrawOption.SwTopBottom =>
        val n = activePlayers.length
        val half = (n + 1) / 2
        for (i <- 0 until half) {
          val opposite = n - 1 - i
          if (i < opposite) {
            pairs += SwissPair((activePlayers(i).id, activePlayers(opposite).id), (0, 0), (0, 0))
          } else if (i == opposite) {
            pairs += SwissPair((activePlayers(i).id, -1), (0, 0), (0, 0)) // Bye
          }
        }

      case DrawOption.SwRandom =>
        val shuffled = scala.util.Random.shuffle(activePlayers)
        for (i <- 0 until shuffled.length by 2) {
          if (i + 1 < shuffled.length) {
            pairs += SwissPair((shuffled(i).id, shuffled(i + 1).id), (0, 0), (0, 0))
          } else {
            pairs += SwissPair((shuffled(i).id, -1), (0, 0), (0, 0)) // Bye
          }
        }

      case _ =>
        // Fallback to SwUpperLower to ensure pairings are never empty
        val half = (activePlayers.length + 1) / 2
        val (upper, lower) = activePlayers.splitAt(half)
        for (i <- upper.indices) {
          if (i < lower.length) {
            pairs += SwissPair((upper(i).id, lower(i).id), (0, 0), (0, 0))
          } else {
            pairs += SwissPair((upper(i).id, -1), (0, 0), (0, 0)) // Bye for the last player in upper half
          }
        }
    }
    pairs
  } 


  /**
   * Initializes matches for a Swiss system.
   */
  def initSwMatches(
    coId: CompId,
    coTyp: CompTyp,
    stageId: StageId,
    stageFormat: StageFormat,
    noWinSets: Int,
    rnd: Int,
    state: SwissSys
  ): Either[shared.basic.AppError, Seq[MEntry]] = {
    try {
      val buf     = ArrayBuffer[MEntry]()
      val noPairs = state.pairing(rnd - 1).length
      for (i <- 0 until noPairs) {
        val maNo = i + 1
        val gameNo = (rnd - 1) * noPairs + maNo
        val id1 = state.pairing(rnd - 1)(i).id._1
        val id2 = state.pairing(rnd - 1)(i).id._2
        val sno1 = state.swPants(id1).sno 
        val sno2 = if (id2 == -1) SNO.bye(state.swPants.length) else state.swPants(id2).sno
        val isBye = sno1.isBye || sno2.isBye
        val (status, sets) = if (isBye) {
          val setsVal = if (sno1.isBye) (0, noWinSets) else (noWinSets, 0)
          (MEntry.MS_FIX, setsVal)
        } else {
          (MEntry.MS_READY, (0, 0))
        }

        buf += MEntrySw.init(
          coId        = coId,
          coTyp       = coTyp,
          stageId     = stageId,
          stageFormat = stageFormat,
          stNoA       = sno1,
          stNoB       = sno2,
          gameNo      = gameNo,
          round       = rnd,
          maNo        = maNo,
          status      = status,
          sets        = sets,
          winSets     = noWinSets
        )
      }
      Right(buf.toSeq)
    } catch {
      case e: Exception => Left(shared.basic.AppError("error.init_swiss_matches_failed", e.getMessage))
    }
  }

  /**
   * Calculates pairings for the subsequent Swiss round, stores them in the stage data,
   * sets the stage status to AUS, and returns the modified stage.
   */
  def generateNextRoundPairing(stage: Stage): Either[shared.basic.AppError, Stage] = {
    stage.data match {
      case StageData.SwissStage(swissState) =>
        try {
          // 1. Calculate wins for each player from stage.matches to update their scoreGroup
          val winsMap = scala.collection.mutable.Map[SNO, Int]().withDefaultValue(0)
          stage.matches.filter(_.finished).foreach { m =>
            if (m.sets._1 > m.sets._2) winsMap(m.stNoA) += 1
            else if (m.sets._2 > m.sets._1) winsMap(m.stNoB) += 1
          }
          
          // 2. Update players' scoreGroup
          val updatedPlayers = swissState.swPants.map { sp =>
            sp.copy(scoreGroup = winsMap(sp.sno))
          }
          swissState.swPants = updatedPlayers
          
          // 3. Build history
          val history = stage.matches.filter(_.finished).flatMap { m =>
            val idxA = swissState.sno2pos.get(m.stNoA)
            val idxB = swissState.sno2pos.get(m.stNoB)
            (idxA, idxB) match {
              case (Some(a), Some(b)) => Seq((a, b), (b, a))
              case (Some(a), None) if m.stNoB.isBye => Seq((a, -1), (-1, a))
              case (None, Some(b)) if m.stNoA.isBye => Seq((-1, b), (b, -1))
              case _ => Seq.empty
            }
          }.toSet
          
          // 4. Compute next pairings using SwissMatcher
          val nextPairings = SwissMatcher.computePairings(swissState.swPants.toSeq, history)
          
          // 5. Store pairings for the next round
          val maxRound = if (stage.matches.isEmpty) 0 else stage.matches.collect { case m: MEntrySw => m.round }.maxOption.getOrElse(0)
          val nextRound = maxRound + 1
          
          while (swissState.pairing.length < nextRound) {
            swissState.pairing += scala.collection.mutable.ArrayBuffer.empty[SwissPair]
          }
          swissState.pairing(nextRound - 1) = scala.collection.mutable.ArrayBuffer.from(nextPairings)
          
          // 6. Set stage status to AUS (since we have generated the draw, but not started the matches yet!)
          stage.status = StageStatus.AUS
          
          Right(stage)
        } catch {
          case e: Exception => Left(shared.basic.AppError("error.generate_next_round_pairing_failed", e.getMessage))
        }
      case _ =>
        Left(shared.basic.AppError("error.not_swiss_stage", "Stage data is not SwissStage"))
    }
  }

object SwissMatcher {

  // Berechnung des Kantengewichts nach Vorgabe
  def calculateWeight(p1: SwissPlayer, p2: SwissPlayer, history: Set[(Int, Int)]): Double = {
    val sameClub = if (p1.club == p2.club) 1 else 0
    val alreadyPlayed = if (history.contains((p1.id, p2.id)) || history.contains((p2.id, p1.id))) 1 else 0
    val ratingDifference = Math.abs(p1.rating - p2.rating)
    val sameScoreGroup = if (p1.scoreGroup == p2.scoreGroup) 1 else 0

    val score = 
      (-10000 * sameClub) +
      (-100000 * alreadyPlayed) +
      (-50 * ratingDifference) +
      (+10 * sameScoreGroup)
    
    score.toDouble
  }

  def calculateByeWeight(p: SwissPlayer, history: Set[(Int, Int)]): Double = {
    val alreadyHadBye = history.contains((p.id, -1)) || history.contains((-1, p.id))
    if (alreadyHadBye) -1000000.0 else 0.0
  }

  // Ein einfacher Weighted Perfect Matching Algorithmus
  def computePairings(players: Seq[SwissPlayer], history: Set[(Int, Int)]): Seq[SwissPair] = {
    val hasBye = players.length % 2 != 0
    val activePlayers = players.toList
    
    var bestPairs = Seq.empty[SwissPair]
    var bestScore = Double.NegativeInfinity
    
    val rnd = new scala.util.Random(42) // deterministic seed for reproducibility
    
    // Wir probieren 10000 Versuche aus und nehmen die Paarung mit der höchsten Gesamtbewertung
    for (_ <- 1 to 10000) {
      var remaining = rnd.shuffle(activePlayers)
      val currentPairs = scala.collection.mutable.ListBuffer[SwissPair]()
      var currentScore = 0.0
      
      if (hasBye) {
        // Unter den verbleibenden Spielern wählen wir einen für das Freilos
        // Wir maximieren die Bye-Bewertung (bevorzugen Spieler, die noch kein Freilos hatten)
        val byeScored = remaining.map(p => (p, calculateByeWeight(p, history)))
        // Wir nehmen den besten (am wenigsten bestraften), bei Gleichstand zufällig durch shuffle
        val bestBye = byeScored.maxBy(_._2)
        val byePlayer = bestBye._1
        
        currentPairs += SwissPair((byePlayer.id, -1), (0, 0), (0, 0))
        currentScore += bestBye._2
        
        remaining = remaining.filter(_.id != byePlayer.id)
      }
      
      while (remaining.nonEmpty) {
        val p1 = remaining.head
        remaining = remaining.tail
        
        if (remaining.nonEmpty) {
          val scoredOpponents = remaining.map { p2 =>
            (p2, calculateWeight(p1, p2, history))
          }
          val bestOpp = scoredOpponents.maxBy(_._2)
          val p2 = bestOpp._1
          val weight = bestOpp._2
          
          currentPairs += SwissPair((p1.id, p2.id), (0, 0), (0, 0))
          currentScore += weight
          
          remaining = remaining.filter(_.id != p2.id)
        }
      }
      
      if (currentScore > bestScore) {
        bestScore = currentScore
        bestPairs = currentPairs.toSeq
      }
    }
    
    bestPairs
  }
}
