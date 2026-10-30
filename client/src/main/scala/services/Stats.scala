package services

import org.scalajs.dom
import base.Global

/**
 * Anonyme Nutzungsstatistik: meldet Ereignisse an den WordPress-Endpunkt tourney/v1/stats-hit.
 *
 * Ereignisse: home (Homepage-Aufruf), demo, comp, tourney (Klick auf die Start-Buttons).
 * Gezählt wird serverseitig nur ein Tageszähler pro Ereignis (Auswertung per Shortcode [tourney_stats]).
 */
object Stats:

  /**
   * Meldet ein Ereignis per navigator.sendBeacon (fire-and-forget, auch beim Seitenwechsel zuverlässig).
   * Fehler werden ignoriert, die Statistik darf die App nie stören.
   *
   * @param event Ereignis-Schlüssel (home, demo, comp, tourney).
   */
  def track(event: String): Unit =
    try dom.window.navigator.sendBeacon(s"${Global.homeUrl}/wp-json/tourney/v1/stats-hit?e=$event", "")
    catch case _: Throwable => ()

  /**
   * Meldet ein Ereignis nur einmal pro Browsersitzung (Merker im sessionStorage).
   *
   * @param event Ereignis-Schlüssel.
   */
  def trackOncePerSession(event: String): Unit =
    val key = s"tourney_stats_$event"
    try
      val storage = dom.window.sessionStorage
      if (storage.getItem(key) == null) {
        storage.setItem(key, "1")
        track(event)
      }
    catch case _: Throwable => track(event)
