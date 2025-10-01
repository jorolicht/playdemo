package shared.model

import scala.util.matching
import upickle.default._
import upickle.default.{ReadWriter => RW, macroRW}

import scala.collection.mutable.{ ArrayBuffer, HashMap, Map }
import shared.Routines._


case class Competition(
  val id:           Long,             // auto increment primary key
  val name:         String,           // if empty initialised with ageGroup, ratingRemark, compType
  var typ:          CompTyp,          // migrated to Scala 3 enum
  val startDate:    String,           // Format: yyyymmdd#hhmm
  var status:       CompStatus,       // migrated to Scala 3 enum
  var options:      String = ""
):

  def hash: Int = s"${name}${typ.id.toString}${startDate}${getFromTTR}${getToTTR}".hashCode

  // mapping of licence to player identification according to clickTT participant list
  // currently only for single player 
  var cttLic2player: Map[String, String] = Map().withDefaultValue("")

  def getTyp(value: String) = value.toLowerCase match
    case "einzel" | "single" => CompTyp.SINGLE
    case "doppel" | "double" => CompTyp.DOUBLE
    case "mixed"             => CompTyp.MIXED
    case "team"              => CompTyp.TEAM
    case _                   => CompTyp.Typ

  def setTyp(value: String) = { typ = getTyp(value) }

  def getOptStr(index: Int): String   = getMDStr(options, index)
  def getOptInt(index: Int): Int      = getMDInt(options, index)
  def getOptLong(index: Int): Long    = getMDLong(options, index)
  def setOpt[X](value: X, index: Int) = { options = setMD(options, value, index) }

  def getAgeGroup: String      = getMDStr(options,0);   def setAgeGroup(value: String)      = { options = setMD(options, value, 0) }
  def getRatingRemark: String  = getMDStr(options,1);   def setRatingRemark(value: String)  = { options = setMD(options, value, 1) }
  def getRatingLowLevel: Int   = getMDInt(options,2);   def setRatingLowLevel(value: Int)   = { options = setMD(options, value, 2) }
  def getRatingUpperLevel: Int = getMDInt(options,3);   def setRatingUpperLevel(value: Int) = { options = setMD(options, value, 3) }
  def getSex: Int              = getMDInt(options,4);   def setSex(value:Int)               = { options = setMD(options, value, 4) }
  def getMaxPerson: Int        = getMDInt(options,5);   def setMaxPerson(value:Int)         = { options = setMD(options, value, 5) }
  def getEntryFee: String      = getMDStr(options,6);   def setEntryFee(value:String)       = { options = setMD(options, value, 6) }
  def getAgeFrom: String       = getMDStr(options,7);   def setAgeFrom(value:String)        = { options = setMD(options, value, 7) }
  def getAgeTo: String         = getMDStr(options,8);   def setAgeTo(value:String)          = { options = setMD(options, value, 8) }
  def getPreRndMod: String     = getMDStr(options,9);   def setPreRndMod(value:String)      = { options = setMD(options, value, 9) }
  def getFinRndMod: String     = getMDStr(options,10);  def setFinRndMod(value:String)      = { options = setMD(options, value, 10) }
  def getManFinRank: String    = getMDStr(options,11);  def setManFinRank(value:String)     = { options = setMD(options, value, 11) }
  def getWebRegister: Boolean  = getMDBool(options,12); def setWebRegister(value:Boolean)   = { options = setMD(options, value, 12) }
  def getCurCoPhId: Int        = getMDInt(options,13);  def setCurCoPhId(value:Int)         = { options = setMD(options, value, 13) }

  // set basis for generating users/participants certificate
  // NONE         -> no certificates generated so far
  // Some(coPhId) -> competition phase / round which is basis for placement calculation 
  def getCertCoPhId = getMDIntOption(options, 14)
  def setCertCoPhId(value: Option[Int]) = { options = setMDOption(options, value, 14) }

  // formatTime - depending on local
  // 0 - date and time
  // 1 - date
  // 2 - time
  def formatTime(lang: String, fmt:Int=0): String =
    val datetime = """(\d\d\d\d\d\d\d\d)#(\d\d\d\d)""".r
    startDate match
      case datetime(ymd, time) => fmt match
        case 0 => int2date(ymd.toInt, lang, 0) + " " + int2time(time.toInt, lang)
        case 1 => int2date(ymd.toInt, lang, 0)
        case 2 => int2time(time.toInt, lang)
        case _ => startDate
      case _ => startDate

  def getStartDate(mfun:(String, Seq[String])=>String, format:Int=0) = formatTime(mfun("app.lang",Seq()), format )

  // validateDate - startDate is in range
  def validateDate(trnyStart: Int, trnyEnd: Int): Boolean =
    val (year, month, day, hour, minute) = ymdHM(startDate)
    val sDate = year * 10000 + month * 100 + day
    sDate >= trnyStart & (trnyEnd==0 | sDate <= trnyEnd)

  // Name composed from type, agegroup, ....
  // Name containing middle dot
  def isNameComposed():Boolean = name.contains("·")

  def genRange(): String =
    val lB = getRatingLowLevel
    val uB = getRatingUpperLevel
    if (lB > 0 & uB > 0) then f"[$lB%04.0f-$uB%04.0f]"
    else if (lB > 0 & uB == 0) then f"[$lB%04.0f-XXXX]"
    else if (lB == 0 & uB > 0) then f"[0000-$uB%04.0f]"
    else "0000-XXXX"

  def getFromTTR: String = if (getRatingLowLevel>0) "%04d".format(getRatingLowLevel) else "0000"
  def getToTTR: String   = if (getRatingUpperLevel>0) "%04d".format(getRatingUpperLevel) else "XXXX"

  def getStatusName(mfun:(String, Seq[String])=>String): String = mfun(status.msgCode, Seq())
  def getName(fun:(String, Seq[String])=>String): String =
    if name != "" then name else s"${getAgeGroup} ${getRatingRemark} ${typ.name(fun)}"

  def equal(co: Competition): Boolean = hash == co.hash

  def encode = write[Competition](this)

  def matchClickTT(ageGroup: String, ttrFrom: String, ttrTo: String, ttrRemark: String, cttType: String): Boolean =
    getAgeGroup.toLowerCase     == ageGroup.toLowerCase &&
    getRatingRemark.toLowerCase == ttrRemark.toLowerCase &&
    getRatingLowLevel           == ttrFrom.toIntOption.getOrElse(0) &&
    getRatingUpperLevel         == ttrTo.toIntOption.getOrElse(0) &&
    typ                         == getTyp(cttType)
