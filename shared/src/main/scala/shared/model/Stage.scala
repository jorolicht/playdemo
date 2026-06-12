package shared.model

import shared.basic.Pickle.*
import scala.collection.mutable.{ ArrayBuffer, Map, Set, Stack }
import shared.format.*

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
  case UNKN   extends StageConfig(-1, StageFormat.UNKN)
  case CFG    extends StageConfig(0,  StageFormat.UNKN)

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

  case UNKN extends StageFormat(-1)
  case GR   extends StageFormat(1)
  case KO   extends StageFormat(2)
  case SW   extends StageFormat(3)
  case RR   extends StageFormat(4)

  def msgCode: String = s"StageFormat.$productPrefix"

object StageFormat:
  given rw: ReadWriter[StageFormat] =
    readwriter[String].bimap[StageFormat](
      _.toString,
      s => try StageFormat.valueOf(s) catch { case _: Exception => StageFormat.UNKN }
    )

  def fromId(id: Int): StageFormat = values.find(_.id == id).getOrElse(UNKN)
  def fromName(name: String): StageFormat = values.find(_.productPrefix == name).getOrElse(UNKN)
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


case class Stage(
  id:                   StageId,
  coId:                 CompId,
  var name:             String,
  var stageConfig:      StageConfig,
  var status:           StageStatus,
  var demo:             Boolean,
  var size:             Int,
  var noPlayers:        Int,
  var noWinSets:        Int = 0,
  var prefId:           Option[StageId] = None,
  var nextIds:          List[StageId] = List(),
  var quali:            QualifyTyp = QualifyTyp.ALL,
  var deleted:          Boolean = false,
  var version:          Int = 0
):

  // -----------------------------
  // Mutable state (kept explicit)
  // -----------------------------
  val candidates: ArrayBuffer[(Pant, Boolean)] = ArrayBuffer.empty
  var candInfo: String = ""
  val matches: ArrayBuffer[MEntry] = ArrayBuffer.empty
  val groups: ArrayBuffer[Group] = ArrayBuffer.empty
  var ko: KoStage = KoStage(0, "", 0L ,0 ,0)

  // -----------------------------
  // Derived counters (safer)
  // -----------------------------
  def mFinished: Int = 0
  def mTotal: Int    = 0
  def mFix: Int      = 0

  // -----------------------------
  // Convenience helpers
  // -----------------------------
  def isGroupStage: Boolean = stageConfig.format == StageFormat.GR
  def isKoStage: Boolean = stageConfig.format == StageFormat.KO


object Stage:
  given rw: ReadWriter[Stage] = macroRW
  def dummy: Stage = Stage(StageId(0), CompId(0), "", StageConfig.UNKN, StageStatus.UNKN, false, 0, 0)
