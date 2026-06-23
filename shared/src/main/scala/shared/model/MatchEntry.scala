package shared.model

import shared.basic.Pickle.{ReadWriter => RW, macroRW, *}
import upickle.implicits.key
import shared.basic.*


sealed trait MEntry {
  def coId: CompId 
  def coTyp: CompTyp 
  def stageId: StageId
  def stageFormat: StageFormat
  def stNoA: SNO
  def stNoB: SNO
  def round: Int
  def gameNo: Int
  def status: Int
  def playfield: String
  def sets: (Int,Int)
  def info: String
  def result: String
  def winSets: Int
  def toString: String
  def toTx: MEntryTx
  def startTime: String
  def startTime_=(value: String): Unit
  def endTime: String
  def endTime_=(value: String): Unit

  def setPantA(sNoA: SNO):Unit
  def setPantB(sNoB: SNO):Unit

  def setPant(pos: Int, sNo: SNO): MEntry = {
    pos match {
      case 0 => setPantA(sNo)
      case 1 => setPantB(sNo)
    }
    this
  }

  def setSets(value:(Int,Int)):Unit
  def setResult(value:String):Unit
  def setPlayfield(value:String):Unit
  def setInfo(value:String):Unit
  def setStatus(value:Int):Unit
  def setGameNo(value: Int):Unit

  def setStatus(depFinished: Boolean=true): MEntry = {
    MEntry.setRunning(this, false) 

    val blocked = MEntry.isPlayerRunning(stNoA, stNoB, coTyp) || !depFinished
    if      (validSets() & (stNoA.isBye | stNoB.isBye))         { setStatus(MEntry.MS_FIX)   }
    else if (validSets())                                       { setStatus(MEntry.MS_FIN)   } 
    else if (stNoA.isNN | stNoB.isNN)                           { setStatus(MEntry.MS_MISS)  } 
    else if (blocked)                                           { setStatus(MEntry.MS_BLOCK) }
    else if (sets==(0,0) & playfield!="")                       { 
                                                                  MEntry.setRunning(this, true)
                                                                  setStatus(MEntry.MS_RUN)   
                                                                }
    else if (sets==(0,0) & playfield=="")                       { setStatus(MEntry.MS_READY) }        
    else if (sets._1 == sets._2 & sets._1 != 0)                 { setStatus(MEntry.MS_DRAW)  }      
    else                                                        { setStatus(MEntry.MS_UNKN)  }
    this
  }

  def getPlayfield = {
    try { 
      val pfCode = playfield.split("·")
      pfCode(pfCode.size-1) 
    } catch  { case _: Throwable => "" }
  }

  def finished = ((status == MEntry.MS_FIN) || (status == MEntry.MS_FIX) || (status == MEntry.MS_DRAW))
  
  def countable = (status == MEntry.MS_FIN) 

  def validSets(): Boolean = ((sets._1 == winSets & sets._2 < winSets) | (sets._1 < winSets & sets._2 == winSets))

  def reset(resetPantA: Boolean=false, resetPantB: Boolean=false):MEntry = { 
    if (resetPantA) setPantA(SNO.nn)
    if (resetPantB) setPantB(SNO.nn)
    setPlayfield("") 
    setInfo("") 
    setSets((0,0)) 
    setResult("")
    startTime = ""
    endTime = ""
    this
  } 

  def getWinner(): SNO = {
    if      (sets._1 > sets._2) stNoA
    else if (sets._2 > sets._1) stNoB
    else SNO.nn
  }
  
  def getLooser(): SNO = {
    if      (sets._1 > sets._2) stNoB
    else if (sets._2 > sets._1) stNoA
    else SNO.nn
  }

  def getBallFromStr(b: String):(Int,Int) = {
    if      (b == "")              (-1,-1)  
    else if (b == "+0" | b == "0") (11,0) 
    else if (b == "-0")            (0,11)  
    else b.toIntOption.getOrElse(0) match {  
      case a if   10 to 500 contains a => (a+2, a)
      case b if    1 to   9 contains b => (11, b)
      case c if   -9 to  -1 contains c => (-c, 11)
      case d if -500 to -10 contains d => (-d, 2 - d) 
      case _                           => (-1,-1)
    }
  }

  def getBalls: Array[(Int,Int)] = {
    val ballsArr = new scala.collection.mutable.ArrayBuffer[(Int,Int)]()
    if (result.trim == "") Array[(Int, Int)]() else {
      result.split("·").foreach( res => ballsArr.append(getBallFromStr(res)) )
      assert(ballsArr.size == sets._1 + sets._2)
      ballsArr.to(Array)
    }  
  }

}


