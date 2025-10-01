package shared.model

import shared.Routines._
import upickle.default.{ReadWriter => RW, macroRW}

case class SNO(value: String):
  def getSingleId: Long = value.toLongOption.getOrElse(0L)
  def getDoubleId: (Long, Long) = 
    val ids = getMDLongArr(value)
    if (ids.length != 2) (0L,0L) else (ids(0), ids(1))
  def isBye = SNO.isBye(value)
  def isNN  = SNO.isNN(value)    


object SNO:
  implicit def rw: RW[SNO] = macroRW
  
  val BYE = "99999" 
  val NN  = "99000" 

  def isBye(value: String) = 
    val intVal = value.toIntOption.getOrElse(0)
    (intVal >= 99500 & intVal <= 99999)

  def isNN(value: String)  = (value == "" | value == SNO.NN )

  def plId(inValue: String): Long = 
    val value = getMDLongArrDef(inValue)
    if (value(0) >= 99500) 0L else value(0)