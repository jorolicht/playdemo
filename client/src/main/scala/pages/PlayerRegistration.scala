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
        val participants = sortParticipants(c.pants1Stage.toSeq)
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

      case id if id.id.startsWith(BtnDeleteParticipant.id) =>
        val snoStr = elem.getAttribute("data-sno")
        removeParticipant(snoStr)

      case id if id.id.startsWith(IdCheckActive.id) =>
        val snoStr = elem.getAttribute("data-sno")
        val active = elem.asInstanceOf[dom.html.Input].checked
        updateParticipation(snoStr, active)

      case _ => 
        debug(s"PlayerRegistration handleEvent: ${elem.id}")

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

  private def removeParticipant(snoStr: String): Unit =
    Global.currentSelection.competition.filter(!isLocked(_)).foreach { c =>
      val sno = SNO.fromString(snoStr)
      val idx = c.pants1Stage.indexWhere(_.id == sno)
      if (idx != -1) {
        c.pants1Stage.remove(idx)
        debug(s"Removed participant with SNO: $snoStr")
        services.TourneyDB.tourney.updateCompetition(c)
        render() // Refresh UI
      }
    }

  private def handleAddParticipant(): Unit =
    Global.currentSelection.competition.filter(!isLocked(_)).foreach { c =>
      val tourney = services.TourneyDB.tourney
      if (c.typ == CompTyp.DOUBLE) {
        val players = tourney.players.toSeq
        val clubs = tourney.clubs.toSeq
        dialogs.DlgAddDouble.show(players, clubs).map {
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
            // 1. Ensure club exists
            val club = tourney.clubs.find(_.name == res.clubName).getOrElse {
              tourney.addClub(res.clubName, checkSimilarity = false, doSync = true).toOption.get
            }
            
            // 2. Find existing player or add new player to tourney
            val playerResult = tourney.players.find(p =>
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
                    club = club.name,
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
                dom.window.alert(s"Fehler beim Hinzufügen des Spielers: ${err.msgCode}")
            }
          case _ => debug("Add Single cancelled")
        }
      }
    }

  private def doUploadCsv(): Unit = {
    val selection = Global.currentSelection
    val tourney   = services.TourneyDB.tourney
    selection.competition.foreach { c =>
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
              
              case class CsvPlayer(firstName: String, lastName: String, clubName: String, ttr: Option[Int])
              
              val csvPlayers = lines.tail.flatMap { line =>
                val parts = line.split(sep).map(_.trim)
                if (parts.length > math.max(vIdx, math.max(nIdx, cIdx))) {
                  val fn = parts(vIdx)
                  val ln = parts(nIdx)
                  val cn = parts(cIdx)
                  val ttrVal = if (parts.length > tIdx) {
                    try Some(parts(tIdx).toInt) catch { case _: Exception => None }
                  } else None
                  
                  if (fn.nonEmpty && ln.nonEmpty && cn.nonEmpty) {
                    Some(CsvPlayer(fn, ln, cn, ttrVal))
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
                  if (existingPlayerOpt.isDefined) {
                    val player = existingPlayerOpt.get
                    val club = tourney.clubs.find(_.id.toInt == player.clubId)
                    val singleSno = SNO.single(player.id)
                    if (!c.pants1Stage.exists(_.id == singleSno)) {
                      val pant = Pant(
                        id = singleSno,
                        name = player.displayName,
                        club = club.map(_.name).getOrElse(""),
                        rating = p.ttr.orElse(player.meta.ttr).getOrElse(0),
                        birthYear = player.birthYear.map(_.toString).getOrElse(""),
                        active = true,
                        status = PantStatus.PLAY
                      )
                      c.pants1Stage += pant
                      tourney.updateCompetition(c)
                    }
                    importNext(index + 1)
                  } else {
                    val club = tourney.clubs.find(_.name.equalsIgnoreCase(p.clubName)).getOrElse {
                      tourney.addClub(p.clubName, checkSimilarity = false, doSync = true).toOption.get
                    }
                    
                    tourney.addPlayer(
                      firstName = p.firstName,
                      lastName = p.lastName,
                      clubId = club.id.toInt,
                      birthYear = None,
                      email = None,
                      whatsApp = None,
                      doSync = true
                    ) match {
                      case Right(player) =>
                        val updatedPlayer = player.copy(
                          meta = player.meta.copy(ttr = p.ttr)
                        )
                        tourney.updatePlayer(updatedPlayer)
                        
                        val singleSno = SNO.single(updatedPlayer.id)
                        val pant = Pant(
                          id = singleSno,
                          name = updatedPlayer.displayName,
                          club = club.name,
                          rating = p.ttr.getOrElse(0),
                          birthYear = "",
                          active = true,
                          status = PantStatus.PLAY
                        )
                        c.pants1Stage += pant
                        tourney.updateCompetition(c)
                      case Left(err) =>
                        Logging.error(s"Failed to add player from CSV: ${err.msgCode}")
                    }
                    importNext(index + 1)
                  }
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
