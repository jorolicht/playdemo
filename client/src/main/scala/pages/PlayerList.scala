package pages

import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.raw.HTMLElement
import base.*
import shared.model.*
import shared.BoxButton
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/**
 * Page listing all players in the tournament (Breadcrumb "Teilnehmer").
 * Displays ID, Name, Club, TTR, enrolled competitions, and allows deletion from all competitions.
 */
object PlayerList extends BasePage with JsWrapper:
  def name = PageNameTyp("PlayerList")

  /**
   * Data model for a row in the player overview table.
   *
   * @param player The Player model object.
   * @param clubName Name of the player's club.
   * @param competitions Sequence of competition names in which the player is participating.
   */
  case class PlayerListRow(
    player: Player,
    clubName: String,
    competitions: Seq[String]
  )

  /** HTML ID for sorting by ID column. */
  val IdHeaderId: HtmlId = genId(name)

  /** HTML ID for sorting by Name column. */
  val IdHeaderName: HtmlId = genId(name)

  /** HTML ID for sorting by Club column. */
  val IdHeaderClub: HtmlId = genId(name)

  /** HTML ID for sorting by TTR column. */
  val IdHeaderTtr: HtmlId = genId(name)

  /** HTML ID prefix for deleting a player. */
  val BtnDeletePlayer: HtmlId = genId(name)

  private var sortCol = "name"
  private var sortAsc = true

  /**
   * Toggles the current sorting column or direction.
   *
   * @param col Column key to sort by.
   */
  private def toggleSort(col: String): Unit =
    if (sortCol == col) sortAsc = !sortAsc
    else {
      sortCol = col
      sortAsc = true
    }
    render()

  /**
   * Sorts the sequence of PlayerListRow objects based on the active sort column and direction.
   *
   * @param rows Sequence of rows to sort.
   * @return Sorted sequence of rows.
   */
  private def sortRows(rows: Seq[PlayerListRow]): Seq[PlayerListRow] =
    val sorted = sortCol match
      case "id"   => rows.sortBy(_.player.id.value)
      case "club" => rows.sortBy(_.clubName.toLowerCase)(Ordering.String)
      case "ttr"  => rows.sortBy(-_.player.meta.ttr.getOrElse(0))
      case _      => rows.sortBy(_.player.displayName.toLowerCase)(Ordering.String)
    
    if (sortAsc) sorted else sorted.reverse

  /**
   * Renders the player list overview page.
   *
   * @param param Optional rendering parameter.
   * @return true if rendering succeeded.
   */
  def render(param: String = ""): Boolean = 
    comps.ContextHeader.render()
    val tourney = services.TourneyDB.tourney
    val compsSeq = tourney.competitions.toSeq.filter(c => c != null && !c.deleted)
    val clubsMap = tourney.clubs.map(c => c.id.toInt -> c.name).toMap

    val rows = tourney.players.toSeq.map { p =>
      val club = clubsMap.getOrElse(p.clubId, "")
      val playerComps = compsSeq.filter { c =>
        c.pants1Stage.exists { pant =>
          (pant.id.isSingle && pant.id.singleId == p.id) ||
          (pant.id.isDouble && (pant.id.doubleId._1 == p.id || pant.id.doubleId._2 == p.id))
        }
      }.map(_.name)
      PlayerListRow(p, club, playerComps)
    }

    val sortedRows = sortRows(rows)
    setMain(cviews.pages.html.PlayerList(sortedRows, sortCol, sortAsc))
    true

  /**
   * Handles DOM events for this page.
   *
   * @param elem The element that triggered the event.
   * @param event The event object.
   */
  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `IdHeaderId`   => toggleSort("id")
      case `IdHeaderName` => toggleSort("name")
      case `IdHeaderClub` => toggleSort("club")
      case `IdHeaderTtr`  => toggleSort("ttr")

      case id if id.id.startsWith(BtnDeletePlayer.id) =>
        val pIdVal = elem.getAttribute("data-player-id").toInt
        val tourney = services.TourneyDB.tourney
        tourney.players.find(_.id == PlayerId(pIdVal)).foreach { player =>
          dialogs.DlgMsgbox.show(
            s"Möchten Sie den Spieler '${player.displayName}' wirklich aus allen Wettbewerben und dem Turnier löschen?",
            "Spieler löschen",
            List(BoxButton.Yes, BoxButton.No)
          ).map { btn =>
            if (btn == BoxButton.Yes) {
              tourney.removePlayer(player.id)
              render()
            }
          }
        }

      case _ =>
