package shared.model

import shared.Routines._
import upickle.default.{ReadWriter, macroRW}

sealed abstract class Match(val coId: Long, val coTyp: CompTyp, val coRndId: Int, val coRndTyp: CompRoundTyp, 
                      var gameNo: Int=0, round: Int=0, var playfield:String="", var status:MatchStatus=MatchStatus.READY,
                      var info: String="", var sets: (Int,Int) =(0,0), var result:String="",
                      val winSets: Int=0, var stNoA: String="", var stNoB: String=""):

  def setParticipantA(sNoA: String): Unit
  def setParticipantB(sNoB: String): Unit

  def setParticipant(pos: Int, sNo: String): Match =
    pos match
      case 0 => setParticipantA(sNo)
      case 1 => setParticipantB(sNo)
    this

  def setSets(value: (Int,Int)): Unit
  def setResult(value: String): Unit
  def setPlayfield(value: String): Unit
  def setInfo(value: String): Unit
  def setStatus(value: MatchStatus): Unit
  def setGameNo(value: Int): Unit

  def setStatus(depFinished: Boolean = true): Match =
    Match.setRunning(this, false)

    val blocked = Match.isPlayerRunning(stNoA, stNoB, coTyp) || !depFinished
    if      (validSets() & (SNO(stNoA).isBye | SNO(stNoB).isBye)) setStatus(MatchStatus.FIX)
    else if (validSets())                                         setStatus(MatchStatus.FIN)
    else if (SNO(stNoA).isNN | SNO(stNoB).isNN)                 setStatus(MatchStatus.MISS)
    else if (blocked)                                           setStatus(MatchStatus.BLOCK)
    else if (sets == (0,0) & playfield != "") {
      Match.setRunning(this, true)
      setStatus(MatchStatus.RUN)
    }
    else if (sets == (0,0) & playfield == "")                  setStatus(MatchStatus.READY)
    else if (sets._1 == sets._2 & sets._1 != 0)                setStatus(MatchStatus.DRAW)
    else                                                       setStatus(MatchStatus.UNKN)
    this

  def getPlayfield: String =
    try
      val pfCode = playfield.split("·")
      pfCode(pfCode.size-1)
    catch
      case _: Throwable => ""

  def finished: Boolean = (status == MatchStatus.FIN) || (status == MatchStatus.FIX) || (status == MatchStatus.DRAW)
  def countable: Boolean = status == MatchStatus.FIN

  def validSets(): Boolean = (sets._1 == winSets & sets._2 < winSets) | (sets._1 < winSets & sets._2 == winSets)

  def reset(resetPantA: Boolean = false, resetPantB: Boolean = false): Match =
    if resetPantA then setParticipantA("")
    if resetPantB then setParticipantB("")
    setPlayfield("")
    setInfo("")
    setSets((0,0))
    setResult("")
    this

  def getWinner(): String =
    if sets._1 > sets._2 then stNoA
    else if sets._2 > sets._1 then stNoB
    else ""

  def getLooser(): String =
    if sets._1 > sets._2 then stNoB
    else if sets._2 > sets._1 then stNoA
    else ""

  def getBallFromStr(b: String): (Int, Int) =
    if b == "" then (-1,-1)
    else if b == "+0" || b == "0" then (11,0)
    else if b == "-0" then (0,11)
    else
      b.toIntOption.getOrElse(0) match
        case a if 10 to 500 contains a => (a+2, a)
        case b if 1 to 9 contains b    => (11, b)
        case c if -9 to -1 contains c  => (-c, 11)
        case d if -500 to -10 contains d => (-d, 2 - d)
        case _ => (-1,-1)

  def getBalls: Array[(Int, Int)] =
    val ballsArr = scala.collection.mutable.ArrayBuffer[(Int, Int)]()
    if result.trim == "" then Array.empty[(Int, Int)]
    else
      result.split("·").foreach(res => ballsArr.append(getBallFromStr(res)))
      assert(ballsArr.size == sets._1 + sets._2)
      ballsArr.toArray


