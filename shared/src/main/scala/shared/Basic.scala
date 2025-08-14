package shared

import scala.concurrent.Future
import scala.reflect.ClassTag
import shared.model.AppError
import java.util.Random

import upickle.default.{ReadWriter => RW, macroRW}
import upickle.default._

type EiErr[T] = Either[Error, T]
type FuEiErr[T] = Future[Either[AppError, T]]

/** generate random string with length
  * 
  */
def randomString(length: Int): String =
  val rand = new Random()
  val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray
  val sb = new StringBuilder

  for (_ <- 0 until length)
    val randomIndex = rand.nextInt(chars.length)
    sb.append(chars(randomIndex))
  sb.toString()   

def ite[T](cond: Boolean, valA: => T, valB: => T): T = if (cond) valA else valB

inline def parseJson[T](x: String)(using r: Reader[T], ct: ClassTag[T]): Either[AppError, T] = {
  import scala.util.Try
  if      (x == "")                            Left(AppError("err00006.parseJson", "empty string"))
  if      (ct.runtimeClass == classOf[String]) Right(x.asInstanceOf[T])
  else if (ct.runtimeClass == classOf[Int])    {
    Try(x.toInt).toOption match {
      case Some(value) => Right(value.asInstanceOf[T])
      case None        => Left(AppError("err00006.parseJson", "invalid integer"))  
    }
  }  
  else { 
    try Right(read[T](x))
    catch { case e: Throwable => Left(AppError("err00006.parseJson", e.getMessage, x.take(10))) }
  }  
}

inline def toJson[T](x: T)(using w: Writer[T]): String = write[T](x)

extension (str: String)
  def toTuple(sep: String=":"): Tuple2[String, String] = 
    val x = str.split(sep)
    if x.length != 2 then ("","") else (x(0),x(1))

  def toError(func: String): AppError = 
    try read[AppError](str)
    catch { case e: Throwable => AppError("err00006.parseJson", e.getMessage, str.take(10)).add(func) }

  def to[T]()(using r: Reader[T]): Either[AppError, T] = {
    try if str == "" then Left(AppError("err00006.parseJson", "empty string")) else Right(read[T](str))
    catch { case e: Throwable => Left(AppError("err00006.parseJson", e.getMessage, str.take(10))) }
  }    

  