package shared.model

import shared.basic.Pickle.{ReadWriter, given}

/**
 * Durchsage der Turnierleitung (z. B. „Siegerehrung Herren A um 16 Uhr“), gespeichert in WordPress
 * (includes/api/announcement.php), angezeigt in „Mein Turnier“ und auf dem Matchboard.
 *
 * @param id   Kennung (vom Server vergeben)
 * @param text Text der Durchsage
 * @param ts   Zeitpunkt "JJJJ-MM-TT HH:MM" (Ortszeit des Servers)
 */
case class Announcement(id: String, text: String, ts: String) derives ReadWriter:
  /** Uhrzeit "HH:MM" für die Anzeige. */
  def time: String = ts.drop(11).take(5)

/** Antwort der REST-Endpunkte `announcements`. */
case class AnnouncementList(announcements: Seq[Announcement] = Seq()) derives ReadWriter

/** Body für eine neue Durchsage. */
case class AnnouncementAdd(text: String) derives ReadWriter
