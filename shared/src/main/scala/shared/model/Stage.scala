package shared.model

import shared.basic.Pickle.*
import scala.collection.mutable.{ ArrayBuffer, Map, Set, Stack }
import shared.format.*
import shared.basic.Log

opaque type StageId = Int
object StageId:
  def apply(value: Int): StageId = value
  def fromInt(value: Int): StageId = value

  extension (id: StageId)
    def value: Int = id

  // We use 'IntReader' and 'IntWriter' specifically to avoid 
  // the 'readwriter[Int]' macro search.
  given rw: ReadWriter[StageId] = 
    ReadWriter.join(IntReader, IntWriter).bimap(
      (id: StageId) => id: Int,
      (value: Int) => StageId(value)
    )

// -----------------------------
// StageConfig
// -----------------------------
enum StageConfig(val id: Int, val format: StageFormat) derives CanEqual:

  // --- Basic Phases ---
  case UNKN   extends StageConfig(-1, StageFormat.TBD)
  case CFG    extends StageConfig(0,  StageFormat.TBD)

  case VRGR   extends StageConfig(1,  StageFormat.GR)
  case ZRGR   extends StageConfig(2,  StageFormat.GR)
  case ERGR   extends StageConfig(3,  StageFormat.GR)
  case TRGR   extends StageConfig(4,  StageFormat.GR)

  case VRKO   extends StageConfig(6,  StageFormat.KO)
  case ERKO   extends StageConfig(8,  StageFormat.KO)
  case TRKO   extends StageConfig(9,  StageFormat.KO)

  // --- Group Systems ---
  case GR3to9 extends StageConfig(100, StageFormat.GR)
  case GRPS3  extends StageConfig(101, StageFormat.GR)   // only groups with 3 players 
  case GRPS34 extends StageConfig(102, StageFormat.GR)   // only groups with 3 or 4 players
  case GRPS4  extends StageConfig(103, StageFormat.GR)   // only groups with 4 players
  case GRPS45 extends StageConfig(104, StageFormat.GR)   // only groups with 4 or 5 players
  case GRPS5  extends StageConfig(105, StageFormat.GR)   // only groups with 5 players
  case GRPS56 extends StageConfig(106, StageFormat.GR)   // only groups with 5 or 6 players
  case GRPS6  extends StageConfig(107, StageFormat.GR)   // only groups with 6 players
  case GRPS7  extends StageConfig(111, StageFormat.GR)   // only groups with 7 players
  case GRPS8  extends StageConfig(112, StageFormat.GR)   // only groups with 8 players

  // --- Tournament Systems ---
  case RR     extends StageConfig(108, StageFormat.RR)    // Round Robin - everyone plays everyone
  case KO     extends StageConfig(109, StageFormat.KO)    // Knockout - single elimination   
  case SW     extends StageConfig(110, StageFormat.SW)    // Swiss - players are paired in each round based on their current score, with the goal of matching players with similar performance.

  // --------------------------------

  def msgCode: String = s"StageConfig.$productPrefix"
  def infoCode: String = s"StageConfigInfo.$productPrefix"
  def isOneOf(values: StageConfig*): Boolean = values.contains(this)

object StageConfig:
  given rw: ReadWriter[StageConfig] =
    readwriter[String].bimap[StageConfig](
      _.toString,
      s => try StageConfig.valueOf(s) catch { case _: Exception => StageConfig.UNKN }
    )

  def fromId(id: Int): StageConfig =
    values.find(_.id == id).getOrElse(UNKN)
      

