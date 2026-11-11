package shared.sport

import shared.basic.Log

/**
 * Versionierte Wertungssysteme der Sportarten.
 *
 * Jede Version ist eine eigene, nach Veröffentlichung unveränderliche Datei `shared/sports/<sport>_<nnn>.json`
 * (z. B. `tt_001.json`, Inhalt `sport.id` = "TT", `sport.version` = 1). Ihr Schlüssel ist der Dateiname ohne Endung
 * ("tt_001"). Jeder Wettbewerb speichert den Schlüssel seines Wertungssystems (`Competition.scoring`) und wird immer
 * danach gewertet; neue Regeln kommen als neue Version hinzu und gelten nur für neu angelegte Wettbewerbe.
 *
 * Die beim Übersetzen eingebetteten Versionen ([[SportFiles]]) sind immer vorhanden. Zur Laufzeit können mit [[load]]
 * weitere (neuere) Versionen ergänzt werden; eingebettete Versionen lassen sich nicht überschreiben.
 *
 * Gerechnet wird im Kontext eines Wertungssystems: [[using]] setzt es für einen Block (z. B. die Methoden einer
 * Stage), [[current]] liefert es; ohne Kontext gilt [[fallbackKey]] (im Client der ausgewählte Wettbewerb).
 */
object Sports:

  /** Kennung der Standard-Sportart. */
  val DefaultId = "TT"

  /** Wertungssystem der Wettbewerbe ohne Eintrag (vor Einführung der Versionen angelegt). */
  val LegacyKey = "tt_001"

  /**
   * Schlüssel einer Konfiguration aus Sportart und Version.
   *
   * @param cfg Konfiguration
   * @return z. B. "tt_001"
   */
  def keyOf(cfg: SportConfig): String = f"${cfg.sport.id.toLowerCase}_${cfg.sport.version}%03d"

  /**
   * Prüft eine Konfiguration und ihren Schlüssel (Dateiname ohne Endung).
   *
   * @param key  erwarteter Schlüssel
   * @param json Inhalt
   * @return Konfiguration oder Fehlertext
   */
  private def parseKeyed(key: String, json: String): Either[String, SportConfig] =
    SportConfig.parse(json).flatMap { cfg =>
      if cfg.sport.version <= 0 then Left("sport.version missing")
      else if keyOf(cfg) != key then Left(s"file name '${key}' does not match sport.id/sport.version ('${keyOf(cfg)}')")
      else Right(cfg)
    }

  /** Beim Übersetzen eingebettete Versionen, Schlüssel z. B. "tt_001". */
  lazy val embedded: Map[String, SportConfig] =
    SportFiles.files.flatMap { case (file, json) =>
      val key = file.stripSuffix(".json")
      parseKeyed(key, json) match
        case Right(cfg) => Some(key -> cfg)
        case Left(err)  => Log.error(s"Sports: ${file}: ${err}"); None
    }.toMap

  /** Zur Laufzeit ergänzte Versionen. */
  private var loaded: Map[String, SportConfig] = Map.empty

  /** Alle bekannten Versionen, Schlüssel z. B. "tt_001". */
  def all: Map[String, SportConfig] = loaded ++ embedded

  /**
   * Schlüssel aller Versionen einer Sportart, aufsteigend nach Version.
   *
   * @param sportId Kennung der Sportart
   * @return z. B. Seq("tt_001", "tt_002")
   */
  def keys(sportId: String = DefaultId): Seq[String] =
    all.values.filter(_.sport.id == sportId).toSeq.sortBy(_.sport.version).map(keyOf)

  /**
   * Schlüssel der neuesten Version einer Sportart; neue Wettbewerbe erhalten diese Version.
   *
   * @param sportId Kennung der Sportart
   * @return z. B. "tt_002", ohne Versionen [[LegacyKey]]
   */
  def latestKey(sportId: String = DefaultId): String = keys(sportId).lastOption.getOrElse(LegacyKey)

  /**
   * Schlüssel eines Wettbewerbs ohne Eintrag ergänzen.
   *
   * @param key gespeicherter Schlüssel (leer = vor Einführung der Versionen angelegt)
   * @return Schlüssel
   */
  def resolveKey(key: String): String = if key.trim.isEmpty then LegacyKey else key.trim

  /**
   * Ist die Version bekannt?
   *
   * @param key Schlüssel (leer = [[LegacyKey]])
   * @return true, wenn eingebettet oder geladen
   */
  def isKnown(key: String): Boolean = all.contains(resolveKey(key))

  /**
   * Konfiguration einer Version.
   *
   * @param key Schlüssel (leer = [[LegacyKey]])
   * @return Konfiguration; ist die Version unbekannt (z. B. Turnier aus neuerer Installation), die neueste Version
   *         derselben Sportart
   */
  def get(key: String): SportConfig =
    val k = resolveKey(key)
    all.getOrElse(k, {
      val sportId = k.takeWhile(_ != '_').toUpperCase
      val alt     = latestKey(if keys(sportId).nonEmpty then sportId else DefaultId)
      Log.error(s"Sports: unknown scoring '${k}', using '${alt}'")
      all(alt)
    })

  /** Neueste Version der Standard-Sportart. */
  def latest: SportConfig = get(latestKey())

  // --- Kontext ---------------------------------------------------------------------------------------------------

  /** Stapel der mit [[using]] gesetzten Schlüssel (innerster zuerst). */
  private var scope: List[String] = Nil

  /** Schlüssel ohne gesetzten Kontext; der Client liefert hier das Wertungssystem des ausgewählten Wettbewerbs. */
  var fallbackKey: () => String = () => ""

  /** Schlüssel des aktuellen Kontexts. */
  def currentKey: String = resolveKey(scope.headOption.getOrElse(fallbackKey()))

  /** Konfiguration des aktuellen Kontexts. */
  def current: SportConfig = get(currentKey)

  /**
   * Führt einen Block im Kontext eines Wertungssystems aus (verschachtelbar).
   *
   * @param key  Schlüssel (leer = [[LegacyKey]])
   * @param body Block
   * @return Ergebnis des Blocks
   */
  def using[T](key: String)(body: => T): T =
    scope = resolveKey(key) :: scope
    try body finally scope = scope.drop(1)

  // --- Laden zur Laufzeit ----------------------------------------------------------------------------------------

  /** Namen der eingebetteten Dateien (z. B. "tt_001.json"). */
  def fileNames: Seq[String] = SportFiles.files.map(_._1)

  /**
   * Dateiname, unter dem eine Version im Plugin-Verzeichnis `data/` ausgeliefert wird.
   *
   * @param fileName Name der Datei in `shared/sports` (z. B. "tt_001.json")
   * @return z. B. "sport_tt_001.json"
   */
  def dataFileName(fileName: String): String = s"sport_${fileName}"

  /** Name der Indexdatei in `data/` mit allen Dateinamen (JSON-Array). */
  val IndexFileName = "sport_index.json"

  /**
   * Ergänzt eine zur Laufzeit gelesene Version; eingebettete Versionen bleiben unverändert.
   *
   * @param fileName Dateiname in `shared/sports` (z. B. "tt_002.json")
   * @param json     Inhalt
   * @return Schlüssel oder Fehlertext
   */
  def load(fileName: String, json: String): Either[String, String] =
    val key = fileName.stripSuffix(".json")
    if embedded.contains(key) then Left(s"${key} is embedded and cannot be replaced")
    else parseKeyed(key, json) match
      case Right(cfg) =>
        loaded = loaded + (key -> cfg)
        Right(key)
      case Left(err) =>
        Log.error(s"Sports: ${fileName}: ${err}")
        Left(err)

  /** Verwirft alle zur Laufzeit ergänzten Versionen. */
  def reset(): Unit = loaded = Map.empty
