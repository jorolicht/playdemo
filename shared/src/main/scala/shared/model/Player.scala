package shared.model

import upickle.default.*
import shared.basic.AppError

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
  id: Long = 0,
  clubId: Long = 0,
  clubName: String = "",
  firstname: String,
  lastname: String,
  birthyear: Option[Int] = None,
  email: Option[String] = None,
  sex: Sex = Sex.Unknown,
  meta: PlayerMeta = PlayerMeta()
) derives ReadWriter:

  /** JSON encoding */
  def encode: String = write(this)

  /** Display name: "Lastname, Firstname" */
  def displayName: String =
    (lastname.trim, firstname.trim) match
      case ("", f) => f
      case (l, "") => l
      case (l, f)  => s"$l, $f"

  /** Display name with optional ID */
  def formattedName(showId: Boolean = false): String =
    if showId && id != 0 then s"$displayName [$id%03d]"
    else displayName

  /** Average rating for doubles */
  def doubleRating(other: Player): Option[Int] =
    (meta.ttr, other.meta.ttr) match
      case (Some(a), Some(b)) => Some((a + b) / 2)
      case _                  => None

  /** Birthyear string for display */
  def birthyearString: String =
    birthyear.map(_.toString).getOrElse("")

  /** Club display */
  def formattedClub(showId: Boolean = false): String =
    if showId && clubId != 0 then s"$clubName [$clubId%03d]"
    else clubName


object Player:

  /**
   * Safe JSON decoding
   */
  def decode(json: String): Either[AppError, Player] =
    try Right(read[Player](json))
    catch
      case e: Throwable =>
        Left(AppError("err.decode.player", json.take(30), e.getMessage))

  /**
   * Parse from simple CSV format:
   * lastname,firstname,club,ttr,birthyear,sex,email
   */
  def fromCSV(csv: String): Either[AppError, Player] =
    val parts = csv.split(',').map(_.trim)

    def toIntSafe(s: String): Option[Int] =
      s.toIntOption

    parts.length match
      case 7 | 6 | 5 | 4 | 3 =>
        val lastname  = parts.lift(0).getOrElse("")
        val firstname = parts.lift(1).getOrElse("")
        val club      = parts.lift(2).getOrElse("")
        val ttr       = parts.lift(3).flatMap(toIntSafe)
        val birthyear = parts.lift(4).flatMap(toIntSafe)
        val sex       = parts.lift(5).flatMap(toIntSafe).map(Sex.fromInt).getOrElse(Sex.Unknown)
        val email     = parts.lift(6).filter(_.nonEmpty)

        if lastname.isEmpty then
          Left(AppError("err.csv.player.empty.lastname"))
        else
          Right(
            Player(
              clubName = club,
              firstname = firstname,
              lastname = lastname,
              birthyear = birthyear,
              email = email,
              sex = sex,
              meta = PlayerMeta(ttr = ttr)
            )
          )

      case _ =>
        Left(AppError("err.csv.player.invalid.format"))