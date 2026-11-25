package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import shared.basic.AppError
import shared.basic.Pickle.*
import shared.model.{ Announcement, AnnouncementAdd, AnnouncementList, MatchboardEntry, MatchboardSetRequest, MatchboardSetResponse, Tourney }
import base.{ Global, Logging }

/**
 * Durchsagen der Turnierleitung: gespeichert in WordPress (REST `announcements`, includes/api/announcement.php),
 * zusätzlich als Info-Eintrag auf dem Matchboard (Play-Server, `ann-<id>`). Nur für Cloud-Turniere.
 */
object Announcements extends ComWrapper:

  /** Höchstlänge einer Durchsage (wie TOURNEY_ANNOUNCEMENT_MAX_LEN im Plugin). */
  val MaxLen = 200

  /** Zuletzt geladene Durchsagen (neueste zuerst) und das Turnier (wpId), zu dem sie gehören. */
  private var cache: Seq[Announcement] = Seq()
  private var cacheFor: Long = 0

  /**
   * Zuletzt geladene Durchsagen eines Turniers (für die erste Anzeige, bevor neu geladen ist).
   *
   * @param t Turnier
   * @return Durchsagen, leer wenn noch keine dieses Turniers geladen sind
   */
  def cached(t: Tourney): Seq[Announcement] = if t.wpId == cacheFor then cache else Seq()

  private def keep(t: Tourney, list: Seq[Announcement]): Seq[Announcement] =
    cache = list; cacheFor = t.wpId; list

  /** Prüft, ob das Turnier in der Cloud liegt (nur dort gibt es Durchsagen). */
  def available(t: Tourney): Boolean =
    !Global.isLocalMode && !Global.isDemoMode && t.wpId > 0 && t.wpId != 999999

  private def params(t: Tourney) = List("postId" -> t.wpId.toString)

  /** Kennung des Turniers auf dem Matchboard ("<slug>-<wpId>"). */
  private def boardSlug(t: Tourney): String =
    s"${t.slug.split('/').lastOption.getOrElse(t.slug).trim}-${t.wpId}"

  /**
   * Lädt die Durchsagen.
   *
   * @param t Turnier
   * @return Durchsagen; bei Fehlern die zuletzt geladenen
   */
  def load(t: Tourney): Future[Seq[Announcement]] =
    if !available(t) then Future.successful(Seq())
    else ajaxGet[AnnouncementList]("/wp-json/tourney/v1/announcements", params(t), host = Global.homeUrl).map {
      case Right(l)  => keep(t, l.announcements)
      case Left(err) => Logging.warn(s"Announcements.load: ${err.msgCode}"); cached(t)
    }

  /**
   * Neue Durchsage speichern und auf dem Matchboard anzeigen.
   *
   * @param t    Turnier
   * @param text Text
   * @return aktuelle Durchsagen oder Fehler
   */
  def add(t: Tourney, text: String): Future[Either[AppError, Seq[Announcement]]] =
    ajaxPost[AnnouncementAdd, AnnouncementList]("/wp-json/tourney/v1/announcements", params(t), AnnouncementAdd(text), host = Global.homeUrl).map {
      case Right(l) =>
        keep(t, l.announcements).headOption.foreach(a => board(t, MatchboardEntry(id = s"ann-${a.id}", entryType = "info", text = Some(a.text))))
        Right(cached(t))
      case Left(err) => Left(err)
    }

  /**
   * Durchsage löschen, auch auf dem Matchboard.
   *
   * @param t  Turnier
   * @param id Kennung
   * @return aktuelle Durchsagen oder Fehler
   */
  def remove(t: Tourney, id: String): Future[Either[AppError, Seq[Announcement]]] =
    ajaxDelete[AnnouncementList]("/wp-json/tourney/v1/announcements", params(t) :+ ("id" -> id)).map {
      case Right(l) =>
        keep(t, l.announcements)
        board(t, MatchboardEntry(id = s"ann-$id", entryType = "delete"))
        Right(cached(t))
      case Left(err) => Left(err)
    }

  /**
   * Stellt die Durchsagen auf dem Matchboard wieder her (z. B. nach einem Neustart des Play-Servers, der seine
   * Einträge nur im Speicher hält). Vorhandene Einträge werden ersetzt.
   *
   * @param t    Turnier
   * @param list Durchsagen
   */
  def syncBoard(t: Tourney, list: Seq[Announcement]): Unit =
    list.reverse.foreach(a => board(t, MatchboardEntry(id = s"ann-${a.id}", entryType = "info", text = Some(a.text))))

  /** Schickt einen Eintrag an das Matchboard; Fehler werden nur protokolliert. */
  private def board(t: Tourney, entry: MatchboardEntry): Unit =
    ajaxPost[MatchboardSetRequest, MatchboardSetResponse]("/matchboard/set", List("slug" -> boardSlug(t)),
      MatchboardSetRequest(action = entry.entryType, tourneyName = Some(t.name), entry = Some(entry))).foreach {
      case Left(err) => Logging.warn(s"Announcements: Matchboard ${err.msgCode}")
      case Right(_)  => ()
    }