object Match:
  import scala.collection.mutable.HashSet
  import scala.collection.mutable.Map

  // list of player currently playing in (coId, coIdPh, gameNo)
  val playing: Map[Long, HashSet[(Long,Int,Int)]] = Map()

  /** addRunning(plId: Long, gaId: (Long,Int,Int))
   *  game identifier = tripple (competition identifier, competition phase identifier, game number)
   */
  def addPlayerRunning(plId: Long, gaId: (Long,Int,Int)) = 
    // println(s"addPlayerRunning: ${plId} game: ${gaId._3}")
    if (plId != 0) { if (playing.contains(plId)) { playing(plId) += gaId } else { playing(plId) = HashSet(gaId) } }  
 
  def removePlayerRunning(plId: Long, gaId: (Long,Int,Int)) = 
    if (plId != 0 && playing.contains(plId)) { playing(plId) -= (gaId) }

  def getPlayerRunning(plId1: Long, plId2: Long=0 ): Boolean =   
    if (playing.contains(plId1) && playing.contains(plId2)) {
      // println(s"getPlayerRunning plId1: ${plId1} plId2: ${plId2} Map1: ${playing(plId1).toString} Map2: ${playing(plId2).toString}")
      (playing(plId1).size > 0) | (playing(plId2).size > 0) 
    } else if (playing.contains(plId1)) {
      // println(s"getPlayerRunning plId1: ${plId1} Map1: ${playing(plId1).toString}")
      playing(plId1).size > 0
    } else {
      // println(s"getPlayerRunning false (no entry)")
      false
    }

  def isPlayerRunning(snoA: String, snoB: String, coTyp: CompTyp): Boolean = 
    import shared.model.Competition._
    coTyp match 
      case CompTyp.SINGLE => getPlayerRunning(SNO.plId(snoA), SNO.plId(snoB))
      case CompTyp.DOUBLE => 
        val idAs = getMDLongArr(snoA)
        val idBs = getMDLongArr(snoB)
        if (idAs.length == 2 && idBs.length == 2) {
          getPlayerRunning(idAs(0), idAs(1)) | getPlayerRunning(idBs(0), idBs(1))
        } else false

  def setRunning(m: Match, run: Boolean) =
    val gaId = (m.coId, m.coRndId, m.gameNo)
    m.coTyp match {
      case CompTyp.SINGLE => {
        if (run) addPlayerRunning(SNO.plId(m.stNoA), gaId) else removePlayerRunning(SNO.plId(m.stNoA), gaId)
        if (run) addPlayerRunning(SNO.plId(m.stNoB), gaId) else removePlayerRunning(SNO.plId(m.stNoB), gaId)
      }
      case CompTyp.DOUBLE => {
        val idAs = getMDLongArr(m.stNoA)
        val idBs = getMDLongArr(m.stNoB)
        if (idAs.length == 2) {
          if (run) addPlayerRunning(idAs(0), gaId) else removePlayerRunning(idAs(0), gaId)
          if (run) addPlayerRunning(idAs(1), gaId) else removePlayerRunning(idAs(1), gaId)
        } 
        if (idBs.length == 2) {
          if (run) addPlayerRunning(idBs(0), gaId) else removePlayerRunning(idBs(0), gaId)
          if (run) addPlayerRunning(idBs(1), gaId) else removePlayerRunning(idBs(1), gaId)
        }
      }
    }       



enum MatchStatus(val id: Int, val label: String):
  case RESET extends MatchStatus(-3,  "RESET")    // match not yet configured
  case MISS  extends MatchStatus(-2,  "MISS")     // not finished (player missing)
  case BLOCK extends MatchStatus(-1,  "BLOCK")    // not finished (blocked)
  case READY extends MatchStatus(0,  "READY")     // not finished (runnable/ready)
  case RUN   extends MatchStatus(1,  "RUN")       // running
  case FIN   extends MatchStatus(2,  "FIN")       // finished with winner
  case FIX   extends MatchStatus(3,  "FIX")       // finished with fixed winner (bye ...)
  case DRAW  extends MatchStatus(4,  "DRAW")      // finished with no winner
  case UNKN  extends MatchStatus(99,  "UNKN")     // finished with no winner or error


