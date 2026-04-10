package shared.model

import shared.basic.Pickle.*
import scala.collection.mutable.{ ArrayBuffer, Map, Set, Stack }

opaque type RoundId = Int
object RoundId:
  def apply(value: Int): RoundId = value
  def fromInt(value: Int): RoundId = value

  extension (id: RoundId)
    def value: Int = id

  // We use 'IntReader' and 'IntWriter' specifically to avoid 
  // the 'readwriter[Int]' macro search.
  given rw: ReadWriter[RoundId] = 
    ReadWriter.join(IntReader, IntWriter).bimap(
      (id: RoundId) => id: Int,
      (value: Int) => RoundId(value)
    )

// -----------------------------
// RoundCfg
// -----------------------------
enum RoundCfg(val id: Int, val typ: RoundTyp) derives CanEqual, ReadWriter:

  // --- Basic Phases ---
  case UNKN   extends RoundCfg(-1, RoundTyp.UNKN)
  case CFG    extends RoundCfg(0,  RoundTyp.UNKN)

  case VRGR   extends RoundCfg(1,  RoundTyp.GR)
  case ZRGR   extends RoundCfg(2,  RoundTyp.GR)
  case ERGR   extends RoundCfg(3,  RoundTyp.GR)
  case TRGR   extends RoundCfg(4,  RoundTyp.GR)

  case VRKO   extends RoundCfg(6,  RoundTyp.KO)
  case ERKO   extends RoundCfg(8,  RoundTyp.KO)
  case TRKO   extends RoundCfg(9,  RoundTyp.KO)

  // --- Group Systems ---
  case GR3to9 extends RoundCfg(100, RoundTyp.GR)
  case GRPS3  extends RoundCfg(101, RoundTyp.GR)
  case GRPS34 extends RoundCfg(102, RoundTyp.GR)
  case GRPS4  extends RoundCfg(103, RoundTyp.GR)
  case GRPS45 extends RoundCfg(104, RoundTyp.GR)
  case GRPS5  extends RoundCfg(105, RoundTyp.GR)
  case GRPS56 extends RoundCfg(106, RoundTyp.GR)
  case GRPS6  extends RoundCfg(107, RoundTyp.GR)

  // --- Tournament Systems ---
  case RR     extends RoundCfg(108, RoundTyp.RR)
  case KO     extends RoundCfg(109, RoundTyp.KO)
  case SW     extends RoundCfg(110, RoundTyp.SW)

  // --------------------------------

  def msgCode: String = s"RoundCfg.$productPrefix"
  def infoCode: String = s"RoundCfgInfo.$productPrefix"
  def isOneOf(values: RoundCfg*): Boolean = values.contains(this)

object RoundCfg:
  def fromId(id: Int): RoundCfg =
    values.find(_.id == id).getOrElse(UNKN)
      


// -----------------------------
// QualifyTyp
// -----------------------------
enum QualifyTyp(val id: Int) derives CanEqual, ReadWriter:
  case ALL extends QualifyTyp(0)
  case WIN extends QualifyTyp(1)
  case LOO extends QualifyTyp(2)
  case MAN extends QualifyTyp(3)

  def msgCode: String = s"QualifyTyp.$productPrefix"

object QualifyTyp:
  def fromId(id: Int): QualifyTyp =
    values.find(_.id == id).getOrElse(ALL)


// -----------------------------
// RoundTyp
// -----------------------------
enum RoundTyp(val id: Int) derives CanEqual, ReadWriter:

  case UNKN extends RoundTyp(-1)
  case GR   extends RoundTyp(1)
  case KO   extends RoundTyp(2)
  case SW   extends RoundTyp(3)
  case RR   extends RoundTyp(4)

  def msgCode: String = s"RoundTyp.$productPrefix"

object RoundTyp:
  def fromId(id: Int): RoundTyp = values.find(_.id == id).getOrElse(UNKN)
  def fromName(name: String): RoundTyp = values.find(_.productPrefix == name).getOrElse(UNKN)
  def apply(id: Int): RoundTyp = fromId(id)  


// -----------------------------
// RoundStatus
// -----------------------------
enum RoundStatus(val id: Int) derives CanEqual, ReadWriter:
  case CFG  extends RoundStatus(0)
  case AUS  extends RoundStatus(1)
  case EIN  extends RoundStatus(2)
  case FIN  extends RoundStatus(3)
  case UNKN extends RoundStatus(-1)

  def msgCode: String = s"RoundStatus.$productPrefix"

object RoundStatus:
  def fromId(id: Int): RoundStatus = values.find(_.id == id).getOrElse(UNKN)
  def fromName(name: String): RoundStatus = values.find(_.productPrefix == name).getOrElse(UNKN)


case class Round(
  id:                   RoundId,
  coId:                 CompId,
  name:                 String,
  rndCfg:               RoundCfg,
  var status:           RoundStatus,
  var demo:             Boolean,
  var size:             Int,
  var noPlayers:        Int,
  val noWinSets:        Int = 0,
  var prefId:           Option[RoundId] = None,
  var nextIds:          List[RoundId] = List(),
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
  var ko: KoRound = KoRound(0, "", 0L ,0 ,0)

  // -----------------------------
  // Derived counters (safer)
  // -----------------------------
  def mFinished: Int = 0
  def mTotal: Int    = 0
  def mFix: Int      = 0

  // -----------------------------
  // Convenience helpers
  // -----------------------------
  def isGroupRound: Boolean = rndCfg.typ == RoundTyp.GR
  def isKoRound: Boolean = rndCfg.typ == RoundTyp.KO


object Round:
  given ReadWriter[Round] = macroRW
