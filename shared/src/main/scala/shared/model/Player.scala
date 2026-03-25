package shared.model

import upickle.default.*
import scala.collection.mutable.{ ArrayBuffer, Map }
import shared.basic.AppError
import shared.model.PlayerId.*


/**
 * Sex ADT (replaces Scala 2 Enumeration)
 * Gives exhaustiveness checking and type safety.
 */
enum Sex derives ReadWriter:
  case Unknown
  case Female
  case Male

object Sex:
  def fromInt(i: Int): Sex =
    i match
      case 1 => Female
      case 2 => Male
      case _ => Unknown


/**
 * Structured metadata instead of encoded String.
 * Replaces fragile index-based metadata handling.
 */
case class PlayerMeta(
  internalNr: Option[String] = None,
  licenceNr: Option[String] = None,
  clubNr: Option[String] = None,
  clubFedNick: Option[String] = None,
  ttr: Option[Int] = None,
  ttrMatchCnt: Option[Int] = None,
  nationality: Option[String] = None,
  foreignerEqState: Option[String] = None,
  region: Option[String] = None,
  subRegion: Option[String] = None
) derives ReadWriter


opaque type PlayerId = Int

object PlayerId:
  def apply(value: Int): PlayerId = value

  // We use 'IntReader' and 'IntWriter' specifically to avoid 
  // the 'readwriter[Int]' macro search.
  given rw: ReadWriter[PlayerId] = 
    ReadWriter.join(IntReader, IntWriter).bimap(
      (id: PlayerId) => id.value,
      (value: Int) => PlayerId(value)
    )
  
  extension (id: PlayerId)
    def value: Int = id

    def idx: Int = id - 1

    def isValid(max: Int): Boolean =
      id >= 1 && id <= max    



/**
 * Immutable Player domain model.
 *
 * Design goals:
 * - immutable
 * - type safe
 * - serializable
 * - easy to validate
 */
case class Player(
  id: PlayerId = PlayerId(0),
  firstName: String,
  lastName: String,
  clubId: Int = 0,
  birthYear: Option[Int] = None,
  email: Option[String] = None,
  sex: Sex = Sex.Unknown,
  var active: Boolean = true,
  var merge: Option[PlayerId] = None,
  var meta: PlayerMeta = PlayerMeta()
) derives ReadWriter:

  def fullName: String =
    s"$firstName $lastName"

  /** Display name: "Lastname, Firstname" */
  def displayName: String =
    (lastName.trim, firstName.trim) match
      case ("", f) => f
      case (l, "") => l
      case (l, f)  => s"$l, $f"

  /** Display name with optional ID */
  def formattedName(showId: Boolean = false): String =
    if showId && id.value != 0 then s"$displayName [$id%03d]"
    else displayName

  /** Average rating for doubles */
  def doubleRating(other: Player): Option[Int] =
    (meta.ttr, other.meta.ttr) match
      case (Some(a), Some(b)) => Some((a + b) / 2)
      case _                  => None

  /** Birthyear string for display */
  def birthyearString: String =
    birthYear.map(_.toString).getOrElse("")