case class MEntryBase(coId: CompId, 
                      coTyp: CompTyp, 
                      stageId: StageId, 
                      stageFormat: StageFormat, 
                      var gameNo: Int=0,
                      round: Int=0, 
                      var playfield:String="", 
                      var status:Int=0,
                      var info: String="", 
                      var sets: (Int,Int) =(0,0),
                      var result:String="",
                      val winSets: Int=0, 
                      var stNoA: SNO=SNO.nn, 
                      var stNoB: SNO=SNO.nn,
                      var startTime: String = "",
                      var endTime: String = ""
                       
) extends MEntry { 
    
  def toTx = MEntryTx(coId, coTyp, stageId, stageFormat, "")
  override def toString(): String = s"""  Base-Match"""

  def setPantA(sNoA: SNO) = stNoA = sNoA
  def setPantB(sNoB: SNO) = stNoB = sNoB

  def setSets(value:(Int,Int)) = { sets = value }
  def setResult(value:String)  = { result = value } 
  def setPlayfield(value: String) = { playfield = value.trim() }
  def setInfo(value: String)  = { info = value } 
  def setStatus(value:Int)    = { status = value }
  def setGameNo(value: Int)   = { gameNo = value }
}


@key("MEntryKo")
case class MEntryKo(
  val coId:   CompId,                     // competition identifier
  val coTyp:  CompTyp,                    // competition typ, e.g. CT_SINGLE, CT_DOUBLE
  val stageId:  StageId,                // competition phase identifier
  val stageFormat: StageFormat,               // competition phase type
  var gameNo: Int,                        //(0) game number within phase
  
  var stNoA:  SNO,                        //(1) participant A start number
  var stNoB:  SNO,                        //(2) participant B start number
  
  var round:  Int,                        //(3) (round (7 ... 0) for 128-field/7, 64-field/6 ... Final/1, 3rdPlace/0 
  var maNo:   Int,                        //(4) Match number within round
  var winPos: String,                     //(5) Next position of winner within match array (gameNo, matchNo within Stage, intStage, pos(0/1))
  var looPos: String,                     //(6) Next position of looser within match array
 
  var playfield:   String,                //(7) playfield eg. 1,2 or "table 5"
  var info:        String,                //(8) additional information of game

  var startTime:   String,                //(9) Format: yyyymmddhhmmss
  var endTime:     String,                //(10)  
  var status:      Int,                   //(11) see Match Status Values: MS_xxx

  var sets:       (Int,Int),              //(12)(13) sets e.g. (3,1)
  val winSets:    Int,                    //(14) number of sets to win the match or 0 for draw             
  var result:     String                  //(15) result details, depending on kind of sport

) extends MEntry {
  def toTx = MEntryTx(coId, coTyp, stageId, stageFormat, s"${gameNo}^${stNoA}^${stNoB}^${round}^${maNo}^${winPos}^${looPos}^${playfield}^${info}^${startTime}^${endTime}^${status}^${sets._1}^${sets._2}^${winSets}^${result}^_")
  override def toString(): String = s"""
    |  Ko-Match: SnoA: ${stNoA} - SnoB: ${stNoB} Winner->${winPos} Looser->${looPos}
    |    gameNo: ${gameNo} round: ${round} maNo: ${maNo} info: ${info} winSets: ${winSets}
    |    coId: ${coId} coTyp: ${coTyp} stageId: ${stageId} stageFormat: ${stageFormat}
    |    playfield: ${playfield} status: ${MEntry.statusInfo(status)} sets: ${sets._1}:${sets._2} result: [${result}]
    """.stripMargin('|')

  def setPantA(sNoA: SNO) = stNoA = sNoA
  def setPantB(sNoB: SNO) = stNoB = sNoB
  def setSets(value:(Int,Int)) = { sets = value }
  def setResult(value:String)  = { result = value } 
  def setPlayfield(value: String) = { playfield = value.trim() }
  def setInfo(value:String)  = { info = value }  
  def setStatus(value:Int)   = { status = value } 
  def setGameNo(value: Int)   = { gameNo = value }  

  def getWinPos():(Int,Int) = {
    val wPos = getMDIntArr(winPos)
    if (wPos.size == 4) (wPos(0), wPos(3)) else (0,0)
  }

  def getLooPos():(Int,Int) = {
    val lPos = getMDIntArr(looPos)
    if (lPos.size == 4) (lPos(0), lPos(3)) else (0,0)
  }

  def setWinLoo() = {
    val rndSize    = if (round >=2) scala.math.pow(2,round-1).toInt else 1
    val nextGameNo = gameNo + rndSize - maNo + (maNo + 1) / 2
    val nextPos    = (maNo + 1) % 2
    round match {
      case x if x > 2  => { winPos = s"${nextGameNo}·${(maNo + 1)/2}·${round - 1}·${nextPos}"; looPos = "" }
      case 2           => { 
        winPos = s"${nextGameNo}·${(maNo + 1)/2}·${round - 1}·${nextPos}"
        looPos = s"${nextGameNo+1}·${(maNo + 1)/2}·${round - 2}·${nextPos}"        
      }
      case _           => { winPos = ""; looPos = "" } 
    }
  } 

  def getWinLoo(round: Int, gameNo: Int, maNo: Int): (String,String) = {
    val rndSize    = if (round >=2) scala.math.pow(2,round-1).toInt else 1
    val nextGameNo = gameNo + rndSize - maNo + (maNo + 1) / 2
    val nextPos    = (maNo + 1) % 2
    round match {
      case x if x > 2  => (s"${nextGameNo}·${(maNo + 1)/2}·${round - 1}·${nextPos}", "")
      case 2           => (s"${nextGameNo}·${(maNo + 1)/2}·${round - 1}·${nextPos}", s"${nextGameNo+1}·${(maNo + 1)/2}·${round - 2}·${nextPos}")        
      case _           => ("","")
    }
  } 

}


