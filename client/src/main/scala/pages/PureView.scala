package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import services.*
import base.*
import dialogs.*

/**
 * PureView page component for rendering the embedded 'pure' mode home view
 * without Navbar, Footer, status bar, or news bar, showing 3 cards (Demo, Neues Turnier, Schnellstart).
 */
object PureView extends BasePage with JsWrapper with ComWrapper:
  /** Name identifier for the page. */
  def name = PageNameTyp("PureView")

  /** HTML-ID for the Demo button. */
  val BtnDemo:    HtmlId = genId(name)
  /** HTML-ID for the New Tournament button. */
  val BtnOption1: HtmlId = genId(name)
  /** HTML-ID for the Quick Start (Single Competition) button. */
  val BtnOption2: HtmlId = genId(name)

  /**
   * Renders the PureView landing page.
   *
   * @param param Optional parameter string.
   * @return Boolean true on render.
   */
  def render(param: String = ""): Boolean =
    setMain(cviews.pages.PureView.html.Base())
    true

  /**
   * Event handler for button clicks and interactions on PureView.
   * Starts the corresponding action directly without confirmation dialogs
   * and saves tournament data into the current WordPress page.
   *
   * @param elem Target HTML element.
   * @param event DOM event trigger.
   */
  override def handleEvent(elem: HTMLElement, event: Event): Unit =
    HtmlId(elem.id) match
      case `BtnDemo` =>
        services.DemoManager.startDemoInPageMode { () =>
          comps.Navbar.openTourneyView(services.TourneyDB.tourney)
        }
      case `BtnOption1` =>
        Global.isLocalMode = false
        Global.isDemoMode = false
        services.TourneyDB.tourney = shared.model.Tourney.default.copy(wpId = 0)
        services.TourneyDB.version = 1
        Global.currentSelection = shared.model.Selection()
        loadPage(TourneyNew.name, "")
      case `BtnOption2` =>
        Global.isLocalMode = false
        Global.isDemoMode = false
        services.TourneyDB.tourney = shared.model.Tourney.default.copy(wpId = 0)
        services.TourneyDB.version = 1
        Global.currentSelection = shared.model.Selection()
        comps.Navbar.doQuickStart()
      case _ => debug(s"PureView handleEvent: ${elem.id}")
