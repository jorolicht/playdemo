package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.MainIds.*
import shared.model.*
import dialogs.*

object PlayerRegistration extends BasePage with JsWrapper:
  def name = PageNameTyp("PlayerRegistration")

  val BtnAddParticipant: HtmlId = genId(name)
  val BtnUploadCsv:      HtmlId = genId(name)
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

    val selection = Global.currentSelection
    val competitions = services.TourneyDB.tourney.competitions.toSeq.filter(_ != null)
    
    selection.competition match
      case Some(c) => 
        val participants = sortParticipants(c.pants.toSeq)
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
        debug("PlayerRegistration: CSV Upload clicked")
        // Placeholder for CSV upload logic - as requested only to add the button
        dom.window.alert("CSV Upload Funktion wird demnächst implementiert.")

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

  private def updateParticipation(snoStr: String, active: Boolean): Unit =
    Global.currentSelection.competition.foreach { c =>
      val sno = SNO.fromString(snoStr)
      c.pants.find(_.id == sno).foreach { p =>
        p.active = active
        p.status = if (active) PantStatus.PLAY else PantStatus.REGI
        debug(s"Updated participant ${p.name}: active=$active")
        services.TourneyDB.tourney.updateCompetition(c)
        render() // Refresh UI
      }
    }

  private def handleAddParticipant(): Unit =
    Global.currentSelection.competition.foreach { c =>
      val tourney = services.TourneyDB.tourney
      if (c.typ == CompTyp.DOUBLE) {
        val players = tourney.players.toSeq
        val clubs = tourney.clubs.toSeq
        dialogs.DlgAddDouble.show(players, clubs).map {
          case Right(res) =>
            // Create Pant for double
            val p = Pant(
              id = SNO.double(res.player1.id, res.player2.id),
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
            c.pants += p
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
            
            // 2. Add player to tourney
            tourney.addPlayer(res.firstName, res.lastName, club.id.toInt, res.year, doSync = true) match {
              case Right(player) =>
                val updatedPlayer = player.copy(
                  meta = player.meta.copy(ttr = res.ttr)
                )
                tourney.updatePlayer(updatedPlayer)

                // 3. Create Pant and add to competition
                val p = Pant(
                  id = SNO.single(updatedPlayer.id),
                  name = updatedPlayer.displayName,
                  club = club.name,
                  rating = res.ttr.getOrElse(0),
                  birthYear = res.year.map(_.toString).getOrElse(""),
                  active = res.enroll,
                  status = if (res.enroll) PantStatus.PLAY else PantStatus.REGI
                )
                c.pants += p
                tourney.updateCompetition(c)
                render()
                
              case Left(err) =>
                dom.window.alert(s"Fehler beim Hinzufügen des Spielers: ${err.msgCode}")
            }
          case _ => debug("Add Single cancelled")
        }
      }
    }
