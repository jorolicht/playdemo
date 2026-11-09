package shared.sport

import shared.basic.Log

/**
 * Zugriff auf die Wertungssysteme der Sportarten. Die Konfigurationen stammen aus `shared/sports/` und werden beim
 * Übersetzen in [[SportFiles]] eingebettet, damit Client und Server dieselbe Datei synchron lesen.
 */
object Sports:

  /** Kennung der Standard-Sportart. */
  val DefaultId = "TT"

  /** Alle gültigen Konfigurationen, Schlüssel ist `sport.id`. */
  lazy val all: Map[String, SportConfig] =
    SportFiles.files.flatMap { case (file, json) =>
      SportConfig.parse(json) match
        case Right(cfg) => Some(cfg.sport.id -> cfg)
        case Left(err)  => Log.error(s"Sports: ${file}: ${err}"); None
    }.toMap

  /**
   * Konfiguration einer Sportart.
   *
   * @param id Kennung der Sportart (z. B. "TT")
   * @return Konfiguration, bei unbekannter Kennung die Standard-Sportart
   */
  def get(id: String): SportConfig = all.getOrElse(id, default)

  /** Standard-Sportart (Tischtennis). Heute gelten ihre Regeln für alle Wettbewerbe. */
  lazy val default: SportConfig = all(DefaultId)
