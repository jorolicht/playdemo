package shared.model

import shared.basic.Pickle.*
import shared.basic.AppError
import scala.collection.mutable. { ArrayBuffer, Map, Stack }


enum CompTyp(val id: Int):
  case UNKN   extends CompTyp(0)
  case SINGLE extends CompTyp(1)
  case DOUBLE extends CompTyp(2)
  case MIXED  extends CompTyp(3)
  case TEAM   extends CompTyp(4)
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

  given ReadWriter[CompTyp] =
    readwriter[String].bimap[CompTyp](
      _.toString,
      s => try CompTyp.valueOf(s) catch { case _: Exception => CompTyp.UNKN }
    )

  def fromString(value: String): CompTyp =
    value.toLowerCase match
      case "einzel" | "single" => CompTyp.SINGLE
      case "doppel" | "double" => CompTyp.DOUBLE
      case "mixed"             => CompTyp.MIXED
      case "team"              => CompTyp.TEAM
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

  given ReadWriter[CompStatus] =
    readwriter[String].bimap[CompStatus](
      _.toString,
      s => try CompStatus.valueOf(s) catch { case _: Exception => CompStatus.UNKN }
    )


/* Competition (based on Click TT Competition definition)                            
 * Tournament/Event can have 1..n competitions
 *             
 *     !ELEMENT competition (players, matches?)>                                    
 *     !ATTLIST competition                                      
 *       age-group CDATA #REQUIRED                            
 *       type (Einzel|Doppel|Mixed|Mannschaft) #REQUIRED                                                     
 *       start-date CDATA #REQUIRED                                                       
 *       ttr-from CDATA #IMPLIED      -> RatingLowerLevel                                 
 *       ttr-to CDATA #IMPLIED        -> RatingUpperLevel                          
 *       ttr-remarks CDATA #IMPLIED   -> RatingRemark      (e.g. C-Klasse)                          
 *       entry-fee CDATA #IMPLIED                                      
 *       age-from CDATA #IMPLIED                                       
 *       age-to CDATA #IMPLIED                                        
 *       sex CDATA #IMPLIED                 
 *       preliminary-round-playmode CDATA #IMPLIED                         
 *       final-round-playmode CDATA #IMPLIED                                   
 *       max-persons CDATA #IMPLIED                       
 *       manual-final-rankings (0|1) #IMPLIED
 */
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

  // Break the loop by referencing the specific Int ordering 
  // rather than letting the compiler search for one.
  given Ordering[CompId] = Ordering.Int

  // We use ReadWriter.join(IntReader, IntWriter) to avoid 
  // the 'readwriter[Int]' macro search entirely.
  given rw: ReadWriter[CompId] = 
    ReadWriter.join(IntReader, IntWriter).bimap(
      id => id, // Inside here, CompId is seen as Int
      value => CompId(value)
    )


case class Competition(
  id:                   CompId,
  name:                 String,
  typ:                  CompTyp,
  startDate:            String,
  var status:           CompStatus,
  var startRound:       Option[RoundId] = None,    // id of first/start Round
  var activ:            Boolean = true,
  var webRegister:      Boolean = false,
  var lowLevel:         Option[Int] = None,
  var upperLevel:       Option[Int] = None,
  var cttInfo:          Option[CompCTT] = None,
  val pants:            ArrayBuffer[Pant] = ArrayBuffer(),
  val deleted:          Boolean = false,
  var version:          Int = 0
):
  var pant2idx: Map[SNO, Int] = Map.empty         // Pant id -> index in pant array

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
  given ReadWriter[Competition] = macroRW


object CompDB:
  val MaxRound = 32
  val comps: Array[Competition] = Array.fill(MaxRound)(null)
  var compIdx: Map[CompId, Int] = Map.empty

  private val free = Stack.from(0 until MaxRound)
  