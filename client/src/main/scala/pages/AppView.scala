package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import services.*
import base.*

/**
 * Offline-App /app/ (Website-Variante, Progressive Web App, siehe wp-plugin/includes/pwa.php):
 * zeigt Installation, Offline-Status und Speicherstatus und startet lokale Turniere im Browser,
 * auch für angemeldete Benutzer (Global.isApp, StartManager.start).
 *
 * Das Ereignis beforeinstallprompt merkt sich das Inline-Skript der Seite in window.tourneyInstallPrompt
 * und meldet Änderungen mit dem Ereignis 'tourney-installable'.
 */
object AppView extends BasePage with JsWrapper with ComWrapper:
  /** Name identifier for the page. */
  def name = PageNameTyp("AppView")

  /** HTML-ID des Install-Buttons (nur sichtbar, wenn der Browser die Installation anbietet). */
  val BtnInstall:     HtmlId = genId(name)
  /** HTML-ID des Buttons zum Fortsetzen des letzten lokalen Turniers. */
  val BtnLoadLast:    HtmlId = genId(name)
  /** HTML-ID des Buttons für das Demo-Turnier. */
  val BtnDemo:        HtmlId = genId(name)
  /** HTML-ID des Buttons für ein neues Turnier. */
  val BtnTourney:     HtmlId = genId(name)
  /** HTML-ID des Buttons für den Schnellstart (einzelner Wettbewerb). */
  val BtnCompetition: HtmlId = genId(name)
  /** HTML-ID des Hinweistexts zur Installation. */
  val InstallTextId:  HtmlId = genId(name)
  /** HTML-ID der Statuszeile Offline-Verfügbarkeit. */
  val OfflineTextId:  HtmlId = genId(name)
  /** HTML-ID der Statuszeile dauerhafter Speicher. */
  val StorageTextId:  HtmlId = genId(name)

  /** True, sobald die Listener für Installation und Service Worker registriert sind. */
  private var listening = false

  /**
   * Rendert die App-Seite und füllt die Statusbereiche.
   *
   * @param param Nicht verwendet.
   * @return Boolean true on render.
   */
  def render(param: String = ""): Boolean =
    setMain(cviews.pages.html.AppView(MainView.getLastTourneyInfo().filter(_.isLocal)))
    if (!listening) {
      listening = true
      dom.window.addEventListener("tourney-installable", (_: Event) => showInstall())
      serviceWorker.foreach(_.addEventListener("controllerchange", (_: Event) => showOffline()))
    }
    showInstall()
    showOffline()
    showStorage()
    true

  /**
   * Startet lokale Turniere, setzt das letzte lokale Turnier fort oder löst die Installation aus.
   *
   * @param elem Target HTML element.
   * @param event DOM event trigger.
   */
  override def handleEvent(elem: HTMLElement, event: Event): Unit =
    HtmlId(elem.id) match
      case `BtnInstall`     => install()
      case `BtnLoadLast`    =>
        Global.isLocalMode = true
        comps.Navbar.doReloadTourney()
      case `BtnDemo`        => StartManager.start(StartKind.Demo)
      case `BtnTourney`     => StartManager.start(StartKind.Tourney)
      case `BtnCompetition` => StartManager.start(StartKind.Competition)
      case _ => debug(s"AppView handleEvent: ${elem.id}")

  /** navigator.serviceWorker, falls der Browser Service Worker unterstützt (nur HTTPS bzw. localhost). */
  private def serviceWorker: Option[js.Dynamic] =
    val sw = dom.window.navigator.asInstanceOf[js.Dynamic].serviceWorker
    if (js.isUndefined(sw) || sw == null) None else Some(sw)

  /** Gemerktes beforeinstallprompt-Ereignis, falls der Browser die Installation anbietet. */
  private def installPrompt: Option[js.Dynamic] =
    val p = dom.window.asInstanceOf[js.Dynamic].tourneyInstallPrompt
    if (js.isUndefined(p) || p == null) None else Some(p)

  /** Zeigt den passenden Installationshinweis und den Install-Button (falls angeboten). */
  private def showInstall(): Unit =
    val text = gE(InstallTextId, withWarning = false)
    val btn  = gE(BtnInstall, withWarning = false)
    if (text != null && btn != null) {
      val iosStandalone = dom.window.navigator.asInstanceOf[js.Dynamic].standalone.asInstanceOf[js.UndefOr[Boolean]].getOrElse(false)
      val standalone = dom.window.matchMedia("(display-mode: standalone)").matches || iosStandalone
      val ios = "iPhone|iPad|iPod".r.findFirstIn(dom.window.navigator.userAgent).isDefined
      val key = if (standalone) "appView.installed"
                else if (installPrompt.isDefined) "appView.installReady"
                else if (ios) "appView.installIos"
                else "appView.installOther"
      setHtml(text, gM(key))
      if (!standalone && installPrompt.isDefined) removeClass(btn, "d-none") else addClass(btn, "d-none")
    }

  /** Öffnet den Installationsdialog des Browsers und aktualisiert danach die Anzeige. */
  private def install(): Unit =
    installPrompt.foreach { p =>
      p.prompt()
      p.userChoice.asInstanceOf[js.Promise[js.Any]].toFuture.foreach { _ =>
        dom.window.asInstanceOf[js.Dynamic].tourneyInstallPrompt = null
        showInstall()
      }
    }

  /** Zeigt, ob die App offline verfügbar ist (Seite wird vom Service Worker kontrolliert). */
  private def showOffline(): Unit =
    val elem = gE(OfflineTextId, withWarning = false)
    if (elem != null) {
      val (icon, key) = serviceWorker match
        case None                                => ("bi-x-circle text-secondary", "appView.offlineUnsupported")
        case Some(sw) if sw.controller != null   => ("bi-check-circle-fill text-success", "appView.offlineReady")
        case Some(_)                             => ("bi-hourglass-split text-secondary", "appView.offlinePreparing")
      setHtml(elem, s"""<i class="bi $icon me-1"></i>${gM(key)}""")
    }

  /** Bittet den Browser um dauerhaften Speicher (lokale Turniere) und zeigt das Ergebnis. */
  private def showStorage(): Unit =
    def show(persisted: Boolean): Unit =
      val elem = gE(StorageTextId, withWarning = false)
      if (elem != null) {
        val (icon, key) = if (persisted) ("bi-check-circle-fill text-success", "appView.storagePersisted")
                          else ("bi-exclamation-circle text-warning", "appView.storageVolatile")
        setHtml(elem, s"""<i class="bi $icon me-1"></i>${gM(key)}""")
      }
    val storage = dom.window.navigator.asInstanceOf[js.Dynamic].storage
    if (js.isUndefined(storage) || storage == null || js.isUndefined(storage.persist)) show(false)
    else storage.persist().asInstanceOf[js.Promise[Boolean]].toFuture
      .map(show)
      .recover { case _ => show(false) }
