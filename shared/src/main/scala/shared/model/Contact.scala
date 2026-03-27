package shared.model

import shared.basic.Pickle.*
import shared.basic.AppError
import scala.util.control.NonFatal
import scala.deriving.Mirror
import cats.syntax.either.*

/**
 * Represents a contact person.
 *
 * Supports:
 *  - JSON encoding via uPickle
 *  - Legacy metadata encoding using "·" separator
 */
case class Contact(
  lastname:  String,
  firstname: String,
  phone:     String,
  email:     String
):

  def encode(): String = write(Tuple.fromProductTyped(this))

  /**
   * Returns formatted name.
   *
   * @param format formatting style
   */
  def name(format: Contact.NameFormat = Contact.NameFormat.FirstLast): String =
    val fn = firstname.trim
    val ln = lastname.trim

    format match
      case Contact.NameFormat.FirstLast =>
        if fn.nonEmpty && ln.nonEmpty then s"$fn $ln"
        else fn + ln

      case Contact.NameFormat.LastFirst =>
        if fn.nonEmpty && ln.nonEmpty then s"$ln, $fn"
        else fn + ln


object Contact:
  given ReadWriter[Contact] = macroRW

  /**
   * Name formatting styles.
   */
  enum NameFormat:
    case FirstLast   // "John Doe"
    case LastFirst   // "Doe, John"


  def decode(value: String): Either[AppError, Contact] =
    // Summon the Mirror to access the Case Class's structural metadata
    val m = summon[Mirror.ProductOf[Contact]]
    
    Either
      .catchNonFatal {
        // 1. Read the JSON string specifically as a 4-element Tuple of Strings
        val tuple = read[(String, String, String, String)](value)
        
        // 2. Use the Mirror to "plug" the Tuple values into the Address constructor
        m.fromProduct(tuple)
      }
      // If the JSON structure is invalid or types mismatch, map the exception to a domain error
      .leftMap(_ => AppError("TODOerr0043.decode.Contact", value))
    