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
          val setsVal = StageHelper.byeSets(noWinSets, sno1.isBye)
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

/**
 * Auslosung der nächsten Runde im Schweizer System als Maximum-Weight-Matching ([[MaxWeightMatching]]).
 *
 * Jeder Teilnehmer ist ein Knoten, bei ungerader Anzahl kommt ein Knoten für das Freilos dazu. Die Kantengewichte
 * sind Strafpunkte (negativ) in klar getrennten Größenordnungen, sodass gilt (wichtigstes zuerst):
 *   1. keine Wiederholung einer Paarung bzw. kein zweites Freilos (nur wenn es gar nicht anders geht)
 *   2. Freilos für einen Spieler der untersten Punktgruppe
 *   3. Paarung innerhalb der Punktgruppe (Abweichung quadratisch bestraft)
 *   4. Vereinsschutz (keine Spieler desselben Vereins)
 *   5. möglichst ähnliche Wertung (TTR); beim Freilos der Schwächste
 * Das Matching ist optimal (nicht heuristisch) und deterministisch.
 */
object SwissMatcher {

  /** Strafe für eine wiederholte Paarung bzw. ein zweites Freilos. */
  val PenaltyRepeat: Long      = 100000000000000L   // 1e14
  /** Strafe je Punkt, den der Freilos-Empfänger über der untersten Punktgruppe liegt. */
  val PenaltyByeScore: Long    = 1000000000000L     // 1e12
  /** Strafe je Quadrat der Punktdifferenz einer Paarung. */
  val PenaltyScoreDiff: Long   = 10000000L          // 1e7
  /** Strafe für eine Paarung von Spielern desselben Vereins. */
  val PenaltySameClub: Long    = 100000L            // 1e5
  /** Strafe je 10 Punkte Wertungsunterschied (höchstens 300 je Paarung). */
  val PenaltyRatingStep: Long  = 1L

  /** Haben die Spieler mit den Kennungen a und b schon gegeneinander gespielt (b = -1: Freilos)? */
  private def played(a: Int, b: Int, history: Set[(Int, Int)]): Boolean =
    history.contains((a, b)) || history.contains((b, a))

  /**
   * Gewicht einer Paarung (größer = besser, Strafpunkte negativ).
   *
   * @param p1      Spieler 1
   * @param p2      Spieler 2
   * @param history bereits gespielte Paarungen (Kennungen, -1 = Freilos)
   */
  def calculateWeight(p1: SwissPlayer, p2: SwissPlayer, history: Set[(Int, Int)]): Long = {
    val diff = (p1.scoreGroup - p2.scoreGroup).toLong
    val sameClub = p1.club != 0 && p1.club == p2.club
    -((if (played(p1.id, p2.id, history)) PenaltyRepeat else 0L) +
      diff * diff * PenaltyScoreDiff +
      (if (sameClub) PenaltySameClub else 0L) +
      math.min(300L, math.abs(p1.rating - p2.rating) / 10L) * PenaltyRatingStep)
  }

  /**
   * Gewicht des Freiloses für einen Spieler (größer = besser): bevorzugt die unterste Punktgruppe, darin den
   * Spieler mit der niedrigsten Wertung; ein zweites Freilos nur, wenn es nicht anders geht.
   *
   * @param p        Spieler
   * @param minScore Punktzahl der untersten Punktgruppe
   * @param history  bereits gespielte Paarungen (Kennungen, -1 = Freilos)
   */
  def calculateByeWeight(p: SwissPlayer, minScore: Int, history: Set[(Int, Int)]): Long =
    -((if (played(p.id, -1, history)) PenaltyRepeat else 0L) +
      (p.scoreGroup - minScore).toLong * PenaltyByeScore +
      math.min(300L, math.max(0, p.rating) / 10L) * PenaltyRatingStep)

  /**
   * Berechnet die Paarungen der nächsten Runde.
   *
   * @param players Teilnehmer mit aktueller Punktgruppe (scoreGroup = Siege)
   * @param history bereits gespielte Paarungen als Paare von Kennungen, (id, -1) = Freilos
   * @return Paarungen ((id1, id2), …); bei ungerader Anzahl genau ein Freilos (id, -1)
   */
  def computePairings(players: Seq[SwissPlayer], history: Set[(Int, Int)]): Seq[SwissPair] = {
    val ps = players.toIndexedSeq
    val n = ps.length
    if (n == 0) return Seq.empty
    if (n == 1) return Seq(SwissPair((ps(0).id, -1), (0, 0), (0, 0)))

    val byeVertex = if (n % 2 != 0) n else -1
    val minScore = ps.map(_.scoreGroup).min
    val edges = scala.collection.mutable.ArrayBuffer.empty[MaxWeightMatching.Edge]
    for (i <- 0 until n; j <- i + 1 until n)
      edges += MaxWeightMatching.Edge(i, j, calculateWeight(ps(i), ps(j), history))
    if (byeVertex >= 0)
      for (i <- 0 until n) edges += MaxWeightMatching.Edge(i, byeVertex, calculateByeWeight(ps(i), minScore, history))

    val mate = MaxWeightMatching.compute(edges.toSeq, maxCardinality = true)
    // Paarungen in der Reihenfolge der Teilnehmerliste, Freilos zuletzt
    val pairs = (0 until n).filter(i => mate(i) > i && mate(i) != byeVertex)
      .map(i => SwissPair((ps(i).id, ps(mate(i)).id), (0, 0), (0, 0)))
    val bye = (0 until n).find(i => byeVertex >= 0 && mate(i) == byeVertex)
      .map(i => SwissPair((ps(i).id, -1), (0, 0), (0, 0)))
    pairs ++ bye.toSeq
  }
}