object MEntryKo {
  def getWinLooSingleKo(round: Int, gameNo: Int, maNo: Int): (String,String) = {
    val rndSize    = if (round >=2) scala.math.pow(2,round-1).toInt else 1
    val nextGameNo = gameNo + rndSize - maNo + (maNo + 1) / 2
    val nextPos    = (maNo + 1) % 2
    round match {
      case x if x > 2  => (s"${nextGameNo}·${(maNo + 1)/2}·${round - 1}·${nextPos}", "")
      case 2           => (s"${nextGameNo}·${(maNo + 1)/2}·${round - 1}·${nextPos}", s"${nextGameNo+1}·${(maNo + 1)/2}·${round - 2}·${nextPos}")        
      case _           => ("","")
    }
  } 

  def init(coId: CompId, coTyp: CompTyp, stageId: StageId, stageFormat: StageFormat, stNoA: SNO, stNoB: SNO, gameNo: Int, round: Int, maNo: Int,
            winPos: String, looPos: String, status: Int, sets: (Int,Int), winSets: Int,
            playfield: String="", info: String="", startTime: String="", endTime: String="", result: String = "") = {
              val wl = getWinLooSingleKo(round, gameNo, maNo)
              MEntryKo(coId, coTyp, stageId, stageFormat, gameNo, stNoA, stNoB, round, maNo, wl._1, wl._2, playfield, info, startTime, endTime, status, sets, winSets, result)
            }
}


@key("MEntryGr")
case class MEntryGr(
  val coId:      CompId,                 // competition identifier
  val coTyp:     CompTyp,                // competition typ, e.g. CT_SINGLE, CT_DOUBLE
  val stageId:     StageId,            // competition phase identifier
  val stageFormat:    StageFormat,           // competition phase system, eg. CPT_GR, CPT_KO
  var gameNo:    Int,                    //(0) game number within phase
  
  var stNoA:     SNO,                    //(1) participant A start number
  var stNoB:     SNO,                    //(2) participant B start number
  
  var round:     Int,                    //(3) Group stage 1 ...  
  var grId:      Int,                    //(4) Group Identification
  var wgw:       (Int,Int),              //(5,6) who against who
  var depend:    String,                 //(7) List of games that should be finished before, 
                                         //    separated by middle dot
  var trigger:   String,                 //(8) List of games/matches that should be triggered (status updated), 
                                         //    separated by middle dot
  var playfield: String,                 //(9) playfield eg. 1,2 or "table 5"
  var info:      String,                 //(10) additional information of game

  var startTime: String,                 //(11) Format: yyyymmddhhmmss
  var endTime:   String,                 //(12)  

  var status:    Int,                    //(13) see Match Status Values: MS_xxx
  var sets:      (Int,Int),              //(14)(15) sets e.g. (3,1)
  val winSets:   Int,                    //(16) number of sets to win the match or 0 for draw                                         
  var result:    String                  //(17) result details, depending on kind of sport
) extends MEntry {

  def toTx = MEntryTx(coId, coTyp, stageId, stageFormat, s"${gameNo}^${stNoA}^${stNoB}^${round}^${grId}^${wgw._1}^${wgw._2}^${depend}^${trigger}^${playfield}^${info}^${startTime}^${endTime}^${status}^${sets._1}^${sets._2}^${winSets}^${result}^_")
  override def toString(): String = s"""
      |  Group-Match: ${wgw._1}-${wgw._2} SnoA: ${stNoA} - SnoB: ${stNoB} 
      |    gameNo: ${gameNo} round: ${round} grId: ${grId} info: ${info} winSets: ${winSets}
      |    coId: ${coId.value} coTyp: ${coTyp} stageId: ${stageId.value} stageFormat: ${stageFormat}
      |    depend: ${depend} trigger: ${trigger} playfield: ${playfield} 
      |    status: ${MEntry.statusInfo(status)} sets: ${sets._1}:${sets._2} result: [${result}]
  """.stripMargin('|')

  def setPantA(sNoA: SNO) = stNoA = sNoA
  def setPantB(sNoB: SNO) = stNoB = sNoB
  def setSets(value:(Int,Int))    = { sets = value }
  def setResult(value:String)     = { result = value } 
  def setPlayfield(value: String) = { playfield = value }
  def setInfo(value:String)       = { info = value } 
  def setStatus(value:Int)        = { status = value } 
  def setGameNo(value: Int)       = { gameNo = value }

  def getTrigger()                = getMDIntArr(trigger)   
  def getDepend()                 = getMDIntArr(depend)
  def hasDepend                   = (depend != "")
}


