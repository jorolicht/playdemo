package shared.model

import shared.basic.Pickle.*

/**
 * ClickTT XML Data Models based on nuLiga TournamentPortal.dtd
 */

case class CttTournament(
  name: String,
  startDate: String,
  endDate: String,
  tournamentId: String,
  winningSets: Option[Int] = None,
  winningSetsText: Option[String] = None,
  multipleParticipationsSameDay: Option[Boolean] = None,
  multipleParticipationsSameTime: Option[Boolean] = None,
  tableCount: Option[Int] = None,
  teamFormation: Option[String] = None,
  locations: Seq[CttLocation] = Nil,
  competitions: Seq[CttCompetition] = Nil
) derives ReadWriter

case class CttLocation(
  name: Option[String] = None,
  street: Option[String] = None,
  zipCode: Option[String] = None,
  city: Option[String] = None
) derives ReadWriter

case class CttCompetition(
  ageGroup: String,
  typ: String,
  startDate: String,
  ttrFrom: Option[Int] = None,
  ttrTo: Option[Int] = None,
  ttrRemarks: Option[String] = None,
  entryFee: Option[String] = None,
  ageFrom: Option[Int] = None,
  ageTo: Option[Int] = None,
  sex: Option[Int] = None,
  preliminaryRoundPlaymode: Option[String] = None,
  finalRoundPlaymode: Option[String] = None,
  maxPersons: Option[Int] = None,
  manualFinalRankings: Boolean = false,
  players: Seq[CttPlayer] = Nil,
  matches: Option[Seq[CttMatch]] = None
) derives ReadWriter

case class CttPlayer(
  typ: String,
  id: String,
  teamName: Option[String] = None,
  teamNr: Option[String] = None,
  placement: Option[String] = None,
  persons: Seq[CttPerson] = Nil
) derives ReadWriter

case class CttPerson(
  firstname: String,
  lastname: String,
  birthyear: String,
  internalNr: String,
  licenceNr: String,
  sex: Int,
  clubName: Option[String] = None,
  clubNr: Option[String] = None,
  clubFederationNickname: Option[String] = None,
  ttr: Option[Int] = None,
  ttrMatchCount: Option[Int] = None,
  nationality: Option[String] = None,
  foreignerEqState: Option[String] = None,
  region: Option[String] = None,
  subRegion: Option[String] = None
) derives ReadWriter

case class CttMatch(
  nr: Option[String] = None,
  group: Option[String] = None,
  scheduled: Option[String] = None,
  playerA: String,
  playerB: String,
  state: Option[String] = None,
  setA: Seq[Int] = Nil,
  setB: Seq[Int] = Nil,
  setsA: Int,
  setsB: Int,
  matchesA: Int,
  matchesB: Int,
  gamesA: Int,
  gamesB: Int
) derives ReadWriter
