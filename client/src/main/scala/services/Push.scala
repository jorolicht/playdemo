package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.typedarray.Uint8Array
import org.scalajs.dom
import shared.model.Tourney
import base.{ Global, Logging }

/**
 * Push-Benachrichtigungen für Durchsagen (Web Push), siehe includes/api/push.php und includes/push-sw.js.
 *
 * Ein Gerät meldet sich je Turnier an („Mein Turnier“). Der Browser hat dafür eine einzige Push-Adresse; welche Turniere
 * abonniert sind, merkt sich der Browser zusätzlich je Turnier (localStorage), damit das Abmelden eines Turniers die
 * anderen nicht betrifft. Nur für Cloud-Turniere ([[Announcements.available]]).
 */
object Push:

  /** Zustand der Benachrichtigungen eines Turniers auf diesem Gerät. */
  enum State:
    case Unsupported, Denied, Off, On

  private def nav: js.Dynamic = dom.window.navigator.asInstanceOf[js.Dynamic]
  private def win: js.Dynamic = dom.window.asInstanceOf[js.Dynamic]

  /** Prüft, ob der Browser Web Push kann (Service Worker, PushManager, Notification). */
  def supported: Boolean =
    !js.isUndefined(nav.serviceWorker) && !js.isUndefined(win.PushManager) && !js.isUndefined(win.Notification)

  /**
   * Hinweis für iPhone/iPad: Push geht dort nur in der als App installierten Seite (Home-Bildschirm, ab iOS 16.4).
   *
   * @return true auf iOS im Browser-Tab ohne Push
   */
  def iosNeedsInstall: Boolean =
    val ua = dom.window.navigator.userAgent
    (ua.contains("iPhone") || ua.contains("iPad")) && !supported

  private def home: String = Global.homeUrl.stripSuffix("/")

  /** Scope des Push-Service-Workers (steuert keine Seiten). */
  private def scope: String = new dom.URL(home + "/").pathname.stripSuffix("/") + "/tourney-push/"

  private def storageKey(t: Tourney) = s"tourney-push-${t.wpId}"

  private def remembered(t: Tourney): Boolean =
    try dom.window.localStorage.getItem(storageKey(t)) == "1" catch case _: Throwable => false

  private def remember(t: Tourney, on: Boolean): Unit =
    try if on then dom.window.localStorage.setItem(storageKey(t), "1") else dom.window.localStorage.removeItem(storageKey(t))
    catch case _: Throwable => ()

  private def permission: String = win.Notification.permission.asInstanceOf[String]

  /** Registrierung des Push-Service-Workers (wird bei Bedarf angelegt). */
  private def registration(): Future[js.Dynamic] =
    nav.serviceWorker.register(s"$home/?tourney_push=sw", js.Dynamic.literal(scope = scope))
      .asInstanceOf[js.Promise[js.Dynamic]].toFuture

  /** Vorhandene Push-Anmeldung des Browsers, falls es eine gibt. */
  private def currentSubscription(): Future[Option[js.Dynamic]] =
    nav.serviceWorker.getRegistration(scope).asInstanceOf[js.Promise[js.UndefOr[js.Dynamic]]].toFuture.flatMap { reg =>
      reg.toOption match
        case Some(r) => r.pushManager.getSubscription().asInstanceOf[js.Promise[js.Dynamic]].toFuture.map(s => Option(s).filterNot(js.isUndefined(_)))
        case None    => Future.successful(None)
    }

  /**
   * Zustand für ein Turnier.
   *
   * @param t Turnier
   * @return Zustand (ohne Unterstützung bzw. Cloud-Turnier: Unsupported)
   */
  def state(t: Tourney): Future[State] =
    if !supported || !Announcements.available(t) then Future.successful(State.Unsupported)
    else if permission == "denied" then Future.successful(State.Denied)
    else if !remembered(t) then Future.successful(State.Off)
    else currentSubscription().map(s => if s.isDefined then State.On else State.Off).recover { case _ => State.Off }

  /** Öffentlicher VAPID-Schlüssel (Base64url) als Bytes für pushManager.subscribe. */
  private def keyBytes(b64: String): Uint8Array =
    val pad = "=" * ((4 - b64.length % 4) % 4)
    val raw = dom.window.atob((b64 + pad).replace('-', '+').replace('_', '/'))
    new Uint8Array(raw.map(_.toShort).toJSArray)

  /** POST an die Push-Endpunkte (JSON, ohne Anmeldung). */
  private def post(route: String, t: Tourney, body: js.Any): Future[Boolean] =
    val init = js.Dynamic.literal(method = "POST", headers = js.Dynamic.literal("Content-Type" -> "application/json"),
                                  body = js.JSON.stringify(body)).asInstanceOf[dom.RequestInit]
    dom.fetch(s"$home/wp-json/tourney/v1/$route?postId=${t.wpId}", init).toFuture.map(_.ok)

  /**
   * Benachrichtigungen für ein Turnier einschalten: Erlaubnis erfragen, Push-Adresse holen bzw. anlegen, beim Turnier
   * anmelden.
   *
   * @param t Turnier
   * @return neuer Zustand
   */
  def enable(t: Tourney): Future[State] =
    win.Notification.requestPermission().asInstanceOf[js.Promise[String]].toFuture.flatMap {
      case "granted" =>
        for
          reg  <- registration()
          _    <- nav.serviceWorker.ready.asInstanceOf[js.Promise[js.Any]].toFuture
          key  <- dom.fetch(s"$home/wp-json/tourney/v1/push-key").toFuture.flatMap(_.json().toFuture)
          sub  <- reg.pushManager.getSubscription().asInstanceOf[js.Promise[js.Dynamic]].toFuture.flatMap { s =>
                    if s != null && !js.isUndefined(s) then Future.successful(s)
                    else reg.pushManager.subscribe(js.Dynamic.literal(userVisibleOnly = true,
                           applicationServerKey = keyBytes(key.asInstanceOf[js.Dynamic].publicKey.asInstanceOf[String])))
                           .asInstanceOf[js.Promise[js.Dynamic]].toFuture
                  }
          ok   <- post("push-subscribe", t, js.Dynamic.literal(subscription = sub.toJSON(), lang = Global.lang))
        yield
          remember(t, ok)
          if ok then State.On else State.Off
      case "denied" => Future.successful(State.Denied)
      case _        => Future.successful(State.Off)
    }.recover { case e: Throwable => Logging.warn(s"Push.enable: ${e.getMessage}"); State.Off }

  /**
   * Benachrichtigungen für ein Turnier ausschalten (die Push-Adresse des Browsers bleibt für andere Turniere erhalten).
   *
   * @param t Turnier
   * @return neuer Zustand
   */
  def disable(t: Tourney): Future[State] =
    remember(t, false)
    currentSubscription().flatMap {
      case Some(s) => post("push-unsubscribe", t, js.Dynamic.literal(endpoint = s.endpoint)).map(_ => State.Off)
      case None    => Future.successful(State.Off)
    }.recover { case _ => State.Off }

  private var listening = false

  /**
   * Meldungen des Service Workers empfangen: neue Durchsage ("tourney-announcement") bzw. Klick auf eine Benachrichtigung
   * bei offener Seite ("tourney-open-mytourney"). Wird einmal eingerichtet.
   *
   * @param onAnnouncement erhält die Post-ID des Turniers
   * @param onOpen         wird bei einem Klick auf die Benachrichtigung aufgerufen
   */
  def listen(onAnnouncement: Long => Unit, onOpen: () => Unit): Unit =
    if supported && !listening then
      listening = true
      nav.serviceWorker.addEventListener("message", { (e: dom.MessageEvent) =>
        val d = e.data.asInstanceOf[js.Dynamic]
        if !js.isUndefined(d) && d != null then
          d.selectDynamic("type").asInstanceOf[js.UndefOr[String]].toOption match
            case Some("tourney-announcement")   => onAnnouncement(d.postId.asInstanceOf[Double].toLong)
            case Some("tourney-open-mytourney") => onOpen()
            case _                              => ()
      }: js.Function1[dom.MessageEvent, Unit])
