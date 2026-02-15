package shared.model

import cats.syntax.either.*
import scala.util.control.NonFatal
import upickle.default.*
import shared.basic.*

/**
 * ADT (Abstract Data Type) representing a placement.
 * It can either be a single position or a range between two points.
 */
sealed trait Placement
case class PlacementPos(pos: Int) extends Placement
case class PlacementRange(from: Int, to: Int) extends Placement

// Standard uPickle ReadWriters for the individual case classes
object PlacementPos   { implicit val rw: ReadWriter[PlacementPos] = macroRW }
object PlacementRange { implicit val rw: ReadWriter[PlacementRange] = macroRW }

object Placement:

  /** Helper to create a consistent domain error for decoding failures */
  private def error(str: String) = 
    Left(AppError("TODOerr0059.decode.Placement", str, "", "Placement.decode"))

  /**
   * Custom encoder that bypasses JSON formatting for a compact string.
   * Single Pos: "5"
   * Range: "10·20" (uses the middle dot '·' as a separator)
   */
  def encode(place: Placement): String = place match
    case PlacementPos(pos)       => pos.toString
    case PlacementRange(from, to) => s"${from}·${to}"

  /**
   * Decodes the custom string format back into a Placement object.
   * Expects either a single integer or two integers separated by a specific character.
   */
  def decode(placeStr: String): Either[AppError, Placement] =
    // getMDIntArr is likely a helper that splits the string by '·' and parses Ints
    Either.catchNonFatal(getMDIntArr(placeStr))
      .flatMap {
        // Case: Single positive integer -> Position
        case Array(p) if p > 0 =>
          Right(PlacementPos(p))
        // Case: Two positive integers -> Range
        case Array(f, t) if f > 0 && t > 0 =>
          Right(PlacementRange(f, t))
        // Fallback: Array structure doesn't match business rules
        case _ => error(placeStr)
      }
      // Catch parsing exceptions (e.g., NumberFormatException) and return domain error
      .left.flatMap(_ => error(placeStr))