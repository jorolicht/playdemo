package services

import shared.model.*
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.{Element, Node, NodeList}
import java.io.ByteArrayInputStream
import scala.util.Try

/**
 * ClickTTParser provides methods to parse ClickTT XML files into Scala objects (JVM version).
 */
object ClickTTParser:

  /**
   * Parses an XML string into a CttTournament object.
   */
  def parse(xmlString: String): Either[String, CttTournament] =
    try
      val factory = DocumentBuilderFactory.newInstance()
      
      // Fix for "DOCTYPE is disallowed" error
      // We allow DOCTYPE but disable external general/parameter entities and DTDs for security
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      factory.setXIncludeAware(false)
      factory.setExpandEntityReferences(false)

      val builder = factory.newDocumentBuilder()
      val is = new ByteArrayInputStream(xmlString.getBytes("UTF-8"))
      val doc = builder.parse(is)
      
      val tournamentNodes = doc.getElementsByTagName("tournament")
      if (tournamentNodes.getLength == 0) return Left("Root element <tournament> not found")
      
      val tournamentEl = tournamentNodes.item(0).asInstanceOf[Element]
      Right(parseTournament(tournamentEl))
    catch
      case e: Exception => 
        Left(s"Failed to parse XML: ${e.getMessage}")

  private def parseTournament(el: Element): CttTournament =
    CttTournament(
      name = el.getAttribute("name"),
      startDate = el.getAttribute("start-date"),
      endDate = el.getAttribute("end-date"),
      tournamentId = el.getAttribute("tournament-id"),
      winningSets = getAttrIntOpt(el, "winning-sets"),
      winningSetsText = getAttrOpt(el, "winning-sets-text"),
      multipleParticipationsSameDay = getAttrBoolOpt(el, "multiple-participations-same-day"),
      multipleParticipationsSameTime = getAttrBoolOpt(el, "multiple-participations-same-time"),
      tableCount = getAttrIntOpt(el, "table-count"),
      teamFormation = getAttrOpt(el, "team-formation"),
      locations = getChildren(el, "tournament-location").map(parseLocation),
      competitions = getChildren(el, "competition").map(parseCompetition)
    )

  private def parseLocation(el: Element): CttLocation =
    CttLocation(
      name = getAttrOpt(el, "name"),
      street = getAttrOpt(el, "street"),
      zipCode = getAttrOpt(el, "zip-code"),
      city = getAttrOpt(el, "city")
    )

  private def parseCompetition(el: Element): CttCompetition =
    val playersNodes = el.getElementsByTagName("players")
    val playersEl = if (playersNodes.getLength > 0) playersNodes.item(0).asInstanceOf[Element] else null
    
    val matchesNodes = el.getElementsByTagName("matches")
    val matchesEl = if (matchesNodes.getLength > 0) matchesNodes.item(0).asInstanceOf[Element] else null
    
    CttCompetition(
      ageGroup = el.getAttribute("age-group"),
      typ = el.getAttribute("type"),
      startDate = el.getAttribute("start-date"),
      ttrFrom = getAttrIntOpt(el, "ttr-from"),
      ttrTo = getAttrIntOpt(el, "ttr-to"),
      ttrRemarks = getAttrOpt(el, "ttr-remarks"),
      entryFee = getAttrOpt(el, "entry-fee"),
      ageFrom = getAttrIntOpt(el, "age-from"),
      ageTo = getAttrIntOpt(el, "age-to"),
      sex = getAttrIntOpt(el, "sex"),
      preliminaryRoundPlaymode = getAttrOpt(el, "preliminary-round-playmode"),
      finalRoundPlaymode = getAttrOpt(el, "final-round-playmode"),
      maxPersons = getAttrIntOpt(el, "max-persons"),
      manualFinalRankings = el.getAttribute("manual-final-rankings") == "1",
      players = if (playersEl != null) getChildren(playersEl, "player").map(parsePlayer) else Nil,
      matches = if (matchesEl != null) Some(getChildren(matchesEl, "match").map(parseMatch)) else None
    )

  private def parsePlayer(el: Element): CttPlayer =
    CttPlayer(
      typ = el.getAttribute("type"),
      id = el.getAttribute("id"),
      teamName = getAttrOpt(el, "team-name"),
      teamNr = getAttrOpt(el, "team-nr"),
      placement = getAttrOpt(el, "placement"),
      persons = getChildren(el, "person").map(parsePerson)
    )

  private def parsePerson(el: Element): CttPerson =
    CttPerson(
      firstname = el.getAttribute("firstname"),
      lastname = el.getAttribute("lastname"),
      birthyear = el.getAttribute("birthyear"),
      internalNr = el.getAttribute("internal-nr"),
      licenceNr = el.getAttribute("licence-nr"),
      sex = Try(el.getAttribute("sex").toInt).getOrElse(0),
      clubName = getAttrOpt(el, "club-name"),
      clubNr = getAttrOpt(el, "club-nr"),
      clubFederationNickname = getAttrOpt(el, "club-federation-nickname"),
      ttr = getAttrIntOpt(el, "ttr"),
      ttrMatchCount = getAttrIntOpt(el, "ttr-match-count"),
      nationality = getAttrOpt(el, "nationality"),
      foreignerEqState = getAttrOpt(el, "foreigner-eq-state"),
      region = getAttrOpt(el, "region"),
      subRegion = getAttrOpt(el, "sub-region")
    )

  private def parseMatch(el: Element): CttMatch =
    CttMatch(
      nr = getAttrOpt(el, "nr"),
      group = getAttrOpt(el, "group"),
      scheduled = getAttrOpt(el, "scheduled"),
      playerA = el.getAttribute("player-a"),
      playerB = el.getAttribute("player-b"),
      state = getAttrOpt(el, "state"),
      setA = (1 to 7).flatMap(i => getAttrIntOpt(el, s"set-a-$i")),
      setB = (1 to 7).flatMap(i => getAttrIntOpt(el, s"set-b-$i")),
      setsA = Try(el.getAttribute("sets-a").toInt).getOrElse(0),
      setsB = Try(el.getAttribute("sets-b").toInt).getOrElse(0),
      matchesA = Try(el.getAttribute("matches-a").toInt).getOrElse(0),
      matchesB = Try(el.getAttribute("matches-b").toInt).getOrElse(0),
      gamesA = Try(el.getAttribute("games-a").toInt).getOrElse(0),
      gamesB = Try(el.getAttribute("games-b").toInt).getOrElse(0)
    )

  // --- Helper Methods ---

  private def getAttrOpt(el: Element, name: String): Option[String] =
    val v = el.getAttribute(name)
    if (v == null || v.isEmpty) None else Some(v)

  private def getAttrIntOpt(el: Element, name: String): Option[Int] =
    getAttrOpt(el, name).flatMap(s => Try(s.toInt).toOption)

  private def getAttrBoolOpt(el: Element, name: String): Option[Boolean] =
    getAttrOpt(el, name).map(_.toLowerCase == "true")

  private def getChildren(el: Element, tagName: String): Seq[Element] =
    val nodes = el.getChildNodes
    val result = for (i <- 0 until nodes.getLength) yield nodes.item(i)
    result.collect { case e: Element if e.getTagName == tagName => e }