enum DrawOption(val id: Int, val format: StageFormat) derives CanEqual:
  case Unknown      extends DrawOption(0, StageFormat.TBD)
  case GrpStart     extends DrawOption(1, StageFormat.GR)		
  case GrpAfterGrp	extends DrawOption(2, StageFormat.GR)
  case KoStart   	  extends DrawOption(3, StageFormat.KO)
  case KoAfterGrp	  extends DrawOption(4, StageFormat.KO)
  case RrStart  	  extends DrawOption(5, StageFormat.RR)
  case RrAfterGrp  	extends DrawOption(6, StageFormat.RR)
  case SwStart  	  extends DrawOption(7, StageFormat.SW)
  case SwAfterSw  	extends DrawOption(8, StageFormat.SW)
  case SwUpperLower extends DrawOption(9, StageFormat.SW)
  case SwAccel2     extends DrawOption(10, StageFormat.SW)
  case SwAccel3     extends DrawOption(11, StageFormat.SW)
  case SwTopBottom  extends DrawOption(12, StageFormat.SW)
  case SwRandom     extends DrawOption(13, StageFormat.SW)
  case SwManual     extends DrawOption(14, StageFormat.SW)

object DrawOption:
  given rw: ReadWriter[DrawOption] =
    readwriter[String].bimap[DrawOption](
      _.toString,
      s => try DrawOption.valueOf(s) catch { case _: Exception => DrawOption.Unknown }
    )

// -----------------------------
// QualifyTyp
// -----------------------------
enum QualifyTyp(val id: Int) derives CanEqual:
  case ALL extends QualifyTyp(0)
  case WIN extends QualifyTyp(1)
  case LOO extends QualifyTyp(2)
  case MAN extends QualifyTyp(3)

  def msgCode: String = s"QualifyTyp.$productPrefix"

object QualifyTyp:
  given rw: ReadWriter[QualifyTyp] =
    readwriter[String].bimap[QualifyTyp](
      _.toString,
      s => try QualifyTyp.valueOf(s) catch { case _: Exception => QualifyTyp.ALL }
    )

  def fromId(id: Int): QualifyTyp =
    values.find(_.id == id).getOrElse(ALL)


// -----------------------------
// StageFormat
// -----------------------------
enum StageFormat(val id: Int) derives CanEqual:

  case TBD  extends StageFormat(-1)
  case GR   extends StageFormat(1)
  case KO   extends StageFormat(2)
  case SW   extends StageFormat(3)
  case RR   extends StageFormat(4)

  def msgCode: String = s"StageFormat.$productPrefix"

object StageFormat:
  given rw: ReadWriter[StageFormat] =
    readwriter[String].bimap[StageFormat](
      _.toString,
      s => try StageFormat.valueOf(s) catch { case _: Exception => StageFormat.TBD }
    )

  def fromId(id: Int): StageFormat = values.find(_.id == id).getOrElse(TBD)
  def fromName(name: String): StageFormat = values.find(_.productPrefix == name).getOrElse(TBD)
  def apply(id: Int): StageFormat = fromId(id)  


// -----------------------------
// StageStatus
// -----------------------------
enum StageStatus(val id: Int) derives CanEqual:
  case CFG  extends StageStatus(0)
  case AUS  extends StageStatus(1)
  case EIN  extends StageStatus(2)
  case FIN  extends StageStatus(3)
  case UNKN extends StageStatus(-1)

  def msgCode: String = s"StageStatus.$productPrefix"

object StageStatus:
  given rw: ReadWriter[StageStatus] =
    readwriter[String].bimap[StageStatus](
      _.toString,
      s => try StageStatus.valueOf(s) catch { case _: Exception => StageStatus.UNKN }
    )

  def fromId(id: Int): StageStatus = values.find(_.id == id).getOrElse(UNKN)
  def fromName(name: String): StageStatus = values.find(_.productPrefix == name).getOrElse(UNKN)


enum StageData:
  case GroupsStage(groups: ArrayBuffer[Group])
  case KnockoutStage(state: KoStage)
  case SwissStage(swGroup: SwGroup)
  case RoundRobinStage(rrGroup: RrGroup)

object StageData:
  given rwGroupsStage: ReadWriter[StageData.GroupsStage] = macroRW
  given rwKnockoutStage: ReadWriter[StageData.KnockoutStage] = macroRW
  given rwSwissStage: ReadWriter[StageData.SwissStage] = macroRW
  given rwRoundRobinStage: ReadWriter[StageData.RoundRobinStage] = macroRW

  given rw: ReadWriter[StageData] = macroRW


