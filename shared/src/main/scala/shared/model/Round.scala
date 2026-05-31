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
enum RoundCfg(val id: Int, val typ: RoundTyp) derives CanEqual:

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
  case GRPS3  extends RoundCfg(101, RoundTyp.GR)   // only groups with 3 players 
  case GRPS34 extends RoundCfg(102, RoundTyp.GR)   // only groups with 3 or 4 players
  case GRPS4  extends RoundCfg(103, RoundTyp.GR)   // only groups with 4 players
  case GRPS45 extends RoundCfg(104, RoundTyp.GR)   // only groups with 4 or 5 players
  case GRPS5  extends RoundCfg(105, RoundTyp.GR)   // only groups with 5 players
  case GRPS56 extends RoundCfg(106, RoundTyp.GR)   // only groups with 5 or 6 players
  case GRPS6  extends RoundCfg(107, RoundTyp.GR)   // only groups with 6 players
  case GRPS7  extends RoundCfg(111, RoundTyp.GR)   // only groups with 7 players
  case GRPS8  extends RoundCfg(112, RoundTyp.GR)   // only groups with 8 players

  // --- Tournament Systems ---
  case RR     extends RoundCfg(108, RoundTyp.RR)    // Round Robin - everyone plays everyone
  case KO     extends RoundCfg(109, RoundTyp.KO)    // Knockout - single elimination   
  case SW     extends RoundCfg(110, RoundTyp.SW)    // Swiss - players are paired in each round based on their current score, with the goal of matching players with similar performance.

  // --------------------------------

  def msgCode: String = s"RoundCfg.$productPrefix"
  def infoCode: String = s"RoundCfgInfo.$productPrefix"
  def isOneOf(values: RoundCfg*): Boolean = values.contains(this)

object RoundCfg:
  given rw: ReadWriter[RoundCfg] =
    readwriter[String].bimap[RoundCfg](
      _.toString,
      s => try RoundCfg.valueOf(s) catch { case _: Exception => RoundCfg.UNKN }
    )

  def fromId(id: Int): RoundCfg =
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
// RoundTyp
// -----------------------------
enum RoundTyp(val id: Int) derives CanEqual:

  case UNKN extends RoundTyp(-1)
  case GR   extends RoundTyp(1)
  case KO   extends RoundTyp(2)
  case SW   extends RoundTyp(3)
  case RR   extends RoundTyp(4)

  def msgCode: String = s"RoundTyp.$productPrefix"

object RoundTyp:
  given rw: ReadWriter[RoundTyp] =
    readwriter[String].bimap[RoundTyp](
      _.toString,
      s => try RoundTyp.valueOf(s) catch { case _: Exception => RoundTyp.UNKN }
    )

  def fromId(id: Int): RoundTyp = values.find(_.id == id).getOrElse(UNKN)
  def fromName(name: String): RoundTyp = values.find(_.productPrefix == name).getOrElse(UNKN)
  def apply(id: Int): RoundTyp = fromId(id)  


// -----------------------------
// RoundStatus
// -----------------------------
enum RoundStatus(val id: Int) derives CanEqual:
  case CFG  extends RoundStatus(0)
  case AUS  extends RoundStatus(1)
  case EIN  extends RoundStatus(2)
  case FIN  extends RoundStatus(3)
  case UNKN extends RoundStatus(-1)

  def msgCode: String = s"RoundStatus.$productPrefix"

object RoundStatus:
  given rw: ReadWriter[RoundStatus] =
    readwriter[String].bimap[RoundStatus](
      _.toString,
      s => try RoundStatus.valueOf(s) catch { case _: Exception => RoundStatus.UNKN }
    )

  def fromId(id: Int): RoundStatus = values.find(_.id == id).getOrElse(UNKN)
  def fromName(name: String): RoundStatus = values.find(_.productPrefix == name).getOrElse(UNKN)


case class Round(
  id:                   RoundId,
  coId:                 CompId,
  var name:             String,
  var rndCfg:           RoundCfg,
  var status:           RoundStatus,
  var demo:             Boolean,
  var size:             Int,
  var noPlayers:        Int,
  var noWinSets:        Int = 0,
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
  given rw: ReadWriter[Round] = macroRW
  def dummy: Round = Round(RoundId(0), CompId(0), "", RoundCfg.UNKN, RoundStatus.UNKN, false, 0, 0)
