package shared.model

import upickle.default.*
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
  // implicit val rw: ReadWriter[RoundCfg] = readwriter[String].bimap[RoundCfg](
  //     x => x.toString,           // Serialize: Enum -> String
  //     s => RoundCfg.valueOf(s)   // Deserialize: String -> Enum
  //   )

  implicit val rw: ReadWriter[RoundCfg] =
    readwriter[String].bimap[RoundCfg](
      _.toString,
      s => RoundCfg.values.find(_.toString == s).getOrElse(RoundCfg.UNKN)
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
  def fromId(id: Int): QualifyTyp =
    values.find(_.id == id).getOrElse(ALL)
  
  // Add this implicit ReadWriter
  implicit val rw: ReadWriter[QualifyTyp] = readwriter[String].bimap[QualifyTyp](
    _.toString,             // To JSON: Enum -> "ALL"
    valueOf(_)              // From JSON: "ALL" -> Enum.ALL
  )    


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
  // This tells uPickle: "Convert the Enum to its name string and back"
  implicit val rw: ReadWriter[RoundStatus] = readwriter[String].bimap[RoundStatus](
    s => s.toString,
    str => RoundStatus.valueOf(str)
  )  
  def fromId(id: Int): RoundStatus = values.find(_.id == id).getOrElse(UNKN)
  def fromName(name: String): RoundStatus = values.find(_.productPrefix == name).getOrElse(UNKN)


case class Round(
  id:             RoundId,
  name:           String,
  coId:           CompId,
  rndCfg:         RoundCfg,
  status:         RoundStatus,
  demo:           Boolean,
  size:           Int,
  noPlayers:      Int,
  noWinSets:      Int = 0,
  prefId:         Option[RoundId] = None,
  nextIds:        List[RoundId] = List(),
  quali:          QualifyTyp = QualifyTyp.ALL,
  var timestamp:  Int
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
  implicit val rw: ReadWriter[Round] = macroRW  


object RoundDB:
  val MaxRound = 256
  val rounds: Array[Round] = Array.fill(MaxRound)(null)

  private val free = Stack.from(0 until MaxRound)

  private def get(id: RoundId): Option[Round] =
    Option(rounds(id.value))

  def init = {
    // todo load Round json encoded from wordpress custom post type round_001 to round_256
    // set nextRoundId to max RoundId value
  }


  // =========================================================
  // WORDPRESS SYNC
  // =========================================================
  private def sync(id: RoundId): Unit =
    val postName = f"round_${id.value + 1}%03d"

    val json =
      if rounds(id.value) == null then ""
      else write[Round](rounds(id.value))

    // Wordpress.updateRoundPost(
    //   Global.postId,
    //   postName,
    //   json
    // )


  // =========================================================
  // ADD ROUND
  // =========================================================
  def addRound(prefId: Option[RoundId], base: Round): Round =
    if free.isEmpty then
      throw RuntimeException("Max rounds reached")

    val id = free.pop()
    val now = (System.currentTimeMillis() / 1000).toInt

    val r =
      base.copy(
        id = RoundId(id),
        prefId = prefId,
        nextIds = List(),
        timestamp = now
      )
    rounds(id) = r

    // Vorgänger aktualisieren
    prefId.foreach { pid =>
      val pref = rounds(pid.value)

      if pref != null then
        rounds(pid.value) =
          pref.copy(
            nextIds = pref.nextIds :+ RoundId(id),
            timestamp = now
          )

        sync(pid)
    }
  
    sync(RoundId(id))
    r



  // =========================================================
  // DELETE ROUND (inkl. aller Nachfolger)
  // =========================================================
  def deleteRound(id: RoundId): Unit =
    val r = rounds(id.value)

    if r == null then return

    // zuerst Nachfolger löschen
    r.nextIds.foreach(deleteRound)

    val now = (System.currentTimeMillis() / 1000).toInt

    // aus Vorgänger austragen
    r.prefId.foreach { pid =>
      val pref = rounds(pid.value)

      if pref != null then
        rounds(pid.value) =
          pref.copy(
            nextIds = pref.nextIds.filterNot(_ == id),
            timestamp = now
          )

        sync(pid)
    }

    rounds(id.value) = null
    free.push(id.value)

    sync(id)

 