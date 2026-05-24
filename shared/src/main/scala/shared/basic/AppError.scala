package shared.basic

import scala.concurrent.Future
import ujson.*

import shared.basic.Pickle.{ReadWriter => RW, macroRW, *}

case class AppError(msgCode:String, var in1:String="", var in2:String="", var callStack: String=""):
  def equal2Code(code: String): Boolean = { this.msgCode == code }
  def is(code: String): Boolean = { this.msgCode == code }
  def add(func: String): AppError = { callStack = s"${func}:${callStack}"; this} 
  def isDummy  = (msgCode == "")
  def msg = s"AppError: ${msgCode} in1:${in1} in2:${in2} callStack:${callStack}"

object AppError: 
  implicit val rw: RW[AppError] = macroRW
  def apply[T](msgCode: String, in: T) = new AppError(msgCode, in.toString(), "", "")
  def apply[T,U](msgCode: String, in1: T, in2: U) = new AppError(msgCode, in1.toString(), in2.toString(), "")
  def apply[T,U](msgCode: String, in1: T, in2: U, callStack: String) = new AppError(msgCode, in1.toString(), in2.toString(), callStack)
  def dummy = AppError("","","","")



def parseError(in: String, func: String): AppError =
  try 
    val json = ujson.read(in)
    if (json.obj.contains("msgCode")) {
      Pickle.read[AppError](in)
    } else if (json.obj.contains("code") && json.obj.contains("message")) {
      // Standard WordPress Error Format
      val code = json.obj("code").str
      val msg = json.obj("message").str
      AppError(code, msg).add(func)
    } else {
      AppError("err00006.parseJson", "Unexpected error format", in.take(100)).add(func)
    }
  catch { 
    case e: Throwable => AppError("err00006.parseJson", e.getMessage, in.take(100)).add(func) 
  }




