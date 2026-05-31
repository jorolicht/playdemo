package pages

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event

import shared.basic.*
import shared.basic.Pickle.*
import shared.MainIds.*
import shared.model.*
import services.*
import base.*

object MainSearch extends BasePage with JsWrapper with ComWrapper with Debouncer:
  def name = PageNameTyp("MainSearch")

  val IdInputTitle:        HtmlId = genId(name)
  val IdInputOrganizer:    HtmlId = genId(name)
  val IdInputDate:         HtmlId = genId(name)
  val IdResultsBody:       HtmlId = genId(name)
  val IdResultsContainer:  HtmlId = genId(name)
  val IdResultCount:       HtmlId = genId(name)
  val IdHeaderDate:        HtmlId = genId(name)
  val IdSortIcon:          HtmlId = genId(name)
  val IdMainSearchForm:    HtmlId = genId(name)
  val InputResultId:       HtmlId = genId(name)

  case class SearchResult(id: Int, name: String, organizer: String, startDate: Int, status: String, slug: String) derives ReadWriter

  private var sortOrder = "DESC"

  def render(param: String = ""): Boolean = 
    setMain(cviews.pages.html.MainSearch())
    // Initial state: empty results
    updateResultsTable(Nil)
    true

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `IdInputTitle` | `IdInputOrganizer` | `IdInputDate` => 
        debounce(delay = 800) {
          performSearch()
        }
      case `IdHeaderDate` =>
        toggleSort()
      case id if id.id.startsWith(InputResultId.id) =>
        val tourneyId = getData(elem,"tourney-id", 0)
        debug(s"Switching to tournament post: $tourneyId")

        // 1. Update Global PageId
        Global.pageId = tourneyId

        // 2. Clear current selection to force reload context
        Global.currentSelection = Selection()

        // 3. Initialize all data from server for the new ID
        TourneyDB.init(tourneyId).map {
          case Right(_) => 
            loadPage(TourneyInfo.name, "")
          case Left(err) => 
            error(s"Failed to initialize tournament $tourneyId: ${err.msgCode}")
            dom.window.alert(s"Fehler beim Laden des Turniers: ${err.msgCode}")
        }
      case _ => 
        debug(s"MainSearch handleEvent: ${elem.id}")

  private def toggleSort(): Unit =
    sortOrder = if (sortOrder == "DESC") "ASC" else "DESC"
    // Update Icon visually
    val icon = gE(IdSortIcon)
    if (icon != null) {
      icon.className = if (sortOrder == "ASC") "bi bi-sort-numeric-down ms-1" else "bi bi-sort-numeric-up-alt ms-1"
    }
    performSearch()

  private def performSearch(): Unit =
    val title     = getInput(gE(IdInputTitle), "")
    val organizer = getInput(gE(IdInputOrganizer), "")
    val date      = getInput(gE(IdInputDate), "").replace("-", "")

    if (title.isEmpty && organizer.isEmpty && date.isEmpty) {
      updateResultsTable(Nil)
    } else {
      val params = List(
        "q"         -> title,
        "organizer" -> organizer,
        "dateFrom"  -> date,
        "order"     -> sortOrder
      )

      ajaxGet[Seq[SearchResult]]("/wp-json/tourney/v1/search", params).map {
        case Right(results) =>
          updateResultsTable(results)
        case Left(err) =>
          error(s"Search failed: ${err.msgCode}")
          setHtml(gE(IdResultsBody), s"<tr><td colspan='4' class='text-center text-danger'>Suche fehlgeschlagen: ${err.msgCode}</td></tr>")
      }
    }

  private def updateResultsTable(results: Seq[SearchResult]): Unit =
    setHtml(gE(IdResultCount), s"${results.length} Turniere gefunden")
    
    if (results.isEmpty) {
      val msg = if (getInput(gE(IdInputTitle), "").isEmpty && 
                    getInput(gE(IdInputOrganizer), "").isEmpty && 
                    getInput(gE(IdInputDate), "").isEmpty) 
                "Geben Sie einen Suchbegriff ein, um die Suche zu starten."
                else "Keine Turniere gefunden."
      setHtml(gE(IdResultsBody), s"<tr><td colspan='4' class='text-center py-4 text-muted'>$msg</td></tr>")
    } else {
      val html = results.map { r =>
        val dateDisplay = formatDate(r.startDate.toString)
        cviews.comps.html.MainSearchInputResult(r, dateDisplay).toString
      }.mkString("")
      setHtml(gE(IdResultsBody), html)
    }

  private def formatDate(yyyymmdd: String): String =
    if (yyyymmdd.length == 8) {
      s"${yyyymmdd.substring(6, 8)}.${yyyymmdd.substring(4, 6)}.${yyyymmdd.substring(0, 4)}"
    } else yyyymmdd