object MEntryGr {
   def init(coId: CompId, coTyp: CompTyp, stageId: StageId, stageFormat: StageFormat, gameNo: Int, stNoA: SNO, stNoB: SNO, round: Int, grId: Int, wgw: (Int,Int), winSets: Int) = {
     MEntryGr(coId, coTyp, stageId, stageFormat, gameNo, stNoA, stNoB, round, grId, wgw, "_default_", "", "", "", "", "", 0, (0,0), winSets, "")
   }
}

case class MEntryTx(coId: CompId, coTyp: CompTyp, stageId: StageId, stageFormat: StageFormat, content: String)


object MEntry {
  given rwBase: RW[MEntryBase] = macroRW
  given rwKo: RW[MEntryKo] = macroRW
  given rwGr: RW[MEntryGr] = macroRW
  given rwTx: RW[MEntryTx] = macroRW

  given rw: RW[MEntry] = Pickle.readwriter[ujson.Value].bimap[MEntry](
    (m: MEntry) => Pickle.writeJs(m.toTx),
    (json: ujson.Value) => {
      if (json.obj.contains("content")) {
        val tx = Pickle.read[MEntryTx](json)
        tx.stageFormat match {
          case StageFormat.GR | StageFormat.RR | StageFormat.SW =>
            val m = tx.content.split("\\^", -1)
            val gameNo = m(0).toInt
            val stNoA = SNO.fromString(m(1))
            val stNoB = SNO.fromString(m(2))
            val round = m(3).toInt
            val grId = m(4).toInt
            val wgw1 = m(5).toInt
            val wgw2 = m(6).toInt
            val depend = m(7)
            val trigger = m(8)
            val playfield = m(9)
            val info = m(10)
            val startTime = m(11)
            val endTime = m(12)
            val status = m(13).toInt
            val sets1 = m(14).toInt
            val sets2 = m(15).toInt
            val winSets = m(16).toInt
            val result = m(17)
            MEntryGr(tx.coId, tx.coTyp, tx.stageId, tx.stageFormat, gameNo, stNoA, stNoB, round, grId, (wgw1, wgw2), depend, trigger, playfield, info, startTime, endTime, status, (sets1, sets2), winSets, result)

          case StageFormat.KO =>
            val m = tx.content.split("\\^", -1)
            val gameNo = m(0).toInt
            val stNoA = SNO.fromString(m(1))
            val stNoB = SNO.fromString(m(2))
            val round = m(3).toInt
            val maNo = m(4).toInt
            val winPos = m(5)
            val looPos = m(6)
            val playfield = m(7)
            val info = m(8)
            val startTime = m(9)
            val endTime = m(10)
            val status = m(11).toInt
            val sets1 = m(12).toInt
            val sets2 = m(13).toInt
            val winSets = m(14).toInt
            val result = m(15)
            MEntryKo(tx.coId, tx.coTyp, tx.stageId, tx.stageFormat, gameNo, stNoA, stNoB, round, maNo, winPos, looPos, playfield, info, startTime, endTime, status, (sets1, sets2), winSets, result)

          case _ =>
            MEntryBase(tx.coId, tx.coTyp, tx.stageId, tx.stageFormat)
        }
      } else {
        val typeTag = json.obj.get("$type").map(_.str).getOrElse("")
        typeTag match {
          case "MEntryKo" | "shared.model.MEntryKo" => Pickle.read[MEntryKo](json)
          case "MEntryGr" | "shared.model.MEntryGr" => Pickle.read[MEntryGr](json)
          case _ => Pickle.read[MEntryBase](json)
        }
      }
    }
  )

