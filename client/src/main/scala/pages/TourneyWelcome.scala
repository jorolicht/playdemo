package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.MainIds.*
import shared.model.*

/**
 * Public Tournament Welcome Homepage page for VIEW-Mode.
 * Displays greeting, tournament name, date, competitions schedule with start times,
 * online registration buttons, and homepageInfo in Markdown format.
 */
object TourneyWelcome extends BasePage with JsWrapper:
  def name = PageNameTyp("TourneyWelcome")

  /**
   * Checks whether a tournament start/end date is today or in the future.
   *
   * @param tourney The tournament object.
   * @return True if the tournament is in the future or today, false if in the past.
   */
  def isFutureOrToday(tourney: Tourney): Boolean =
    try {
      val d = new scala.scalajs.js.Date()
      val yyyy = d.getFullYear().toInt
      val mm = d.getMonth().toInt + 1
      val dd = d.getDate().toInt
      val todayInt = yyyy * 10000 + mm * 100 + dd
      tourney.startDate == 0 || tourney.startDate >= todayInt || tourney.endDate >= todayInt
    } catch {
      case _: Exception => true
    }

  /**
   * Checks whether a competition start date/time (or tournament date) is in the future or present.
   *
   * @param c The competition object.
   * @param tourney The tournament object.
   * @return True if competition start date/time is in the future or present, false if in the past.
   */
  def isCompStartInFuture(c: Competition, tourney: Tourney): Boolean =
    try {
      val now = new scala.scalajs.js.Date().getTime()
      val startDateStr = if (c.startDate != null && c.startDate.nonEmpty) c.startDate else ""
      val clean = startDateStr.replaceAll("[^0-9]", "")

      if (clean.length >= 12) {
        val yyyy = clean.substring(0, 4).toInt
        val mm = clean.substring(4, 6).toInt - 1
        val dd = clean.substring(6, 8).toInt
        val hh = clean.substring(8, 10).toInt
        val min = clean.substring(10, 12).toInt
        val compTime = new scala.scalajs.js.Date(yyyy, mm, dd, hh, min).getTime()
        compTime >= now
      } else if (clean.length >= 8) {
        val yyyy = clean.substring(0, 4).toInt
        val mm = clean.substring(4, 6).toInt - 1
        val dd = clean.substring(6, 8).toInt
        val compTime = new scala.scalajs.js.Date(yyyy, mm, dd, 23, 59, 59).getTime()
        compTime >= now
      } else {
        isFutureOrToday(tourney)
      }
    } catch {
      case _: Exception => isFutureOrToday(tourney)
    }

  /**
   * Opens the public registration dialog for a competition in VIEW-mode.
   *
   * @param compIdStr Competition ID string.
   */
  def openPublicRegistration(compIdStr: String): Unit =
    try {
      val cId = compIdStr.toIntOption.getOrElse(0)
      val tourney = Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)
      val compOpt = services.CompetitionDB.competitions.find(c => c != null && c.id.value == cId)

      compOpt match {
        case Some(comp) =>
          dialogs.DlgPublicRegistration.show(tourney, comp).map {
            case Right(player) =>
              debug(s"Public registration successful for ${player.firstName} ${player.lastName}")
              // Re-render TourneyWelcome to update table
              render("")
            case Left(_) =>
              debug("Public registration dialog closed")
          }
        case None =>
          debug(s"Competition not found for ID: $compIdStr")
      }
    } catch {
      case e: Exception => error(s"openPublicRegistration error: ${e.getMessage}")
    }

  /**
   * Generates and triggers download of an iCalendar (.ics) file.
   *
   * @param title Event title.
   * @param rawDateStr Raw start date/time string.
   * @param venue Event location/address.
   * @param description Event description.
   */
  def downloadCalendarEvent(title: String, rawDateStr: String, venue: String, description: String): Unit =
    try {
      val clean = rawDateStr.replaceAll("[^0-9]", "")
      if (clean.length < 8) return

      val year = clean.substring(0, 4).toIntOption.getOrElse(2026)
      val month = clean.substring(4, 6).toIntOption.getOrElse(1) - 1
      val day = clean.substring(6, 8).toIntOption.getOrElse(1)
      val hour = if (clean.length >= 10) clean.substring(8, 10).toIntOption.getOrElse(9) else 9
      val min = if (clean.length >= 12) clean.substring(10, 12).toIntOption.getOrElse(0) else 0

      val startDate = new scala.scalajs.js.Date(year, month, day, hour, min)
      val endDate = new scala.scalajs.js.Date(startDate.getTime() + 3.0 * 3600.0 * 1000.0)

      def pad(n: Int): String = if (n < 10) s"0$n" else n.toString

      val dtStart = s"${startDate.getFullYear()}${pad(startDate.getMonth().toInt + 1)}${pad(startDate.getDate().toInt)}T${pad(startDate.getHours().toInt)}${pad(startDate.getMinutes().toInt)}00"
      val dtEnd = s"${endDate.getFullYear()}${pad(endDate.getMonth().toInt + 1)}${pad(endDate.getDate().toInt)}T${pad(endDate.getHours().toInt)}${pad(endDate.getMinutes().toInt)}00"

      val icsContent = List(
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Playdemo//Tournament Calendar//DE",
        "BEGIN:VEVENT",
        s"SUMMARY:$title",
        s"DESCRIPTION:$description",
        s"LOCATION:$venue",
        s"DTSTART:$dtStart",
        s"DTEND:$dtEnd",
        "END:VEVENT",
        "END:VCALENDAR"
      ).mkString("\r\n")

      val blob = new dom.Blob(
        scala.scalajs.js.Array(icsContent),
        dom.BlobPropertyBag(`type` = "text/calendar;charset=utf-8;")
      )

      val url = dom.URL.createObjectURL(blob)
      val a = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
      a.href = url
      val safeTitle = title.replaceAll("[^a-zA-Z0-9]", "_")
      a.setAttribute("download", s"$safeTitle.ics")
      dom.document.body.appendChild(a)
      a.click()
      dom.document.body.removeChild(a)
    } catch {
      case e: Exception => error(s"downloadCalendarEvent error: ${e.getMessage}")
    }

  /**
   * Renders the public tournament welcome homepage for VIEW-mode.
   *
   * @param param Optional parameter string.
   * @return True if tournament is present and rendered, false otherwise.
   */
  def render(param: String = ""): Boolean = 
    Global.currentSelection = Global.currentSelection.copy(competition = None, stage = None)

    val tourney = Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)

    if (tourney.wpId != 0) {
      Global.currentSelection = Global.currentSelection.copy(tourney = Some(tourney))
      
      // Register calendar download & public registration functions on window object
      dom.window.asInstanceOf[scala.scalajs.js.Dynamic].downloadCalendarEvent = 
        (title: String, rawDateStr: String, venue: String, description: String) =>
          downloadCalendarEvent(title, rawDateStr, venue, description)

      dom.window.asInstanceOf[scala.scalajs.js.Dynamic].openPublicRegistration = 
        (compIdStr: String) => openPublicRegistration(compIdStr)

      // Render ContextHeader sub-menu
      comps.ContextHeader.render()

      val compList = services.CompetitionDB.competitions.toSeq.filter(c => c != null && !c.deleted)

      setMain(cviews.pages.html.TourneyWelcome(tourney, compList))
      true
    } else {
      debug("TourneyWelcome: No tournament found, redirecting to Home")
      loadPage(MainView.name, "")
      false
    }
