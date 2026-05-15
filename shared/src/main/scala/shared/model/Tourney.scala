package shared.model

import shared.basic.Pickle.*
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


case class Tourney(
  name:          String,          // Tournament name
  var organizer: String,          // Organizer name (club or registered program user)
  var startDate: Int,             // Format: yyyymmdd
  var endDate:   Int,
  var ident:     String,          // clickTT id
  typ:           TourneyTyp,
  var contact:   Option[Contact] = None,
  var address:   Option[Address] = None,
  var version:   Int = 0
):

  /**
   * Validates tournament data.
   */
  def check(): Either[AppError, Boolean] =
    val (y, _, _) = int2ymd(startDate)

    if name.length <= 3 then
      Left(AppError("TODOerr0179.Tourney.name", "", "", "Tourney.check"))
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


object Tourney:
  given ReadWriter[Tourney] = macroRW

  /**
   * Decodes a single Tourney from JSON.
   */
  def decode(json: String): Either[AppError, Tourney] =
    if json.isBlank then
      Left(AppError("TODOerr0062.decode.Tourney", "<empty input>", "", "Tourney.decode"))
    else
      try Right(read[Tourney](json))
      catch
        case NonFatal(_) =>
          Left(AppError("TODOerr0062.decode.Tourney", json.take(20), "", "Tourney.decode"))

  /**
   * Encodes a sequence of tournaments into JSON.
   */
  def encSeq(values: Seq[Tourney]): String =
    write(values)

  /**
   * Decodes a sequence of tournaments from JSON.
   */
  def decSeq(json: String): Either[AppError, Seq[Tourney]] =
    try Right(read[Seq[Tourney]](json))
    catch
      case NonFatal(_) =>
        Left(AppError("TODOerr0144.decode.Tourney", json.take(20), "", "Tourney.decSeq"))