  import scala.collection.mutable.HashSet
  import scala.collection.mutable.Map

  // Match Status Values
  val MS_RESET = -3   // match not yet configured
  val MS_MISS  = -2   // not finished (player missing)
  val MS_BLOCK = -1   // not finished (blocked)
  val MS_READY =  0   // not finished (runnable/ready)
  val MS_RUN   =  1   // running
  val MS_FIN   =  2   // finished with winner
  val MS_FIX   =  3   // finished with fixed winner (bye ...)
  val MS_DRAW  =  4   // finished with no winner
  val MS_UNKN  = 99   // finished with no winner or error

  // list of player currently playing in (coId, stageId, gameNo)
  val playing: Map[PlayerId, HashSet[(CompId, StageId, Int)]] = Map()

  def statusInfo(value: Int) = value match {
    case MS_RESET  => "RESET"
    case MS_MISS   => "MISS"
    case MS_BLOCK  => "BLOCK"
    case MS_READY  => "READY"
    case MS_RUN    => "RUN"
    case MS_FIN    => "FIN"
    case MS_FIX    => "FIX"    
    case MS_DRAW   => "DRAW"
    case MS_UNKN   => "UNKN"
    case _         => "ERROR"
  }

  /** addRunning(plId: Long, gaId: (Long,Int,Int))
   *  game identifier = triple (competition identifier, stage identifier, game number)
   */
  def addPlayerRunning(plId: PlayerId, gaId: (CompId, StageId, Int)) = {
    if (plId.value > 0) { if (playing.contains(plId)) { playing(plId) += gaId } else { playing(plId) = HashSet(gaId) } }  
  }  
  def removePlayerRunning(plId: PlayerId, gaId: (CompId, StageId, Int)) = {
    if (plId.value > 0 && playing.contains(plId)) { playing(plId) -= (gaId) }
  }  

  def getPlayerRunning(plId1: PlayerId, plId2: PlayerId = PlayerId(0)): Boolean = {  
    if (playing.contains(plId1) && playing.contains(plId2)) {
      (playing(plId1).size > 0) | (playing(plId2).size > 0) 
    } else if (playing.contains(plId1)){
      playing(plId1).size > 0
    } else {
      false
    }
  } 


  def isPlayerRunning(snoA: SNO, snoB: SNO, coTyp: CompTyp): Boolean = {
    import shared.model.Competition._
    coTyp match {
      case CompTyp.SINGLE => getPlayerRunning(SNO.singleId(snoA), SNO.singleId(snoB))
      case CompTyp.DOUBLE => {
        val (idA1,idA2) = SNO.doubleId(snoA)
        val (idB1,idB2) = SNO.doubleId(snoB)
        getPlayerRunning(idA1, idA2) | getPlayerRunning(idB1, idB2)         
      }
      case _ => false
    }
  }

  def setRunning(m: MEntry, run: Boolean) = {
    import shared.model.Competition._

    val gaId = (m.coId, m.stageId, m.gameNo)
    m.coTyp match {
      case CompTyp.SINGLE => {
        if (run) addPlayerRunning(m.stNoA.singleId, gaId) else removePlayerRunning(m.stNoA.singleId, gaId)
        if (run) addPlayerRunning(m.stNoB.singleId, gaId) else removePlayerRunning(m.stNoB.singleId, gaId)
      }
      case CompTyp.DOUBLE => {
        val (idA1,idA2) = m.stNoA.doubleId
        val (idB1,idB2) = m.stNoB.doubleId
        if (run) addPlayerRunning(idA1, gaId) else removePlayerRunning(idA1, gaId)
        if (run) addPlayerRunning(idA2, gaId) else removePlayerRunning(idA2, gaId)
        if (run) addPlayerRunning(idB1, gaId) else removePlayerRunning(idB1, gaId)
        if (run) addPlayerRunning(idB2, gaId) else removePlayerRunning(idB2, gaId)
      }
      case _ =>
    }
  }

}