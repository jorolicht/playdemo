package shared.model

import upickle.default.{ReadWriter => RW, macroRW}

case class Participant(
  var sno:       String,         // start number(s) concatenated string of player identifieres  
  val name:      String,                     
  val club:      String, 
  val rating:    Int,            // eg. ttr for table tennis
  var qInfo:     String,         // position after finishing the round (group or ko) 
  var place:     (Int,Int)       // position after finishing the round (group or ko)
):
  def setPlace(p: (Int,Int)) = place = p
  def getPlace  = place._1.toString
  def getRatingInfo = if (rating == 0) "" else rating.toString 
  def getEffRating(value: Int=0) = if (rating == 0) value else rating
  def toCSV = s"${sno}·${name}·${club}·${rating}·${qInfo}·${qInfo}·${place._1}·${place._2}"

  // getName returns name for all types of participants
  def getName(byeName: String="") = if (SNO.isBye(sno)) byeName else name


object Participant:
  implicit def rw: RW[Participant] = macroRW
  def bye(name: String="bye") =  Participant(SNO.BYE, name, "", 0, "", (0, 0))
  def genSNO(id1: Long, id2: Long): String = {
    (id1, id2) match {
      case (x,y) if (x<=0 | y<=0) => ""
      case (x,y) if (x==y)        => x.toString
      case (x,y) if (x>y)         => y.toString + "·" + x.toString
      case (x,y)                  => x.toString + "·" + y.toString 
    }
  }
  def fromCSV(value: String): Either[AppError, Participant] = {
    try   { val x = value.split('·'); Right(Participant(x(0), x(1), x(2), x(3).toInt, x(4), (x(5).toInt, x(6).toInt) )) }
    catch {  case _: Throwable =>  Left(AppError("err0238.decode.Pant", value))}
  } 
