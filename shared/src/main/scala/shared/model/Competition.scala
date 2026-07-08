package shared.model

import shared.basic.Pickle.*
import shared.basic.AppError
import scala.collection.mutable. { ArrayBuffer, Map, Stack }
import ujson.*



enum CompCategory:
  case UNKNOWN
  case TT  // Table Tennis

object CompCategory:
  import shared.basic.Pickle.*

  given rw: ReadWriter[CompCategory] =
    readwriter[String].bimap[CompCategory](
      _.toString,
      s => try CompCategory.valueOf(s) catch { case _: Exception => CompCategory.UNKNOWN }
    )

enum CompTyp(val id: Int):
  case UNKN   extends CompTyp(0)
  case SINGLE extends CompTyp(1)
  case DOUBLE extends CompTyp(2)
  case MIXED  extends CompTyp(3)
  case TEAM2  extends CompTyp(4)
  case TEAM3  extends CompTyp(5)
  case TEAM4  extends CompTyp(6)
  case TEAM6  extends CompTyp(7)
  case Typ    extends CompTyp(99)

  def msgCode: String = s"CompTyp.${this.toString}"

  def equalsTo(compareWith: CompTyp*): Boolean =
    compareWith.contains(this)

  def name(
      mfun: (String, Seq[String]) => String,
      insert: String = ""
  ): String =
    mfun(msgCode, Seq(insert))

object CompTyp:
  import shared.basic.Pickle.*

  given rw: ReadWriter[CompTyp] =
    readwriter[String].bimap[CompTyp](
      _.toString,
      s => try CompTyp.valueOf(s) catch { case _: Exception => CompTyp.UNKN }
    )

  def fromString(value: String): CompTyp =
    value.toLowerCase match
      case "einzel" | "single" => CompTyp.SINGLE
      case "doppel" | "double" => CompTyp.DOUBLE
      case "mixed"             => CompTyp.MIXED
      case "team" | "team2"    => CompTyp.TEAM2
      case "team3"             => CompTyp.TEAM3
      case "team4"             => CompTyp.TEAM4
      case "team6"             => CompTyp.TEAM6
      case _                   => CompTyp.Typ

  def fromId(id: Int): CompTyp =
    values.find(_.id == id).getOrElse(CompTyp.UNKN)

  def apply(id: Int): CompTyp =
    fromId(id)


enum CompStatus(val id: Int):
  case UNKN   extends CompStatus(97)
  case Status extends CompStatus(98)
  case CFG    extends CompStatus(99)
  case RUN    extends CompStatus(100)
  case FIN    extends CompStatus(103)

  case READY  extends CompStatus(0)
  case VRAUS  extends CompStatus(1)
  case VREIN  extends CompStatus(2)
  case VRFIN  extends CompStatus(3)
  case ZRAUS  extends CompStatus(4)
  case ZREIN  extends CompStatus(5)
  case ZRFIN  extends CompStatus(6)
  case ERAUS  extends CompStatus(7)
  case EREIN  extends CompStatus(8)
  case ERFIN  extends CompStatus(9)

  def msgCode: String =
    s"CompStatus.${this.toString}"

  def equalsTo(compareWith: CompStatus*): Boolean =
    compareWith.contains(this)

object CompStatus:

  given rw: ReadWriter[CompStatus] =
    readwriter[String].bimap[CompStatus](
      _.toString,
      s => try CompStatus.valueOf(s) catch { case _: Exception => CompStatus.UNKN }
    )


case class CompCTT(
  ageGroup:             String = "",
  ratingRemark:         String = "",
  ratingLowLevel:       Int = 0,
  ratingUpperLevel:     Int = 0,
  sex:                  Int = 0,
  maxPersons:           Int = 0,
  entryFee:             String = "",
  ageFrom:              String = "",
  ageTo:                String = "",
  preliminaryRoundMode: String = "",
  finalRoundMode:       String = "",
  manualFinalRankings:  Boolean = false
)

object CompCTT:
  import shared.basic.Pickle.*
  given ReadWriter[CompCTT] = macroRW


opaque type CompId = Int

object CompId:
  def apply(value: Int): CompId = value
  def fromInt(value: Int): CompId = value
  
  extension (id: CompId)
    def value: Int = id

  given Ordering[CompId] = Ordering.Int

  given rw: ReadWriter[CompId] = 
    ReadWriter.join(IntReader, IntWriter).bimap(
      id => id, 
      value => CompId(value)
    )


case class Competition(
  id:                   CompId,
  var name:             String,
  var typ:              CompTyp,
  var category:         CompCategory,
  var startDate:        String, // Format: yyyy-MM-dd HH:mm:ss
  var status:           CompStatus,
  var startStage:       Option[StageId] = None,
  var activ:            Boolean = true,
  var webRegister:      Boolean = false,
  var lowLevel:         Option[Int] = None,
  var upperLevel:       Option[Int] = None,
  var cttInfo:          Option[CompCTT] = None,
  val pants1Stage:      ArrayBuffer[Pant] = ArrayBuffer(),
  var deleted:          Boolean = false,
  var version:          Int = 0
):
  var pant2idx: Map[SNO, Int] = Map.empty         // Pant id -> index in pant array
  val pantIdent2SNO: Map[String, SNO] = Map.empty

  def rebuildPantIdent2SNO(): Unit =
    pantIdent2SNO.clear()
    pants1Stage.foreach { p =>
      if (p.ident != null && p.ident.nonEmpty) {
        pantIdent2SNO(p.ident) = p.id
      }
    }

  def hash: Int = s"$name${typ.id}$startDate${getFromTTR}${getToTTR}".hashCode
  def equal(co: Competition): Boolean = hash == co.hash
  def setTyp(value: String): Competition = copy(typ = CompTyp.fromString(value))
  def getAgeGroup: String = cttInfo.map(_.ageGroup).getOrElse("")
  def getRatingLowLevel: Int = lowLevel.getOrElse(0)
  def getRatingUpperLevel: Int = upperLevel.getOrElse(0)
  def getFromTTR: String =
    if getRatingLowLevel > 0 then f"${getRatingLowLevel}%04d"
    else "0000"

  def getToTTR: String =
    if getRatingUpperLevel > 0 then f"${getRatingUpperLevel}%04d"
    else "XXXX"


object Competition:
  given rw: ReadWriter[Competition] =
    ReadWriter.join(
      macroRW[Competition].map { comp =>
        comp.rebuildPantIdent2SNO()
        comp
      },
      macroRW[Competition]
    )
  def dummy: Competition = Competition(CompId(0), "", CompTyp.UNKN, CompCategory.UNKNOWN, "", CompStatus.UNKN)


object CompDB:
  val MaxStage = 32
  val comps: Array[Competition] = Array.fill(MaxStage)(null)
  var compIdx: Map[CompId, Int] = Map.empty

  private val free = Stack.from(0 until MaxStage)
