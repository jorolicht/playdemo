package shared.model

import upickle.default.*
import shared.basic.*
import scala.util.control.NonFatal

/**
 * Represents a row in the tournament table.
 *
 * Contains:
 *  - unique identifier
 *  - tournament name
 *  - organizer
 *  - start and end date (format: yyyymmdd as Int)
 *  - clickTT id
 *  - tournament type
 *  - visibility flag (private/public)
 *  - optional contact and address
 */
enum TourneyTyp derives ReadWriter:
  case Unknown
  case TableTennis


case class TournBase(
  name:      String,
  organizer: String,          // Organizer name (club or registered program user)
  orgDir:    String,          // Normalized organizer name (used as directory)
  startDate: Int,             // Format: yyyymmdd
  endDate:   Int,
  ident:     String,          // clickTT id
  typ:       TourneyTyp,
  privat:    Boolean,         // Private tournaments visible only to registered users
  contact:   Option[Contact] = None,
  address:   Option[Address] = None,
  id:        Long = 0L        // Autoincrement database id
) derives ReadWriter:

  /**
   * Validates tournament data.
   */
  def check(): Either[AppError, Boolean] =
    val (y, _, _) = int2ymd(startDate)

    if name.length <= 3 then
      Left(AppError("TODOerr0179.Tourney.name", "", "", "TournBase.check"))
    else if startDate > endDate then
      Left(AppError("TODOerr0180.Tourney.edate"))
    else if y < 2022 then
      Left(AppError("TODOerr0181.Tourney.sdate"))
    else
      Right(true)

  /**
   * Returns formatted contact name in form:
   * "Lastname, Firstname"
   */
  def getContactName: String =
    contact match
      case Some(person) =>
        val lname = person.lastname
        val fname = person.firstname

        if lname.isBlank && fname.isBlank then
          s"$lname, $fname"
        else
          lname + fname
      case None =>
        ""

  def getStartDate(lang: String, fmt: Int = 0): String =
    int2date(startDate, lang, fmt)

  def getEndDate(lang: String, fmt: Int = 0): String =
    int2date(endDate, lang, fmt)


object TournBase:

  /**
   * Decodes a single TournBase from JSON.
   */
  def decode(json: String): Either[AppError, TournBase] =
    if json.isBlank then
      Left(AppError("TODOerr0062.decode.TournBase", "<empty input>", "", "TournBase.decode"))
    else
      try Right(read[TournBase](json))
      catch
        case NonFatal(_) =>
          Left(AppError("TODOerr0062.decode.TournBase", json.take(20), "", "TournBase.decode"))

  /**
   * Encodes a sequence of tournaments into JSON.
   */
  def encSeq(values: Seq[TournBase]): String =
    write(values)

  /**
   * Decodes a sequence of tournaments from JSON.
   */
  def decSeq(json: String): Either[AppError, Seq[TournBase]] =
    try Right(read[Seq[TournBase]](json))
    catch
      case NonFatal(_) =>
        Left(AppError("TODOerr0144.decode.TournBase", json.take(20), "", "TournBase.decSeq"))