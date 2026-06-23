package shared.model

import shared.basic.Pickle.*
import shared.basic.Pickle.{ReadWriter => RW, macroRW}

import scala.collection.mutable.{ ArrayBuffer, HashMap, Map }
import shared.basic.*

opaque type SNO = String

object SNO:

  // ----------------------------
  // Konstruktoren
  // ----------------------------
  def fromString(str: String): SNO =  str 
  def single(id: PlayerId): SNO = s"[${id.value}]"
  def double(id1: PlayerId, id2: PlayerId): SNO =
    if (id1.value < id2.value) then
      s"[${id1.value},${id2.value}]"
    else
      s"[${id2.value},${id1.value}]"
  def bye(seed: Int): SNO = s"[${-1 - seed}]"
  val nn: SNO = "[0]"

  // By using StringReader and StringWriter directly, 
  // we bypass the macro's search for an implicit ReadWriter[String].
  given rw: ReadWriter[SNO] =
    ReadWriter.join(StringReader, StringWriter).bimap[SNO](
      identity, // write: SNO is String here
      identity  // read: String is SNO here
    )    


  // ----------------------------
  // Extensions
  // ----------------------------    
  extension (s: SNO)

    private def content: String =  s.substring(1, s.length - 1) // remove the surrounding brackets
    private def parts: Array[String] = content.split(",")
    
    def isSingle: Boolean = parts.length == 1 && !content.startsWith("-") && content != "0"
    def isDouble: Boolean = parts.length == 2
    def startId: String =  String.format("%03d", parts(0).toInt)
    def isBye: Boolean = parts.length == 1 && content.startsWith("-")
    def isNN: Boolean = s == "[0]"  

    /**
     * Generates a safe suffix for HTML element IDs by stripping brackets
     * and replacing commas/hyphens with safe characters.
     */
    def idSaveSuffix: String = 
      content.replaceAll(",", "_").replaceAll("-", "m")

    def singleId: PlayerId =
      val p = parts
      if p.length == 1 then
        PlayerId(p(0).toInt)
      else 
        PlayerId(0)

    def doubleId: (PlayerId, PlayerId) =
      val p = parts
      if p.length == 2 then
        (PlayerId(p(0).toInt), PlayerId(p(1).toInt))
      else 
        (PlayerId(0), PlayerId(0))        


    def singleIdOpt: Option[PlayerId] =
      val p = parts
      if p.length == 1 then
        val v = p(0).toInt
        if v > 0 then Some(PlayerId(v))
        else None
      else None

    def doubleIdsOpt: Option[(PlayerId, PlayerId)] =
      val p = parts
      if p.length == 2 then
        Some(
          PlayerId(p(0).toInt),
          PlayerId(p(1).toInt)
        )
      else None

    def byeSeedOpt: Option[Int] =
      val p = parts
      if p.length == 1 then
        val v = p(0).toLong
        if v < 0 then Some((-1 - v).toInt)
        else None
      else None    



  // ----------------------------
  // Konvertierung
  // ----------------------------
  // def fromPantId(p: PantId): SNO =
  //   p match
  //     case PantId.Single(id)      => single(id)
  //     case PantId.Double(a,b)     => double(a,b)
  //     case PantId.Bye(seed)       => bye(seed)
  //     case PantId.NN              => nn

  // def toPantId(e: SNO): PantId =
  //   val arr = read[Array[Long]](e)
  //   arr.length match
  //     case 1 =>
  //       val v = arr(0)
  //       if v == 0 then PantId.NN
  //       else if v < 0 then PantId.Bye((-1 - v).toInt)
  //       else PantId.Single(v)
  //     case 2 =>
  //       PantId.Double(arr(0), arr(1))
  //     case _ =>
  //       throw new Exception("Invalid encoded PantId")

  // // ----------------------------
  // // Extension
  // // ----------------------------
  // extension (e: SNO)
  //   def value: String = e

 

enum PantStatus(val id: Int, val label: String):

  // participant status
  case UNKN extends PantStatus(-99, "UNKN")
  case RJEC extends PantStatus(-3,  "RJEC")
  case WAIT extends PantStatus(-2,  "WAIT")
  case PEND extends PantStatus(-1,  "PEND")
  case REGI extends PantStatus(0,   "REGI")
  case REDY extends PantStatus(1,   "REDY")
  case PLAY extends PantStatus(2,   "PLAY")
  case FINI extends PantStatus(3,   "FINI")

  // ---- helper methods ----
  def code: String = label
  def msgCode: String = s"PantStatus.$label"
  def equalsTo(compareWith: PantStatus*): Boolean = compareWith.contains(this)

object PantStatus:
  given ReadWriter[PantStatus] =
    readwriter[String].bimap[PantStatus](
      _.toString,
      s => PantStatus.valueOf(s)
    )


/**
 * Pant = Participant Entry inside a competition.
 *
 * Represents a player participating in a competition tournament
 * (single, double, bye, or NN).
 *
 * Immutable and type-safe.
 */
case class Pant(
  id:             SNO,
  name:           String = "",
  club:           String = "",
  rating:         Int = 0,
  var birthYear:  String = "",
  var qInfo:      String = "",
  var place:      (Int, Int) = (0, 0),
  var status:     PantStatus = PantStatus.UNKN,
  var active:     Boolean = false,
  var ident:      String = ""
) derives ReadWriter:
  def getEffRating(value: Int=0) = if (rating == 0) value else rating
  def getName(byeText: String): String = if (id.isBye) byeText else name
  def getRatingInfo: String = if (id.isBye) "" else rating.toString
