package pages

import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.raw.HTMLElement
import base.*
import shared.model.*

/**
 * Page listing participants for all competitions.
 * Displays participant IDs and allows toggling their active status for the initial stage.
 */
object PlayerList extends BasePage with JsWrapper:
  def name = PageNameTyp("PlayerList")

  /** HTML ID for the active/participation checkbox. */
  val IdCheckActive: HtmlId = genId(name)

  /** Stores the ID of the currently expanded competition accordion item. */
  private var expandedCompId: Option[Int] = None

  /**
   * Captures the currently open accordion item from the DOM and saves its ID.
   */
  private def saveExpandedState(): Unit =
    val openItem = dom.document.querySelector(".accordion-collapse.show")
    if (openItem != null) {
      val idStr = openItem.id.replace("collapsePlayer", "")
      expandedCompId = idStr.toIntOption
    } else {
      expandedCompId = None
    }

  /**
   * Renders the page.
   *
   * @param param Optional page parameter.
   * @return true if rendering succeeded.
   */
  def render(param: String = ""): Boolean = 
    comps.ContextHeader.render()
    val competitions = services.CompetitionDB.competitions.toSeq.filter(c => c != null && !c.deleted)
    setMain(cviews.pages.html.PlayerList(competitions, expandedCompId))
    true

  /**
   * Handles DOM events for this page.
   *
   * @param elem The element that triggered the event.
   * @param event The event object.
   */
  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case id if id.id.startsWith(IdCheckActive.id) =>
        val compIdVal = elem.getAttribute("data-comp-id").toInt
        val snoStr = elem.getAttribute("data-sno")
        val checked = elem.asInstanceOf[dom.html.Input].checked
        
        val t = services.TourneyDB.tourney
        val cIdx = compIdVal - 1
        if (cIdx >= 0 && cIdx < 64 && t.competitions(cIdx) != null) {
          val comp = t.competitions(cIdx)
          if (!t.isRegLocked(comp)) {
            val sno = SNO.fromString(snoStr)
            comp.pants1Stage.find(_.id == sno).foreach { p =>
              p.active = checked
              p.status = if (checked) PantStatus.PLAY else PantStatus.REGI
              debug(s"Updated player active status: ${p.name} -> active=$checked")
              
              // Capture expanded state right before updating and rerendering
              saveExpandedState()
              
              t.updateCompetition(comp)
              render()
            }
          }
        }
      case _ =>