end Competition


object Competition:

  implicit val compStatusReadWrite: upickle.default.ReadWriter[CompStatus] =
    upickle.default.readwriter[Int].bimap[CompStatus](_.id, CompStatus.fromId)

  implicit val compTypReadWrite: upickle.default.ReadWriter[CompTyp] =
    upickle.default.readwriter[Int].bimap[CompTyp](_.id, CompTyp.fromId)

  implicit def rw: RW[Competition] = macroRW

  def tupled = (this.apply _).tupled
  def init             = new Competition(0L, "",  CompTyp.Typ, "", CompStatus.CFG, "")
  def get(name: String)= new Competition(0L, name,  CompTyp.Typ, "", CompStatus.CFG, "")

  def decode(s: String): Either[AppError, Competition] =
    try Right(read[Competition](s))
    catch { case _: Throwable => Left(AppError("err0020.decode.Competition", s, "", "Competition.decode"))}

  def decSeq(comps: String): Either[AppError, Seq[Competition]] =
    try Right(read[Seq[Competition]](comps))
    catch { case _: Throwable => Left(AppError("err0061.decode.Competitions", comps.take(20), "", "Competition.deqSeq")) }
end Competition


enum CompTyp(val id: Int, val label: String):
  case UNKN   extends CompTyp(0,  "UNKN")
  case SINGLE extends CompTyp(1,  "SINGLE")
  case DOUBLE extends CompTyp(2,  "DOUBLE")
  case MIXED  extends CompTyp(3,  "MIXED")
  case TEAM   extends CompTyp(4,  "TEAM")
  case Typ    extends CompTyp(99, "Typ")

  def msgCode: String = s"CompTyp.${this.toString}"
  def equalsTo(compareWith: CompTyp*): Boolean = compareWith.contains(this)
  def name(mfun: (String, Seq[String]) => String, insert: String = ""): String =
    mfun(s"CompTyp.${this.toString}", Seq(insert))


enum CompStatus(val id: Int, val label: String):
  case UNKN   extends CompStatus(97,  "UNKN")
  case Status extends CompStatus(98,  "Status")
  case CFG    extends CompStatus(99,  "CFG")
  case RUN    extends CompStatus(100, "RUN")
  case FIN    extends CompStatus(103, "FIN")

  case READY  extends CompStatus(  0, "READY")
  case VRAUS  extends CompStatus(  1, "VRAUS")   // Auslosung der Vorrunde
  case VREIN  extends CompStatus(  2, "VREIN")   // Auslosung erfolgt, Eingabe der Ergebnisse
  case VRFIN  extends CompStatus(  3, "VRFIN")   // Vorrunde beendet, Auslosung ZR oder ER kann erfolgen

  case ZRAUS  extends CompStatus(  4, "ZRAUS")   // Auslosung der Zwischenrunde
  case ZREIN  extends CompStatus(  5, "ZREIN")   // Auslosung erfolgt, Eingabe der Ergebnisse
  case ZRFIN  extends CompStatus(  6, "ZRFIN")   // Zwischenrunde beendet, Auslosung ER kann erfolgen

  case ERAUS  extends CompStatus(  7, "ERAUS")   // Auslosung der Endrunde
  case EREIN  extends CompStatus(  8, "EREIN")   // Auslosung erfolgt, Eingabe der Ergebnisse
  case ERFIN  extends CompStatus(  9, "ERFIN")   // Endrunde beendet ...

  def msgCode: String = s"CompStatus.${this.toString}"
  def equalsTo(compareWith: CompStatus*): Boolean = compareWith.contains(this)


object CompTyp:
  def fromId(id: Int): CompTyp = CompTyp.values.find(_.id == id).getOrElse(CompTyp.UNKN)
end CompTyp  

object CompStatus:
  def fromId(id: Int): CompStatus = CompStatus.values.find(_.id == id).getOrElse(CompStatus.UNKN)
end CompStatus
