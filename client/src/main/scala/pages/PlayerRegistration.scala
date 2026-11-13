package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.MainIds.*
import shared.model.*
import shared.AuthIds.*
import shared.BoxButton
import scala.scalajs.js
import dialogs.*

/**
 * Page handling the registration of players for a selected competition.
 * Allows adding single or double participants, and toggling active status.
 */
object PlayerRegistration extends BasePage with JsWrapper:
  def name = PageNameTyp("PlayerRegistration")

  /** Button to add a new participant. */
  val BtnAddParticipant: HtmlId = genId(name)
  /** Button to delete a registered participant. */
  val BtnDeleteParticipant: HtmlId = genId(name)
  /** Button to upload participant list from CSV. */
  val BtnUploadCsv:      HtmlId = genId(name)
  /** Info-Icon hinter "CSV Upload": zeigt das erlaubte CSV-Format mit Beispiel. */
  val BtnCsvInfo:        HtmlId = genId(name)
  /** Tree Node Glyph vor dem Mannschaftsnamen: Spieler der Mannschaft ein-/ausklappen, Suffix = Mannschafts-Id. */
  val BtnTeamTng:        HtmlId = genId(name)
  /** Ids der aufgeklappten Mannschaften (bleibt beim Neu-Rendern erhalten). */
  var expandedTeams: Set[Int] = Set.empty
  /** Select element to choose a competition. */
  val SelectComp:        HtmlId = genId(name)

  // Header IDs for sorting
  val IdHeaderSno:       HtmlId = genId(name)
  val IdHeaderName:      HtmlId = genId(name)
  val IdHeaderClub:      HtmlId = genId(name)
  val IdHeaderTtr:       HtmlId = genId(name)
  val IdHeaderYear:      HtmlId = genId(name)
  val IdHeaderActive:    HtmlId = genId(name)

  val IdCheckActive:     HtmlId = genId(name)

  private var sortCol = "name"
  private var sortAsc = true

  def render(param: String = ""): Boolean = 
    // Handle parameter for direct competition selection
    if (param.nonEmpty) {
      val cId = CompId(param.toInt)
      services.CompetitionDB.competitions.find(c => c != null && c.id == cId).foreach { c =>
        Global.currentSelection = Global.currentSelection.copy(competition = Some(c))
        comps.ContextHeader.render()
      }
    }

    var selection = Global.currentSelection
    val competitions = services.TourneyDB.tourney.competitions.toSeq.filter(_ != null)
    
    // If it's a SIMPLE tournament and no competition is selected, select the first one automatically
    if (selection.competition.isEmpty && services.TourneyDB.tourney.ident == "SIMPLE") {
      competitions.headOption.foreach { c =>
        Global.currentSelection = Global.currentSelection.copy(competition = Some(c))
        selection = Global.currentSelection
      }
    }

    comps.ContextHeader.render()

    selection.competition match
      case Some(c) => 
        val filteredPants = if (c.typ == CompTyp.DOUBLE || c.typ == CompTyp.MIXED) {
          c.pants1Stage.filter(_.id.isDouble).toSeq
        } else if (shared.format.TeamEncounter.isTeam(c.typ)) {
          c.pants1Stage.filter(_.id.isTeam).toSeq
        } else {
          c.pants1Stage.filter(_.id.isSingle).toSeq
        }
        val participants = sortParticipants(filteredPants)
        setMain(cviews.pages.html.PlayerRegistration(selection, competitions, participants, sortCol, sortAsc))
        true
      case None => 
        setMain(cviews.pages.html.PlayerRegistration(selection, competitions, Seq.empty, sortCol, sortAsc))
        true

  private def sortParticipants(pants: Seq[Pant]): Seq[Pant] =
    val sorted = sortCol match
      case "sno"    => pants.sortBy(_.id.startId)(Ordering.String)
      case "club"   => pants.sortBy(_.club.toLowerCase)(Ordering.String)
      case "ttr"    => pants.sortBy(-_.rating)
      case "year"   => pants.sortBy(_.birthYear)(Ordering.String)
      case "active" => pants.sortBy(_.active)
      case _        => pants.sortBy(_.name.toLowerCase)(Ordering.String)
    
    if (sortAsc) sorted else sorted.reverse

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `SelectComp` =>
        val cIdVal = elem.asInstanceOf[dom.html.Select].value.toInt
        if (cIdVal > 0) {
          render(cIdVal.toString)
        } else {
          Global.currentSelection = Global.currentSelection.copy(competition = None)
          comps.ContextHeader.render()
          render()
        }

      case `IdHeaderSno`    => toggleSort("sno")
      case `IdHeaderName`   => toggleSort("name")
      case `IdHeaderClub`   => toggleSort("club")
      case `IdHeaderTtr`    => toggleSort("ttr")
      case `IdHeaderYear`   => toggleSort("year")
      case `IdHeaderActive` => toggleSort("active")

      case `BtnAddParticipant` =>
        handleAddParticipant()

      case `BtnUploadCsv` =>
        doUploadCsv()

      case id if id.id.startsWith(BtnTeamTng.id + "-") =>
        id.id.substring(BtnTeamTng.id.length + 1).toIntOption.foreach(toggleTeam)

      case `BtnCsvInfo` =>
        Global.currentSelection.competition.foreach(c => dialogs.DlgCsvInfo.show(c.typ))

      case id if id.id.startsWith(BtnDeleteParticipant.id) =>
        val snoStr = elem.getAttribute("data-sno")
        removeParticipant(snoStr)

      case id if id.id.startsWith(IdCheckActive.id) =>
        val snoStr = elem.getAttribute("data-sno")
        val active = elem.asInstanceOf[dom.html.Input].checked
        updateParticipation(snoStr, active)

      case _ => 
        debug(s"PlayerRegistration handleEvent: ${elem.id}")

  /**
   * Klappt die Spielerliste einer Mannschaft ein oder aus, ohne die Seite neu zu rendern.
   *
   * @param teamId Id der Mannschaft
   */
  private def toggleTeam(teamId: Int): Unit =
    val open = !expandedTeams.contains(teamId)
    expandedTeams = if open then expandedTeams + teamId else expandedTeams - teamId
    val rows = dom.document.querySelectorAll(s".team-players-$teamId")
    (0 until rows.length).foreach(k => rows(k).classList.toggle("d-none", !open))
    Option(dom.document.getElementById(s"team-tng-icon-$teamId"))
      .foreach(_.setAttribute("class", if open then "bi bi-caret-down-fill" else "bi bi-caret-right-fill"))

  private def toggleSort(col: String): Unit =
    if (sortCol == col) sortAsc = !sortAsc
    else {
      sortCol = col
      sortAsc = true
    }
    render()

  /**
   * Checks if player registration modifications are locked for the given competition.
   * Registration is locked if the competition status is FIN or its start stage is no longer in CFG status.
   *
   * @param comp The competition to check.
   * @return True if registration changes should be disabled.
   */
  def isLocked(comp: Competition): Boolean =
    services.TourneyDB.tourney.isRegLocked(comp)

  private def updateParticipation(snoStr: String, active: Boolean): Unit =
    Global.currentSelection.competition.filter(!isLocked(_)).foreach { c =>
      val sno = SNO.fromString(snoStr)
      c.pants1Stage.find(_.id == sno).foreach { p =>
        p.active = active
        p.status = if (active) PantStatus.PLAY else PantStatus.REGI
        debug(s"Updated participant ${p.name}: active=$active")
        services.TourneyDB.tourney.updateCompetition(c)
        render() // Refresh UI
      }
    }

  /**
   * Entfernt eine Meldung aus dem ausgewählten Wettbewerb. Bei Doppeln und Mannschaften wird vorher nachgefragt;
   * es wird nur das Doppel bzw. die Mannschaft gelöscht, die Spieler bleiben im Turnier.
   *
   * @param snoStr Startnummer der Meldung
   */
  private def removeParticipant(snoStr: String): Unit =
    val sno = SNO.fromString(snoStr)
    val pantOpt = Global.currentSelection.competition.flatMap(_.pants1Stage.find(_.id == sno))
    pantOpt match
      case Some(pant) if sno.isDouble || sno.isTeam =>
        val (question, title) =
          if (sno.isTeam) (gM("PlayerRegistration.delete_team_question", pant.name), gM("PlayerRegistration.delete_team_title"))
          else            (gM("PlayerRegistration.delete_double_question", pant.name), gM("PlayerRegistration.delete_double_title"))
        dialogs.DlgMsgbox.show(question, title, List(BoxButton.Yes, BoxButton.No)).map {
          case BoxButton.Yes => doRemoveParticipant(sno)
          case _             => ()
        }
      case _ => doRemoveParticipant(sno)

  /**
   * Entfernt die Meldung ohne Rückfrage; bei Mannschaften auch die Mannschaft samt Spielerzuordnung.
   *
   * @param sno Startnummer der Meldung
   */
  private def doRemoveParticipant(sno: SNO): Unit =
    Global.currentSelection.competition.filter(!isLocked(_)).foreach { c =>
      val snoStr = sno.toString
      val idx = c.pants1Stage.indexWhere(_.id == sno)
      if (idx != -1) {
        c.pants1Stage.remove(idx)
        // gemeldete Mannschaft: Mannschaft samt Spielerzuordnung entfernen
        if (sno.isTeam) c.teams.filterInPlace(_.id != sno.teamId)
        debug(s"Removed participant with SNO: $snoStr")
        services.TourneyDB.tourney.updateCompetition(c)
        render() // Refresh UI
      }
    }

  /**
   * Stellt den Verein sicher und sucht einen gleichen Spieler (Name, Verein, Jahrgang) im Turnier oder legt ihn neu an;
   * der TTR-Wert aus den Eingaben wird übernommen.
   *
   * @param res Eingaben aus [[dialogs.DlgAddSingle]]
   * @return Spieler oder Fehler
   */
  def findOrAddPlayer(res: dialogs.DlgAddSingleResult): Either[AppError, Player] =
    val tourney = services.TourneyDB.tourney
    val club = tourney.clubs.find(_.name == res.clubName).getOrElse {
      tourney.addClub(res.clubName, checkSimilarity = false, doSync = true).toOption.get
    }
    tourney.players.find(p =>
      p.firstName == res.firstName &&
      p.lastName == res.lastName &&
      p.clubId == club.id.toInt &&
      p.birthYear == res.year
    ) match {
      case Some(existingPlayer) =>
        val updatedPlayer = existingPlayer.copy(
          meta = existingPlayer.meta.copy(ttr = res.ttr)
        )
        tourney.updatePlayer(updatedPlayer)
        Right(updatedPlayer)
      case None =>
        tourney.addPlayer(
          firstName = res.firstName, 
          lastName = res.lastName, 
          clubId = club.id.toInt, 
          birthYear = res.year, 
          email = res.email,
          whatsApp = res.whatsApp,
          doSync = true
        ).map { player =>
          val updatedPlayer = player.copy(
            meta = player.meta.copy(ttr = res.ttr)
          )
          tourney.updatePlayer(updatedPlayer)
          updatedPlayer
        }
    }

  private def handleAddParticipant(): Unit =
    Global.currentSelection.competition.filter(!isLocked(_)).foreach { c =>
      val tourney = services.TourneyDB.tourney
      if (shared.format.TeamEncounter.isTeam(c.typ)) {
        // Mannschaft über den Dialog der Seite "Mannschaften" anlegen und melden
        TeamList.addTeam(c, () => render())
      } else if (c.typ == CompTyp.DOUBLE || c.typ == CompTyp.MIXED) {
        val registeredPlayerIds: Set[PlayerId] = c.pants1Stage.flatMap { pant =>
          if (pant.id.isDouble) {
            val (p1, p2) = pant.id.doubleId
            Seq(p1, p2)
          } else Seq.empty
        }.toSet

        val availablePlayers = tourney.players.toSeq.filterNot(p => registeredPlayerIds.contains(p.id))
        val clubs = tourney.clubs.toSeq
        val createPlayer = (res: dialogs.DlgAddSingleResult) =>
          findOrAddPlayer(res).map(pl => (pl, tourney.clubs.toSeq))
        dialogs.DlgAddDouble.show(availablePlayers, clubs, createPlayer).map {
          case Right(res) =>
            // Create Pant for double
            val doubleSno = SNO.double(res.player1.id, res.player2.id)
            val p = Pant(
              id = doubleSno,
              name = s"${res.player1.lastName} / ${res.player2.lastName}",
              club = if (res.player1.clubId == res.player2.clubId) {
                tourney.clubs.find(_.id.toInt == res.player1.clubId).map(_.name).getOrElse("")
              } else {
                val c1 = tourney.clubs.find(_.id.toInt == res.player1.clubId).map(_.name).getOrElse("")
                val c2 = tourney.clubs.find(_.id.toInt == res.player2.clubId).map(_.name).getOrElse("")
                s"$c1, $c2"
              },
              rating = (res.player1.meta.ttr.getOrElse(0) + res.player2.meta.ttr.getOrElse(0)) / 2,
              birthYear = "", // No combined birthyear for doubles
              active = res.enroll,
              status = if (res.enroll) PantStatus.PLAY else PantStatus.REGI
            )
            c.pants1Stage += p
            tourney.updateCompetition(c)
            render()
          case _ => debug("Add Double cancelled")
        }
      } else {
        val clubs = tourney.clubs.toSeq
        dialogs.DlgAddSingle.show(clubs).map {
          case Right(res) =>
            // 1./2. Verein sicherstellen, Spieler suchen oder neu anlegen
            val playerResult = findOrAddPlayer(res)
            val clubName = playerResult.toOption.flatMap(pl => tourney.clubs.find(_.id.toInt == pl.clubId)).map(_.name).getOrElse(res.clubName)

            playerResult match {
              case Right(updatedPlayer) =>
                // 3. Create Pant and add to competition
                val singleSno = SNO.single(updatedPlayer.id)
                if (c.pants1Stage.exists(_.id == singleSno)) {
                  dom.window.alert("Spieler ist bereits in diesem Wettbewerb angemeldet.")
                } else {
                  val p = Pant(
                    id = singleSno,
                    name = updatedPlayer.displayName,
                    club = clubName,
                    rating = res.ttr.getOrElse(0),
                    birthYear = res.year.map(_.toString).getOrElse(""),
                    active = res.enroll,
                    status = if (res.enroll) PantStatus.PLAY else PantStatus.REGI
                  )
                  c.pants1Stage += p
                  tourney.updateCompetition(c)
                  render()
                }
                
              case Left(err) =>
                dialogs.DlgMsgbox.showError(err, "Fehler beim Hinzufügen des Spielers")
            }
          case _ => debug("Add Single cancelled")
        }
      }
    }

  /**
   * Handles uploading a CSV file containing player data.
   * - For SINGLE competition: Players are added to tourney.players (if new) and added as inactive participants (PantStatus.REGI) to pants1Stage.
   * - For DOUBLE or MIXED competition: Players are added to tourney.players only (if new) and NOT added to pants1Stage.
   */
  private def doUploadCsv(): Unit = {
    val selection = Global.currentSelection
    val tourney   = services.TourneyDB.tourney
    selection.competition.foreach { c =>
      val isSingleComp = (c.typ == CompTyp.SINGLE)
      val fileInput = dom.document.createElement("input").asInstanceOf[dom.html.Input]
      fileInput.`type` = "file"
      fileInput.accept = ".csv"
      
      fileInput.addEventListener("change", (e: dom.Event) => {
        val files = fileInput.files
        if (files.length > 0) {
          val file = files(0)
          val reader = new dom.FileReader()
          reader.onload = (event: dom.Event) => {
            val csvText = reader.result.asInstanceOf[String]
            
            val lines = csvText.split("\n").map(_.trim).filter(_.nonEmpty).toSeq
            if (lines.length <= 1) {
              dom.window.alert("Die CSV-Datei enthält keine ausreichenden Daten.")
            } else {
              val header = lines.head
              val sep = if (header.contains(";")) ";" else ","
              val cols = header.split(sep).map(_.trim.toLowerCase)
              
              val idxVorname = if (cols.indexOf("vorname") >= 0) cols.indexOf("vorname") else cols.indexOf("first name")
              val idxNachname = if (cols.indexOf("nachname") >= 0) cols.indexOf("nachname") else cols.indexOf("last name")
              val idxVerein = if (cols.indexOf("verein") >= 0) cols.indexOf("verein") else cols.indexOf("club")
              val idxTtr = cols.indexOf("ttr")
              
              val vIdx = if (idxVorname >= 0) idxVorname else 0
              val nIdx = if (idxNachname >= 0) idxNachname else 1
              val cIdx = if (idxVerein >= 0) idxVerein else 2
              val tIdx = if (idxTtr >= 0) idxTtr else 3
              // optionale Spalten nur über den Spaltennamen
              val yIdx = cols.indexWhere(Set("jahrgang", "geburtsjahr", "birth year", "year of birth").contains)
              val eIdx = cols.indexWhere(Set("email", "e-mail").contains)
              val wIdx = cols.indexWhere(Set("whatsapp", "whats app").contains)
              val curYear = new scala.scalajs.js.Date().getFullYear().toInt

              case class CsvPlayer(firstName: String, lastName: String, clubName: String, ttr: Option[Int],
                                   birthYear: Option[Int], email: Option[String], whatsApp: Option[String])
              
              val csvPlayers = lines.tail.flatMap { line =>
                val parts = line.split(sep).map(_.trim)
                if (parts.length > math.max(vIdx, math.max(nIdx, cIdx))) {
                  val fn = parts(vIdx)
                  val ln = parts(nIdx)
                  val cn = parts(cIdx)
                  val ttrVal = if (parts.length > tIdx) {
                    try Some(parts(tIdx).toInt) catch { case _: Exception => None }
                  } else None
                  
                  def opt(i: Int): Option[String] = if (i >= 0 && parts.length > i && parts(i).nonEmpty) Some(parts(i)) else None
                  val year  = opt(yIdx).flatMap(_.toIntOption).filter(y => y >= 1900 && y <= curYear)
                  val email = opt(eIdx).filter(_.contains("@"))
                  val wa    = opt(wIdx)

                  if (fn.nonEmpty && ln.nonEmpty && cn.nonEmpty) {
                    Some(CsvPlayer(fn, ln, cn, ttrVal, year, email, wa))
                  } else None
                } else None
              }
              
              def importNext(index: Int): Future[Unit] = {
                if (index >= csvPlayers.length) {
                  render()
                  Future.successful(())
                } else {
                  val p = csvPlayers(index)
                  val existingPlayerOpt = tourney.players.find(tp =>
                    tp.firstName.trim.equalsIgnoreCase(p.firstName) && tp.lastName.trim.equalsIgnoreCase(p.lastName)
                  )
                  
                  val playerRes: Either[AppError, Player] = existingPlayerOpt match {
                    case Some(player) =>
                      val updatedPlayer = player.copy(
                        birthYear = p.birthYear.orElse(player.birthYear),
                        email     = p.email.orElse(player.email),
                        whatsApp  = p.whatsApp.orElse(player.whatsApp),
                        meta = player.meta.copy(ttr = p.ttr.orElse(player.meta.ttr))
                      )
                      tourney.updatePlayer(updatedPlayer)
                      Right(updatedPlayer)
                    case None =>
                      val club = tourney.clubs.find(_.name.equalsIgnoreCase(p.clubName)).getOrElse {
                        tourney.addClub(p.clubName, checkSimilarity = false, doSync = true).toOption.get
                      }
                      tourney.addPlayer(
                        firstName = p.firstName,
                        lastName = p.lastName,
                        clubId = club.id.toInt,
                        birthYear = p.birthYear,
                        email = p.email,
                        whatsApp = p.whatsApp,
                        doSync = true
                      ).map { player =>
                        val updatedPlayer = player.copy(
                          meta = player.meta.copy(ttr = p.ttr)
                        )
                        tourney.updatePlayer(updatedPlayer)
                        updatedPlayer
                      }
                  }

                  playerRes match {
                    case Right(importedPlayer) =>
                      // For SINGLE competition, add to pants1Stage as inactive (PantStatus.REGI)
                      if (isSingleComp) {
                        val singleSno = SNO.single(importedPlayer.id)
                        val club = tourney.clubs.find(_.id.toInt == importedPlayer.clubId)
                        if (!c.pants1Stage.exists(_.id == singleSno)) {
                          val pant = Pant(
                            id = singleSno,
                            name = importedPlayer.displayName,
                            club = club.map(_.name).getOrElse(""),
                            rating = p.ttr.orElse(importedPlayer.meta.ttr).getOrElse(0),
                            birthYear = importedPlayer.birthYear.map(_.toString).getOrElse(""),
                            active = false,
                            status = PantStatus.REGI
                          )
                          c.pants1Stage += pant
                          tourney.updateCompetition(c)
                        }
                      }
                    case Left(err) =>
                      Logging.error(s"Failed to add player from CSV: ${err.msgCode}")
                  }

                  importNext(index + 1)
                }
              }
              
              importNext(0)
            }
          }
          reader.readAsText(file)
        }
      })
      
      fileInput.click()
    }
  }
