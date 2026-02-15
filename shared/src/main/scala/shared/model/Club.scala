package shared.model

import upickle.default.*
import shared.basic.AppError
import shared.basic.*
import scala.util.control.NonFatal

/**
 * Represents a club of a player.
 *
 * Stores:
 *  - internal id
 *  - club name
 *  - optional metadata (legacy "·" separated format)
 *
 * Metadata indices:
 *   0 -> club number
 *   1 -> federation nickname
 */

case class Club(
  id:      Long,
  name:    String,
  ctt:     Option[ClubCTT] = None
) derives ReadWriter:

  /** Hash based only on club name */
  def hash: Int =
    name.hashCode

  /**
   * Returns formatted name.
   */
  def formatted(format: Club.NameFormat = Club.NameFormat.Plain): String =
    format match
      case Club.NameFormat.Plain =>
        name
      case Club.NameFormat.WithId =>
        if id != 0 then f"$name [$id%03d]" else name


case class ClubCTT(
  clubNr: Option[String] = None,
  clubFedNick: Option[String] = None
) derives ReadWriter


object Club:
  /**
   * Name formatting styles.
   */
  enum NameFormat:
    case Plain
    case WithId

  /**
   * Parses a formatted club name.
   *
   * Accepts:
   *   "MyClub"
   *   "MyClub [001]"
   */
  def validateName(input: String): Either[AppError, (String, Long)] =
    val trimmed = input.trim

    if trimmed.isEmpty then
      Right("" -> 0L)
    else
      val PatternWithId = raw"""(.+)\s\[(\d{3})\]""".r
      val PatternPlain = raw"""[^,;:$$=?+*"]+""".r

      trimmed match
        case PatternWithId(name, id) =>
          Right(name.trim -> id.toLong)

        case PatternPlain() =>
          Right(trimmed -> 0L)

        case _ =>
          Left(AppError("err0161.Club.parseName"))

  /**
   * Decode sequence of clubs from JSON.
   */
  def decodeSeq(json: String): Either[AppError, Seq[Club]] =
    try
      Right(read[Seq[Club]](json))
    catch
      case NonFatal(_) =>
        Left(AppError("err0056.decode.Clubs", json.take(20), "", "Club.decodeSeq"))

