package shared.model

import upickle.default.*
import scala.collection.mutable

opaque type CompRoundId = Int
object CompRoundId:
  def apply(value: Int): CompRoundId = value

  def fromInt(value: Int): CompRoundId = value

  extension (id: CompRoundId)
    def value: Int = id

  // We use 'IntReader' and 'IntWriter' specifically to avoid 
  // the 'readwriter[Int]' macro search.
  given rw: ReadWriter[CompRoundId] = 
    ReadWriter.join(IntReader, IntWriter).bimap(
      (id: CompRoundId) => id: Int,
      (value: Int) => CompRoundId(value)
    )



case class CompRound(
  id:         CompRoundId,
  name:       String,
  coId:       CompId,
  rndCfg:     CompRoundCfg,
  status:     CompRoundStatus,
  demo:       Boolean,
  size:       Int,
  noPlayers:  Int,
  noWinSets:  Int = 0,
  prefId:     Option[CompRoundId] = None,
  nextIds:    List[CompRoundId] = List(),
  quali:      QualifyTyp = QualifyTyp.ALL
):

  // -----------------------------
  // Mutable state (kept explicit)
  // -----------------------------
  val candidates: mutable.ArrayBuffer[(Pant, Boolean)] =
    mutable.ArrayBuffer.empty

  var candInfo: String = ""

  val matches: mutable.ArrayBuffer[MEntry] =
    mutable.ArrayBuffer.empty

  val groups: mutable.ArrayBuffer[Group] =
    mutable.ArrayBuffer.empty

  var ko: KoRound =
    KoRound(0, "", 0L ,0 ,0)


  // -----------------------------
  // Derived counters (safer)
  // -----------------------------
  def mFinished: Int = 0
  def mTotal: Int    = 0
  def mFix: Int      = 0

  // -----------------------------
  // Convenience helpers
  // -----------------------------
  def isGroupRound: Boolean =
    rndCfg.typ == CompRoundTyp.GR

  def isKoRound: Boolean =
    rndCfg.typ == CompRoundTyp.KO


// -----------------------------
// CompRoundCfg
// -----------------------------
enum CompRoundCfg(val id: Int, val typ: CompRoundTyp) derives CanEqual:

  // --- Basic Phases ---
  case UNKN   extends CompRoundCfg(-1, CompRoundTyp.UNKN)
  case CFG    extends CompRoundCfg(0,  CompRoundTyp.UNKN)

  case VRGR   extends CompRoundCfg(1,  CompRoundTyp.GR)
  case ZRGR   extends CompRoundCfg(2,  CompRoundTyp.GR)
  case ERGR   extends CompRoundCfg(3,  CompRoundTyp.GR)
  case TRGR   extends CompRoundCfg(4,  CompRoundTyp.GR)

  case VRKO   extends CompRoundCfg(6,  CompRoundTyp.KO)
  case ERKO   extends CompRoundCfg(8,  CompRoundTyp.KO)
  case TRKO   extends CompRoundCfg(9,  CompRoundTyp.KO)

  // --- Group Systems ---
  case GR3to9 extends CompRoundCfg(100, CompRoundTyp.GR)
  case GRPS3  extends CompRoundCfg(101, CompRoundTyp.GR)
  case GRPS34 extends CompRoundCfg(102, CompRoundTyp.GR)
  case GRPS4  extends CompRoundCfg(103, CompRoundTyp.GR)
  case GRPS45 extends CompRoundCfg(104, CompRoundTyp.GR)
  case GRPS5  extends CompRoundCfg(105, CompRoundTyp.GR)
  case GRPS56 extends CompRoundCfg(106, CompRoundTyp.GR)
  case GRPS6  extends CompRoundCfg(107, CompRoundTyp.GR)

  // --- Tournament Systems ---
  case RR     extends CompRoundCfg(108, CompRoundTyp.RR)
  case KO     extends CompRoundCfg(109, CompRoundTyp.KO)
  case SW     extends CompRoundCfg(110, CompRoundTyp.SW)

  // --------------------------------

  def msgCode: String = s"CompRoundCfg.$productPrefix"

  def infoCode: String = s"CompRoundCfgInfo.$productPrefix"

  def isOneOf(values: CompRoundCfg*): Boolean = values.contains(this)

object CompRoundCfg:
  def fromId(id: Int): CompRoundCfg =
    values.find(_.id == id).getOrElse(UNKN)    


// -----------------------------
// CompRoundTyp
// -----------------------------
enum CompRoundTyp(val id: Int) derives CanEqual:

  case UNKN extends CompRoundTyp(-1)
  case GR   extends CompRoundTyp(1)
  case KO   extends CompRoundTyp(2)
  case SW   extends CompRoundTyp(3)
  case RR   extends CompRoundTyp(4)

  def msgCode: String =
    s"CompRoundTyp.$productPrefix"

object CompRoundTyp:

  def fromId(id: Int): CompRoundTyp =
    values.find(_.id == id).getOrElse(UNKN)

  def fromName(name: String): CompRoundTyp =
    values.find(_.productPrefix == name).getOrElse(UNKN)

  def apply(id: Int): CompRoundTyp =
    fromId(id)  


// -----------------------------
// CompRoundStatus
// -----------------------------
enum CompRoundStatus(val id: Int) derives CanEqual:
  case CFG  extends CompRoundStatus(0)
  case AUS  extends CompRoundStatus(1)
  case EIN  extends CompRoundStatus(2)
  case FIN  extends CompRoundStatus(3)
  case UNKN extends CompRoundStatus(-1)

  def msgCode: String =
    s"CompRoundStatus.$productPrefix"

object CompRoundStatus:

  def fromId(id: Int): CompRoundStatus =
    values.find(_.id == id).getOrElse(UNKN)

  def fromName(name: String): CompRoundStatus =
    values.find(_.productPrefix == name).getOrElse(UNKN)    


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
  def fromId(id: Int): QualifyTyp =
    values.find(_.id == id).getOrElse(ALL)    
