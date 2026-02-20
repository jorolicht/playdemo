package shared.basic

import scala.concurrent.Future
import upickle.default.{ReadWriter => RW, macroRW}
import upickle.default.*


case class Return[A](value: A):
  def encode(using Writer[A]): String =
    write(value)

object Return:
  def decode[A](s: String)(using Reader[A]): Either[AppError, A] =
    try Right(read[A](s))
    catch
      case e: Exception => Left(AppError("TODO", e.getMessage))