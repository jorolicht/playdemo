package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import shared.basic.Pickle.*
import shared.BoxButton
import shared.PageNameTyp
import base.{Global, Logging}
import base.Messages.gM

/**
 * Art der Neuanlage auf MainView/PureView.
 */
enum StartKind:
  case Demo, Tourney, Competition

/**
 * Einheitliche Neuanlage von Demo-Turnier, Turnier und Wettbewerb (Schnellstart).
 *
 * Das Ziel ergibt sich aus Plugin-Variante und Benutzer:
 *  - fws=true, angemeldet:      in der Cloud (Demo: Bestätigung, falls es dort schon existiert);
 *                               ohne Recht zum Anlegen (z. B. Abonnent): Fehlermeldung
 *  - fws=true, nicht angemeldet: lokal im Browser (Bestätigung, falls schon ein lokales Turnier gespeichert ist)
 *  - fws=false, mit Schreibrecht auf die Seite: in der aktuellen Seite (Bestätigung, falls dort schon ein Turnier gespeichert ist)
 *  - fws=false, ohne Schreibrecht: Fehlermeldung
 */
object StartManager extends ComWrapper:

  /** Antwort von /read; tourney ist null, wenn auf dem Post kein Turnier gespeichert ist. */
  case class PageReadResponse(version: Int, tourney: ujson.Value) derives ReadWriter

  /**
   * Startet die Neuanlage.
   *
   * @param kind Demo-Turnier, Turnier oder Wettbewerb (Schnellstart).
   */
  def start(kind: StartKind): Unit =
    if (!Global.fws) startInPage(kind)
    else if (Global.user.isDefined) startInCloud(kind)
    else startLocal(kind)

  /** fws=false: Anlage in der aktuellen WordPress-Seite. */
  private def startInPage(kind: StartKind): Unit =
    if (!Global.canEditPage || Global.hostPageId <= 0) {
      dialogs.DlgMsgbox.show(gM("startNew.noRights"), gM("startNew.noRightsTitle"), List(BoxButton.Ok))
    } else {
      pageHasTourney().flatMap { exists =>
        if (exists) confirm(gM("startNew.overwritePage")) else Future.successful(true)
      }.foreach { ok =>
        if (ok) kind match {
          case StartKind.Demo => startDemoOnServer(overwrite = false)
          case _              => openCreatePage(kind)
        }
      }
    }

  /** fws=true und angemeldet: Anlage in der Cloud (nur mit Recht zum Anlegen). */
  private def startInCloud(kind: StartKind): Unit =
    if (!Global.canCreateTourney) {
      dialogs.DlgMsgbox.show(gM("startNew.noCreateRights"), gM("startNew.noRightsTitle"), List(BoxButton.Ok))
    } else kind match {
      case StartKind.Demo =>
        DemoManager.startDemoOnServer().foreach {
          case Left(err) if err.is("tourney_already_exists") =>
            confirm(gM("startNew.overwriteCloud")).foreach { ok => if (ok) startDemoOnServer(overwrite = true) }
          case res => afterDemo(res)
        }
      case _ => openCreatePage(kind)
    }

  /** fws=true und nicht angemeldet: Anlage lokal im Browser. */
  private def startLocal(kind: StartKind): Unit =
    val confirmed =
      if (!TourneyDB.hasLocalTourney) Future.successful(true)
      else kind match {
        case StartKind.Demo        => dialogs.DlgStartDemo.show()
        case StartKind.Tourney     => dialogs.DlgStartTourney.show()
        case StartKind.Competition => dialogs.DlgStartCompetition.show()
      }
    confirmed.foreach { ok =>
      if (ok) kind match {
        case StartKind.Demo =>
          DemoManager.startDemoInLocalMode { () =>
            Global.currentSelection = shared.model.Selection(Some(TourneyDB.tourney))
            comps.ContextHeader.render()
            comps.Navbar.render()
            pages.loadPage(PageNameTyp("TourneyInfo"), "")
          }
        case StartKind.Tourney     => DemoManager.startLocalMode(() => pages.loadPage(PageNameTyp("TourneyNew"), ""))
        case StartKind.Competition => DemoManager.startLocalMode(() => comps.Navbar.doQuickStart())
      }
    }

  /** Legt das Demo-Turnier in Cloud bzw. Seite an und öffnet es. */
  private def startDemoOnServer(overwrite: Boolean): Unit =
    DemoManager.startDemoOnServer(overwrite).foreach(afterDemo)

  /** Öffnet das angelegte Demo-Turnier oder zeigt den Fehler an. */
  private def afterDemo(res: Either[shared.basic.AppError, Unit]): Unit =
    res match {
      case Right(_)  => comps.Navbar.openTourneyView(TourneyDB.tourney)
      case Left(err) => dialogs.DlgMsgbox.show(gM("startNew.demoFailed", err.msgCode), gM("startNew.demoFailedTitle"), List(BoxButton.Ok))
    }

  /** Setzt ein leeres Server-Turnier (Cloud bzw. Seite) und öffnet die Anlage-Seite für Turnier oder Schnellstart. */
  private def openCreatePage(kind: StartKind): Unit =
    Global.isLocalMode = false
    Global.isDemoMode = false
    TourneyDB.tourney = shared.model.Tourney.default.copy(wpId = 0)
    TourneyDB.version = 1
    Global.currentSelection = shared.model.Selection()
    if (kind == StartKind.Tourney) pages.loadPage(PageNameTyp("TourneyNew"), "")
    else comps.Navbar.doQuickStart()

  /** Prüft, ob auf der aktuellen WordPress-Seite bereits ein Turnier gespeichert ist. */
  private def pageHasTourney(): Future[Boolean] =
    ajaxGet[PageReadResponse]("/wp-json/tourney/v1/read", List("postId" -> Global.hostPageId.toString), host = Global.homeUrl).map {
      case Right(res) => !res.tourney.isNull
      case Left(err) =>
        // Im Zweifel nachfragen statt ohne Bestätigung zu überschreiben
        Logging.debug(s"StartManager.pageHasTourney: ${err.msgCode}")
        true
    }

  /** Zeigt eine Ja/Nein-Bestätigung zum Überschreiben an. */
  private def confirm(msg: String): Future[Boolean] =
    dialogs.DlgMsgbox.show(msg, gM("startNew.overwriteTitle"), List(BoxButton.Yes, BoxButton.No)).map(_ == BoxButton.Yes)