/**
 * Represents a single stage (round/phase) of a competition.
 * Contains configuration, player size, current stage data and matches.
 *
 * @param id Unique identifier of the stage.
 * @param coId Parent competition identifier.
 * @param name Name of the stage.
 * @param stageConfig Format configuration.
 * @param status Current status of the stage.
 * @param demo Flag indicating if this is a demo stage.
 * @param size Number of participants or size configuration.
 * @param noPlayers Number of players registered.
 * @param data Specific stage data format.
 * @param noWinSets Number of winning sets required.
 * @param prefId Option reference to predecessor stage.
 * @param nextIds List of successor stage IDs.
 * @param quali Qualification type.
 * @param deleted Soft delete flag.
 * @param version Optimistic locking version.
 * @param matches ArrayBuffer of matches assigned to this stage.
 */
case class Stage(
  id:                   StageId,
  coId:                 CompId,
  var name:             String,
  var stageConfig:      StageConfig,
  var status:           StageStatus,
  var demo:             Boolean,
  var size:             Int,
  var noPlayers:        Int,
  var data:             StageData,
  var noWinSets:        Int = 0,
  var prefId:           Option[StageId] = None,
  var nextIds:          List[StageId] = List(),
  var quali:            QualifyTyp = QualifyTyp.ALL,
  var deleted:          Boolean = false,
  var version:          Int = 0,
  val matches:          ArrayBuffer[MEntry] = ArrayBuffer.empty
):

  // -----------------------------
  // Mutable state (kept explicit)
  // -----------------------------
  val candidates: ArrayBuffer[(Pant, Boolean)] = ArrayBuffer.empty
  var candInfo: String = ""

  // -----------------------------
  // Derived counters (safer)
  // -----------------------------
  def mFinished: Int = matches.count(_.finished)
  def mTotal: Int    = matches.length
  def mFix: Int      = matches.count(_.status == MEntry.MS_FIX)

  // -----------------------------
  // Convenience helpers
  // -----------------------------
  def isGroupStage: Boolean = stageConfig.format == StageFormat.GR
  def isKoStage: Boolean = stageConfig.format == StageFormat.KO


  /**
   * Initialisiert die Matches für diese Stage basierend auf dem Wettbewerbstyp.
   *
   * Löscht zunächst alle vorhandenen Matches und fügt dann die neu generierten Matches
   * entsprechend des Stage-Formats (Gruppen, Round Robin, Swiss oder Knockout) hinzu.
   *
   * @param coTyp Der Wettbewerbstyp (CompTyp).
   * @return Left(AppError) im Fehlerfall oder Right(Boolean) zur Bestätigung des Erfolgs.
   */
  def initMatches(coTyp: CompTyp): Either[shared.basic.AppError, Boolean] =
    matches.clear()
    data match
      case StageData.GroupsStage(groups) => initGrMatches(groups, coTyp)
      case StageData.RoundRobinStage(rr) => initRrMatches(rr, coTyp)
      case StageData.SwissStage(sw)      => initSwMatches(sw, coTyp)
      case StageData.KnockoutStage(ko)   => initKoMatches(ko, coTyp)

  /**
   * Setzt alle Matches dieser Stage zurück und berechnet die Gruppenplatzierungen neu.
   *
   * @return Left(AppError) bei Berechnungsfehlern oder Right(()) bei Erfolg.
   */
  def resetMatches(): Either[shared.basic.AppError, Unit] = 
    for i <- 0 until matches.length do matches(i).reset()
    data match
      case StageData.GroupsStage(groups) => resetGrMatches(groups)
      case StageData.RoundRobinStage(rr) => resetRrMatches(rr)
      case StageData.SwissStage(sw)      => resetSwMatches(sw)
      case StageData.KnockoutStage(ko)   => resetKoMatches(ko)

  /**
   * Setzt alle Gruppenspiele zurück und berechnet die Platzierungen für jede Gruppe neu.
   *
   * @param groups Die Liste der Gruppen dieser Stage.
   * @return Left(AppError) bei Berechnungsfehlern oder Right(()) bei Erfolg.
   */
  private def resetGrMatches(groups: ArrayBuffer[Group]): Either[shared.basic.AppError, Unit] =
    groups.foldLeft[Either[shared.basic.AppError, Unit]](Right(())) { (acc, group) =>
      acc.flatMap(_ => group.resetMatches())
    }

  private def resetRrMatches(rrGroup: RrGroup): Either[shared.basic.AppError, Unit] =
    rrGroup.resetMatches()

  /**
   * Dummy-Methode zum Zurücksetzen von Swiss-System-Matches.
   */
  private def resetSwMatches(swGroup: SwGroup): Either[shared.basic.AppError, Unit] =
    Right(())

  /**
   * Dummy-Methode zum Zurücksetzen von Knockout-Matches.
   */
  private def resetKoMatches(ko: KoStage): Either[shared.basic.AppError, Unit] =
    Right(())

  private def initGrMatches(groups: ArrayBuffer[Group], coTyp: CompTyp): Either[shared.basic.AppError, Boolean] =
    import shared.format.Groups
    Groups.initGrMatches(coId, coTyp, id, stageConfig.format, noWinSets, groups) match
      case Right(grMatches) =>
        matches ++= grMatches
        Right(true)
      case Left(err) =>
        Left(err)

  private def initRrMatches(rrGroup: Group, coTyp: CompTyp): Either[shared.basic.AppError, Boolean] =
    initGrMatches(ArrayBuffer(rrGroup), coTyp)

  private def initSwMatches(swGroup: Group, coTyp: CompTyp): Either[shared.basic.AppError, Boolean] =
    initGrMatches(ArrayBuffer(swGroup), coTyp)

  private def initKoMatches(ko: KoStage, coTyp: CompTyp): Either[shared.basic.AppError, Boolean] =
    import shared.format.SingleElimination
    SingleElimination.initKoMatches(coId, coTyp, id, stageConfig.format, noWinSets, ko) match
      case Right(koMatches) =>
        matches ++= koMatches
        Right(true)
      case Left(err) =>
        Left(err)



  // getMatch
  def getMatch(game: Int): MEntry = 
    matches(game - 1)

  // existsMatchNo
  def existsMatchNo(gameNo: Int): Boolean =
    gameNo >= 1 && gameNo <= matches.length

  // depFinished
  def depFinished(gameNo: Int, format: StageFormat): Boolean =
    format match {
      case StageFormat.GR | StageFormat.RR | StageFormat.SW =>
        getMatch(gameNo) match {
          case mgr: MEntryGr =>
            mgr.getDepend().forall { depGameNo =>
              matches.find(_.gameNo == depGameNo).exists(_.finished)
            }
          case _ => true
        }
      case _ =>
        true
    }

  // inputMatch - set match result, info, playfield ....
  def inputMatch(gameNo: Int, sets: (Int,Int), result: String, info: String, playfield: String): Either[shared.basic.AppError, List[Int]] = {
    try {
      val m = getMatch(gameNo)
      m.setSets(sets)
      m.setResult(result)
      m.setInfo(info)
      m.setPlayfield(playfield)
      m.setStatus(depFinished(gameNo, m.stageFormat))
      setModel(m)
      updateStatus() 
      Right(propMatch(gameNo))
    } catch { case _: Throwable => Left(shared.basic.AppError("err0224.coph.inputMatch.invalidGameNo", gameNo.toString))} 
  }  

  // propMatch
  def propMatch(gameNo: Int): List[Int] = 
    try {
      val triggerList = scala.collection.mutable.ListBuffer[Int](gameNo)
      val m = getMatch(gameNo)

      m.stageFormat match {
        case StageFormat.GR | StageFormat.RR | StageFormat.SW => {
          val trigger = m.asInstanceOf[MEntryGr].getTrigger()
          for (g <- trigger) { 
            val nm = getMatch(g)
            nm.setStatus(depFinished(g, m.stageFormat))
            setModel(nm)
            triggerList.append(g) 
          }
        }

        case StageFormat.KO => {
          val (gWin, pWin) = m.asInstanceOf[MEntryKo].getWinPos()
          // propagate winner
          if (existsMatchNo(gWin) && m.finished) { 
            val nmWin = getMatch(gWin)
            nmWin.setPant(pWin, m.getWinner())
            nmWin.setStatus(true)
            
            // Check if this next match is now a bye match
            if (nmWin.stNoA.isBye || nmWin.stNoB.isBye) {
              val (setsA, setsB) = if (nmWin.stNoA.isBye) (0, nmWin.winSets) else (nmWin.winSets, 0)
              nmWin.setSets((setsA, setsB))
              nmWin.setResult("")
              nmWin.setStatus(MEntry.MS_FIX)
            }

            setModel(nmWin)
            triggerList.append(gWin)
            if (nmWin.finished) {
              triggerList ++= propMatch(gWin)
            }
          }  
          // propagate looser i.e. 3rd place match
          val (gLoo, pLoo) = m.asInstanceOf[MEntryKo].getLooPos()
          if (existsMatchNo(gLoo) && m.finished) {
            val nmLoo = getMatch(gLoo)
            nmLoo.setPant(pLoo, m.getLooser())
            nmLoo.setStatus(true)

            // Check if this next match is now a bye match
            if (nmLoo.stNoA.isBye || nmLoo.stNoB.isBye) {
              val (setsA, setsB) = if (nmLoo.stNoA.isBye) (0, nmLoo.winSets) else (nmLoo.winSets, 0)
              nmLoo.setSets((setsA, setsB))
              nmLoo.setResult("")
              nmLoo.setStatus(MEntry.MS_FIX)
            }

            setModel(nmLoo)
            triggerList.append(gLoo)
            if (nmLoo.finished) {
              triggerList ++= propMatch(gLoo)
            }
          }
        }
        case _ => {}
      }
      updateStatus() 
      triggerList.toList.distinct
    } catch { case _: Throwable => println("ERROR propMatch exception"); List() }  

  // resetMatchesPropagate
  def resetMatchesPropagate(): Either[shared.basic.AppError, List[Int]] = 
    try {
      var error = shared.basic.AppError.dummy
      val triggerList = scala.collection.mutable.ListBuffer[Int]()
      
      stageConfig.format match {
        case StageFormat.KO | StageFormat.GR | StageFormat.RR | StageFormat.SW  => 
          for (i <- 0 until matches.length) {
            if (matches(i).status == MEntry.MS_FIN || matches(i).status == MEntry.MS_RUN) {
              resetMatch(matches(i).gameNo) match {
                case Left(err)  => error = err
                case Right(res) => triggerList ++= res
              }
            }
          }
        case _ => {}
      }
      if (error.isDummy) Right(triggerList.distinct.sorted.toList) else Left(error)
    } catch { case _: Throwable => Left(shared.basic.AppError("err0229.svc.resetMatches.failed")) }

  // resetMatch
  def resetMatch(gameNo: Int, resetPantA: Boolean = false, resetPantB: Boolean = false): Either[shared.basic.AppError, List[Int]] = 
    try {
      var error = shared.basic.AppError.dummy
      val triggerList = scala.collection.mutable.ListBuffer[Int](gameNo)
      val m = getMatch(gameNo)
      if (m.status == MEntry.MS_FIX && !resetPantA && !resetPantB) {
        return Right(List())
      }
      m.reset(resetPantA, resetPantB)

      m.stageFormat match {
        case StageFormat.GR | StageFormat.RR  | StageFormat.SW  => {
          m.setStatus(depFinished(gameNo, m.stageFormat))
          setModel(m)

          // set status for every match to be triggered
          val trigger = m.asInstanceOf[MEntryGr].getTrigger()
          for (g <- trigger) { 
            val nm = getMatch(g)
            nm.setStatus(depFinished(g, m.stageFormat))
            setModel(nm)
            triggerList.append(g)
          }  
        }

        case StageFormat.KO => {
          m.setStatus(true)
          setModel(m)      
          
          // propagate deletion of that position
          val (gWin, pWin) = m.asInstanceOf[MEntryKo].getWinPos()
          if (existsMatchNo(gWin)) {
            resetMatch(gWin, pWin == 0, pWin == 1) match {
              case Left(err)  => error = err
              case Right(res) => triggerList ++= res
            }
          }
          
          // propagate looser i.e. 3rd place match
          val (gLoo, pLoo) = m.asInstanceOf[MEntryKo].getLooPos()
          if (existsMatchNo(gLoo)) {
            resetMatch(gLoo, pLoo == 0, pLoo == 1) match {
              case Left(err) => error = err
              case Right(res) => triggerList ++= res
            }
          }
        }
        case _ => {}
      }
      updateStatus() 
      if (error.isDummy) Right(triggerList.toList.distinct) else Left(error)
    } catch { case _: Throwable => Left(shared.basic.AppError("err0230.svc.resetMatch.game", gameNo.toString))}

  // updateStatus
  def updateStatus(): Unit = { 
    val mFinished = matches.count(_.finished)
    val mTotal = matches.length
    
    status match {
      case StageStatus.CFG  => Log.error(s"Stage.updateStatus -> status=${status}") 
      case StageStatus.AUS  => 
      case StageStatus.EIN  => if (mFinished == mTotal) status = StageStatus.FIN
      case StageStatus.FIN  => if (mFinished < mTotal)  status = StageStatus.EIN
      case _ =>
    }
  }

  // setModel enter result into the corresponding model 
  def setModel(m: MEntry): Unit = {  
    try {
      if (m.gameNo - 1 >= 0 && m.gameNo - 1 < matches.length) {
        matches(m.gameNo - 1) = m 
      } else {
        println(s"ERROR setModel index out of range ${m.gameNo}")
      }

      m.stageFormat match {
        case StageFormat.GR | StageFormat.RR | StageFormat.SW =>
          val mtch = m.asInstanceOf[MEntryGr]
          data match {
            case StageData.GroupsStage(groups) =>
              if (mtch.grId > 0 && mtch.grId <= groups.length) {
                groups(mtch.grId - 1).setMatch(mtch) match {
                  case Left(err) => println(s"ERROR setModel: group match: ${err.toString}")
                  case Right(res) => if (res) groups(mtch.grId - 1).calc() match {
                    case Left(err) => println(s"ERROR setModel: calc failed: ${err.toString}")
                    case Right(_) => ()
                  } else {
                    println("ERROR setModel: set group match, invalid param")
                  }
                }
              } else {
                println("ERROR setModel set group match, invalid group id")
              }
            case StageData.RoundRobinStage(rrGroup) =>
              if (mtch.grId == 1) {
                rrGroup.setMatch(mtch) match {
                  case Left(err) => println(s"ERROR setModel: rr match: ${err.toString}")
                  case Right(res) => if (res) rrGroup.calc() match {
                    case Left(err) => println(s"ERROR setModel: calc failed: ${err.toString}")
                    case Right(_) => ()
                  } else {
                    println("ERROR setModel: set rr match, invalid param")
                  }
                }
              }
            case StageData.SwissStage(swGroup) =>
              if (mtch.grId == 1) {
                swGroup.setMatch(mtch) match {
                  case Left(err) => println(s"ERROR setModel: sw match: ${err.toString}")
                  case Right(res) => if (res) swGroup.calc() match {
                    case Left(err) => println(s"ERROR setModel: calc failed: ${err.toString}")
                    case Right(_) => ()
                  } else {
                    println("ERROR setModel: set sw match, invalid param")
                  }
                }
              }
            case _ =>
              println("ERROR setModel: stage data doesn't match group format")
          }

        case StageFormat.KO =>
          // KO matches are stored directly in Stage.matches, no separate calculations needed in KoStage

        case _ =>
          println(s"ERROR setModel: invalid competition phase type")
      }
    } catch {
      case e: Throwable => println(s"ERROR setModel ${m.toString}: ${e.getMessage}")
    }
  }


object Stage:
  given rw: ReadWriter[Stage] = macroRW
  // def dummy: Stage = Stage(StageId(0), CompId(0), "", StageConfig.UNKN, StageStatus.UNKN, false, 0, 0)
