package shared.model

import upickle.default.*
import shared.basic.AppError
import shared.basic.*
import scala.util.control.NonFatal
import scala.collection.mutable.{ ArrayBuffer, Map }
import shared.model.ClubId.*


val abbreviationMap = Map(
  "hsv" -> "hamburger sv",
  "fcb" -> "bayern munchen",
  "bvb" -> "borussia dortmund"
)

opaque type ClubId = Int

object ClubId:
  /** create a ClubId */
  def apply(id: Int): ClubId = id

  /** extract underlying Int */
  def value(id: ClubId): Int = id

  /** extension helpers */
  extension (id: ClubId)
    def toInt: Int = id

  // We use ReadWriter.join(IntReader, IntWriter) to avoid 
  // the 'readwriter[Int]' macro search entirely.
  given rw: ReadWriter[ClubId] = 
    ReadWriter.join(IntReader, IntWriter).bimap(
      id => id, // Inside here, ClubId is seen as Int
      value => ClubId(value)
    )  


       

def jaroWinkler(s1: String, s2: String): Double = {
  if (s1 == s2) return 1.0
  if (s1.isEmpty || s2.isEmpty) return 0.0

  val matchDistance = (Math.max(s1.length, s2.length) / 2) - 1
  val s1Matches = Array.fill[Boolean](s1.length)(false)
  val s2Matches = Array.fill[Boolean](s2.length)(false)

  var matches = 0
  var transpositions = 0

  for (i <- s1.indices) {
    val start = Math.max(0, i - matchDistance)
    val end = Math.min(i + matchDistance + 1, s2.length)

    var j = start
    var found = false
    while (j < end && !found) {
      if (!s2Matches(j) && s1(i) == s2(j)) {
        s1Matches(i) = true
        s2Matches(j) = true
        matches += 1
        found = true
      }
      j += 1
    }
  }

  if (matches == 0) return 0.0

  var k = 0
  for (i <- s1.indices if s1Matches(i)) {
    while (!s2Matches(k)) k += 1
    if (s1(i) != s2(k)) transpositions += 1
    k += 1
  }

  val m = matches.toDouble
  val jaro =
    (m / s1.length +
      m / s2.length +
      (m - transpositions / 2.0) / m) / 3.0

  val prefixLength = s1.zip(s2).takeWhile { case (a, b) => a == b }.length min 4
  jaro + (prefixLength * 0.1 * (1 - jaro))
}
  

/**
 * Represents a club of a player.
 *
 * Stores:
 *  - internal id
 *  - club name
 *  - optional metadata (legacy "·" separated format)
 */

case class Club(
  id: ClubId,
  name: String,
  normalizedName: String,
  ctt: Option[ClubCTT] = None,
  active: Boolean = true
) derives ReadWriter:


  /**
   * Returns formatted name.
   */
  def formatted(format: Club.NameFormat = Club.NameFormat.Plain): String =
    format match
      case Club.NameFormat.Plain =>
        name
      case Club.NameFormat.WithId =>
        if id.toInt != 0 then f"$name [$id%03d]" else name


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


  def normalize(name: String): String =

    val lower = name.toLowerCase
      .replace("ü", "ue")
      .replace("ö", "oe")
      .replace("ä", "ae")
      .replace("ß", "ss")

    val expanded =
      abbreviationMap.foldLeft(lower) {
        case (acc, (abbr, full)) =>
          acc.replaceAll(s"\\b$abbr\\b", full)
      }

    val cleaned = expanded
      .replaceAll("[^a-z0-9 ]", " ")
      .replaceAll("\\b(fc|ev|e v|verein|club)\\b", "")
      .replaceAll("\\b19\\d{2}\\b", "")
      .replaceAll("\\b1\\b", "")
      .replaceAll("\\s+", " ")
      .trim

    // Tokens sortieren → Wortreihenfolge egal
    cleaned.split(" ").filter(_.nonEmpty).sorted.mkString(" ")


  def findSimilar(newClub: String, clubs: ArrayBuffer[Club], threshold: Double = 0.90): Option[(ClubId, Double)] = 
    clubs
      .map { existing =>
        val score = jaroWinkler(normalize(newClub), existing.normalizedName)
        (existing.id, score)
      }
      .maxByOption(_._2)
      .filter(_._2 >= threshold)
  


object ClubDB:
  val clubs: ArrayBuffer[Club] = ArrayBuffer()
  var timestamp: Long = 0

  private def idx(id: ClubId): Int = id.toInt - 1
  private def validIdx(i: Int): Boolean = i >= 0 && i < clubs.length
  private def nextId(): ClubId = ClubId(clubs.length + 1)


  def add(name: String): Either[AppError, Club] =
    try
      val normalized = Club.normalize(name)
      Club.findSimilar(name, clubs) match

        case Some((existingId, _)) =>
          val i = idx(existingId)

          if !validIdx(i) then
            Left(AppError("club.index.corrupt"))
          else
            val existing = clubs(i)

            if !existing.active then
              val updated = existing.copy(active = true)
              clubs.update(i, updated)
              timestamp = System.currentTimeMillis()

            Right(clubs(i))

        case None =>
          val id = nextId()

          val club =
            Club(
              id = id,
              name = name.trim,
              normalizedName = normalized,
              active = true
            )

          clubs += club
          timestamp = System.currentTimeMillis()
          Right(club)

    catch
      case NonFatal(e) =>
        Left(AppError(s"club.add.failed: ${e.getMessage}"))  

  def removeClub(id: ClubId): Either[AppError, Club] =
    val i = idx(id)

    if !validIdx(i) then
      Left(AppError("club.notFound"))
    else
      val club = clubs(i)

      if !club.active then
        Right(club)
      else
        val updated = club.copy(active = false)
        clubs.update(i, updated)
        timestamp = System.currentTimeMillis()
        Right(updated)


  def merge(
    sourceId: ClubId,
    targetId: ClubId
  ): Either[String, Club] =

    if sourceId == targetId then
      return Left("Source und Target sind identisch.")

    val sourceIdx = idx(sourceId)
    val targetIdx = idx(targetId)

    if !validIdx(sourceIdx) || !validIdx(targetIdx) then
      return Left("Source oder Target Club existiert nicht.")

    val source = clubs(sourceIdx)
    val target = clubs(targetIdx)

    if !source.active then
      return Left(s"Source-Club '${source.name}' ist bereits deaktiviert.")

    // Spieler umhängen
    PlayerDB.players.indices.foreach { i =>
      if PlayerDB.players(i).clubId == sourceId.toInt then
        PlayerDB.players.update(i, PlayerDB.players(i).copy(clubId = targetId.toInt))
    }

    // CTT zusammenführen
    val mergedCtt = target.ctt.orElse(source.ctt)
    val updatedTarget = target.copy(ctt = mergedCtt)

    clubs.update(targetIdx, updatedTarget)

    // Source deaktivieren
    clubs.update(sourceIdx, source.copy(active = false))

    Right(updatedTarget)

