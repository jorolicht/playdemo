package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.control.NonFatal

import org.scalajs.dom
import shared.basic.Pickle.*
import shared.basic.AppError
import shared.PageNameTyp
import base.{Global, Logging}
import base.Messages.gM

/**
 * Multiuser-Betrieb (Variante A, siehe doc/MultiUser.md): WordPress bleibt die maßgebliche Datenhaltung.
 *
 * - Nach jedem erfolgreichen Speichern meldet der Client die Änderung über den WebSocket des Turniers
 *   (`/ws/<slug>-<wpId>`, Meldung `SYNC:<json>`); der Play-Server reicht sie an alle anderen Clients weiter.
 * - Empfänger laden die betroffenen Daten aus WordPress nach, aktualisieren die Ansicht (ohne bereits eingegebene,
 *   noch nicht gespeicherte Werte zu verlieren) und zeigen einen Hinweis „<Name>: <Änderung>“.
 * - Konflikte bei Ergebnissen führt [[StageDB]] je Spiel zusammen („letzte Eingabe gewinnt“ mit Hinweis).
 * - Die Ergebnisse werden in WordPress protokolliert (Tabelle tourney_changelog), Anzeige in TourneyAdmin.
 *
 * Nur auf der Website turnier-service.info (Cloud-Turniere), nicht im Embed-Plugin, Demo- oder Lokalmodus.
 */
