package shared.model

import shared.basic.Pickle.*
import shared.basic.AppError
import shared.basic.*
import scala.util.control.NonFatal
import scala.deriving.Mirror
import cats.syntax.either.*

/**
 * Represents an Address data model.
 * Note: This class uses also a "Tuple-based" serialization strategy. 
 * compact JSON arrays like ["Description", "Country", "Zip", "City", "Street"].
 */
case class Address(
  description: String,
  country:     String,
  zip:         String,
  city:        String,
  street:      String
):
  /**
   * Serializes the Case Class instance into a compact JSON string.
   * Tuple.fromProductTyped(this) transforms the object into a typed Tuple.
   * uPickle then writes that Tuple as a JSON Array.
   */
  def encode(): String = write(Tuple.fromProductTyped(this))

object Address:
  /** * Provides a standard uPickle ReadWriter. 
   * Useful for internal framework operations or standard Object-based JSON.
   */
  given ReadWriter[Address] = macroRW

  /**
   * Decodes a JSON String (formatted as an Array) back into an Address instance.
   * @param value The raw JSON string.
   * @return Either a custom AppError or the successfully decoded Address.
   */
  def decode(value: String): Either[AppError, Address] =
    // Summon the Mirror to access the Case Class's structural metadata
    val m = summon[Mirror.ProductOf[Address]]
    
    Either
      .catchNonFatal {
        // 1. Read the JSON string specifically as a 5-element Tuple of Strings
        val tuple = read[(String, String, String, String, String)](value)
        
        // 2. Use the Mirror to "plug" the Tuple values into the Address constructor
        m.fromProduct(tuple)
      }
      // If the JSON structure is invalid or types mismatch, map the exception to a domain error
      .leftMap(_ => AppError("err0043.decode.Address", value))