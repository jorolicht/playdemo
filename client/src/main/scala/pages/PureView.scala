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
   * Starts the corresponding action via StartManager, which saves the tournament
   * into the current WordPress page (with overwrite confirmation) or shows an error without write access.
   *
   * @param elem Target HTML element.
   * @param event DOM event trigger.
   */
  override def handleEvent(elem: HTMLElement, event: Event): Unit =
    HtmlId(elem.id) match
      case `BtnDemo`    => StartManager.start(StartKind.Demo)
      case `BtnOption1` => StartManager.start(StartKind.Tourney)
      case `BtnOption2` => StartManager.start(StartKind.Competition)
      case _ => debug(s"PureView handleEvent: ${elem.id}")