object MultiUser extends ComWrapper:

  /** Präfix der Änderungsmeldungen auf dem WebSocket (wie `WebSocketClientActor.SyncPrefix` im Server). */
  val SyncPrefix = "SYNC:"

  /**
   * Änderungsmeldung an die anderen Bearbeiter.
   *
   * @param kind Art der geänderten Daten: "stage", "competition", "player", "club" oder "tourney"
   * @param by   Name des Bearbeiters
   * @param text Kurzbeschreibung der Änderung, z. B. "Herren A – Gruppen: Spiel 12 Müller – Meier 3:1"
   */
  case class ChangeNote(kind: String, by: String, text: String) derives ReadWriter

  /**
   * Eintrag des Änderungsprotokolls (GET tourney/v1/changelog).
   *
   * @param ts    Zeitpunkt (Zeitzone von WordPress, "JJJJ-MM-TT hh:mm:ss")
   * @param user  Anzeigename des Bearbeiters
   * @param obj   Objektart, z. B. "stage"
   * @param objId ID des Objekts
   * @param text  Beschreibung der Änderung
   */
  case class ChangeLogEntry(ts: String, user: String, obj: String, objId: Int, text: String) derives ReadWriter
  case class ChangeLogResponse(entries: Seq[ChangeLogEntry]) derives ReadWriter

  /** Seiten, die nach einer Änderung eines anderen Bearbeiters neu angezeigt werden. */
  private val refreshPages = Set("StageInput", "StageResult", "CompetitionInfo", "ResultList", "TourneyInfo",
                                 "PlayerList", "TeamList")

  /** Wartende Aktualisierung der Ansicht (Dialog offen), siehe [[refreshView]]. */
  private var refreshTimer: Option[Int] = None

  /**
   * Ist der Multiuser-Betrieb aktiv? Nur auf turnier-service.info (Website-Variante) für Cloud-Turniere.
   *
   * @return true, wenn Änderungen gemeldet, zusammengeführt und protokolliert werden
   */
  def enabled: Boolean = Global.usePlayServer && TourneyDB.tourney.wpId > 0 && TourneyDB.tourney.wpId != 999999

  /**
   * Anzeigename des angemeldeten Benutzers für Meldungen an andere Bearbeiter.
   *
   * @return "Vorname Nachname", sonst Benutzername, sonst "?"
   */
  def userName: String =
    Global.user.map { u =>
      val full = s"${u.firstname} ${u.lastname}".trim
      if (full.nonEmpty) full else u.username
    }.filter(_.nonEmpty).getOrElse("?")

  /**
   * Meldet eine gespeicherte Änderung an die anderen Bearbeiter des Turniers.
   *
   * @param kind Art der geänderten Daten, siehe [[ChangeNote]]
   * @param text Kurzbeschreibung der Änderung
   */
  def notifyChange(kind: String, text: String): Unit =
    if (enabled) WebSocketService.send(SyncPrefix + write(ChangeNote(kind, userName, text.take(300))))

  /**
   * Verarbeitet eine Änderungsmeldung eines anderen Bearbeiters: Daten aus WordPress nachladen, Auswahl und
   * Ansicht aktualisieren, Hinweis anzeigen.
   *
   * @param payload JSON der [[ChangeNote]] (ohne Präfix)
   */
  def onRemoteChange(payload: String): Unit =
    if (enabled) {
      try {
        val note = read[ChangeNote](payload)
        val reload: Future[Any] = note.kind match
          case "stage"       => StageDB.refresh()
          case "competition" => if (CompetitionDB.hasPending) Future.unit else CompetitionDB.load()
          case "player"      => if (PlayerDB.hasPending) Future.unit else PlayerDB.load()
          case "club"        => if (ClubDB.hasPending) Future.unit else ClubDB.load()
          case "tourney"     => TourneyDB.refreshBasic()
          case _             => Future.unit
        // ohne Text (nur neue Version der Turnierdaten): still nachladen
        reload.foreach { _ =>
          updateSelection()
          if (note.text.nonEmpty) {
            refreshView()
            showToast(s"${note.by}: ${note.text}")
          }
        }
      } catch {
        case NonFatal(ex) => Logging.debug(s"[MultiUser] invalid change note: ${ex.getMessage}")
      }
    }

  /**
   * Ersetzt Turnier, Wettbewerb und Stage der aktuellen Auswahl durch die neu geladenen Objekte (gleiche ID).
   */
  def updateSelection(): Unit =
    val sel = Global.currentSelection
    if (sel.tourney.isDefined) {
      val t = TourneyDB.tourney
      Global.currentSelection = sel.copy(
        tourney     = Some(t),
        competition = sel.competition.flatMap(c => t.competitions.find(x => x != null && x.id == c.id)).orElse(sel.competition),
        stage       = sel.stage.flatMap(s => t.stages.find(x => x != null && x.id == s.id)).orElse(sel.stage)
      )
    }

  /**
   * Zeigt die aktuelle Seite mit den neuen Daten erneut an, falls sie betroffen sein kann. Bereits eingegebene,
   * noch nicht gespeicherte Werte (z. B. Satzergebnisse) und der Eingabefokus bleiben erhalten. Ist ein Dialog
   * geöffnet, wird die Aktualisierung verschoben.
   */
  def refreshView(): Unit =
    val page = Global.activePageName
    if (refreshPages.contains(page)) {
      refreshTimer.foreach(dom.window.clearTimeout)
      refreshTimer = None
      if (dom.document.querySelector(".modal.show") != null) {
        refreshTimer = Some(dom.window.setTimeout(() => refreshView(), 1000))
      } else {
        // vom Benutzer geänderte, noch nicht gespeicherte Eingaben merken (Wert, angezeigter Ausgangswert)
        val inputs = dom.document.querySelectorAll("input[id]")
        val edited = (0 until inputs.length).map(i => inputs(i).asInstanceOf[dom.html.Input])
          .filter(el => el.`type` != "checkbox" && el.`type` != "radio" && el.value != el.defaultValue)
          .map(el => (el.id, el.value, el.defaultValue))
        val focusId = Option(dom.document.activeElement).map(_.id).filter(_.nonEmpty)

        val param = try dom.window.sessionStorage.getItem("tourney_last_param") catch { case NonFatal(_) => null }
        pages.pagesMap.get(PageNameTyp(page)).foreach(_.render(if (param == null) "" else param))

        // nur zurückschreiben, wenn der gespeicherte Wert unverändert ist (sonst gilt der neue Stand)
        edited.foreach { case (id, value, shown) =>
          val el = dom.document.getElementById(id).asInstanceOf[dom.html.Input]
          if (el != null && !el.disabled && !el.readOnly && el.value == shown && el.value != value) {
            el.value = value
            el.dispatchEvent(new dom.Event("input", new dom.EventInit { bubbles = true }))
          }
        }
        focusId.foreach { id =>
          val el = dom.document.getElementById(id).asInstanceOf[dom.html.Element]
          if (el != null) el.focus()
        }
      }
    }

  /**
   * Zeigt unten rechts einen Hinweis, der nach einigen Sekunden verschwindet.
   *
   * @param text Hinweistext (wird als Text, nicht als HTML eingefügt)
   */
  def showToast(text: String): Unit =
    var box = dom.document.getElementById("mu-toast-container").asInstanceOf[dom.html.Element]
    if (box == null) {
      box = dom.document.createElement("div").asInstanceOf[dom.html.Div]
      box.id = "mu-toast-container"
      box.className = "toast-container position-fixed bottom-0 end-0 p-3"
      box.setAttribute("style", "z-index: 1090")
      dom.document.body.appendChild(box)
    }
    val toast = dom.document.createElement("div").asInstanceOf[dom.html.Element]
    toast.className = "toast show align-items-center text-bg-dark border-0 mu-toast"
    toast.setAttribute("role", "status")
    toast.setAttribute("aria-live", "polite")
    val body = dom.document.createElement("div").asInstanceOf[dom.html.Element]
    body.className = "toast-body"
    val icon = dom.document.createElement("i").asInstanceOf[dom.html.Element]
    icon.className = "bi bi-people-fill me-2"
    icon.setAttribute("aria-hidden", "true")
    body.appendChild(icon)
    body.appendChild(dom.document.createTextNode(text))
    toast.appendChild(body)
    box.appendChild(toast)
    dom.window.setTimeout(() => if (toast.parentNode != null) toast.parentNode.removeChild(toast), 8000)

  /**
   * Lädt das Änderungsprotokoll des aktuellen Turniers (neueste Einträge zuerst).
   *
   * @return Einträge oder Fehler
   */
  def loadChangelog(): Future[Either[AppError, Seq[ChangeLogEntry]]] =
    val params = List("postId" -> TourneyDB.tourney.wpId.toString, "limit" -> "300")
    ajaxGet[ChangeLogResponse]("/wp-json/tourney/v1/changelog", params, hdrs = Map("X-WP-Nonce" -> Global.wpNonce),
                               host = Global.homeUrl).map(_.map(_.entries))
