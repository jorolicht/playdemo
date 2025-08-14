package shared.model

import scala.concurrent.Future

import upickle.default.{ReadWriter => RW, macroRW}
import upickle.default._

case class AppError(msgCode:String, var in1:String="", var in2:String="", var callStack: String=""):
  def equal2Code(code: String): Boolean = { this.msgCode == code }
  def is(code: String): Boolean = { this.msgCode == code }
  def add(func: String): AppError = { callStack = s"${func}:${callStack}"; this} 
  def isDummy  = (msgCode == "")

object AppError: 
  implicit val rw: RW[AppError] = macroRW
  def apply[T](msgCode: String, in: T) = new AppError(msgCode, in.toString(), "", "")
  def apply[T,U](msgCode: String, in1: T, in2: U) = new AppError(msgCode, in1.toString(), in2.toString(), "")
  def apply[T,U](msgCode: String, in1: T, in2: U, callStack: String) = new AppError(msgCode, in1.toString(), in2.toString(), callStack)
  def dummy = AppError("","","","")


def parseError(in: String, func: String): AppError =
  try read[AppError](in)
  catch { case e: Throwable => AppError("err00006.parseJson", e.getMessage, in.take(100)).add(func) }


