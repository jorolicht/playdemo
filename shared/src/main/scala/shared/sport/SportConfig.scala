package shared.sport

import shared.basic.Pickle.{ReadWriter => RW, macroRW, *}
import upickle.implicits.key

/**
 * Name einer Wertungsstufe, Sportart, Variante oder eines Formats in einer Sprache.
 *
 * @param one   Singular (z. B. "Satz")
 * @param other Plural (z. B. "Sätze"), leer = wie Singular
 * @param short Kurzform für Tabellen und Anzeigetafeln (z. B. "S")
 */
case class Name(one: String, other: String = "", short: String = "")

object Name:
  given rw: RW[Name] = macroRW

/**
 * Namen je Sprache (Schlüssel "de", "en" …).
 */
type Names = Map[String, Name]

extension (names: Names)
  /**
   * Liefert den Namen in der gewünschten Sprache mit Rückfall auf "en", dann "de".
   *
   * @param lang Sprachkürzel
   * @return Name, oder ein leerer Name, wenn keine Sprache vorhanden ist
   */
  def localized(lang: String): Name =
    names.get(lang).orElse(names.get("en")).orElse(names.get("de")).getOrElse(Name(""))


/**
 * Wertungsstufe (z. B. Ball, Satz, Spiel). Welche Parameter gelten, bestimmt `kind`:
 *
 *  - `atomic`: kleinste gezählte Einheit, keine Regel
 *  - `race`:   gewonnen, wer zuerst `target` Einheiten hat und `winBy` vorn liegt; optional Obergrenze `cap`
 *  - `bestOf`: gewonnen, wer zuerst `toWin` Einheiten hat
 *  - `fixed`:  genau `count` Einheiten werden gespielt, Sieger nach Summe
 *  - `sum`:    Sieger nach Summe der Einheiten
 *
 * Nicht modellierte Felder des Entwurfs (`deciding`, `tieBreak`, `periods`, `display`) werden beim Lesen
 * ignoriert.
 *
 * @param id        Kennung der Stufe
 * @param kind      Stufenart (atomic, race, bestOf, fixed, sum)
 * @param unit      Kennung der Stufe darunter (leer bei `atomic`)
 * @param name      Namen je Sprache
 * @param target    `race`: Zielwert
 * @param winBy     `race`: nötiger Vorsprung
 * @param cap       `race`: Obergrenze, bei der der nächste Punkt entscheidet
 * @param toWin     `bestOf`: Anzahl der nötigen Gewinneinheiten
 * @param count     `fixed`: Anzahl der gespielten Einheiten
 * @param allowDraw `fixed`/`sum`: Unentschieden möglich; `bestOf`: Unentschieden bei (toWin-1):(toWin-1), wenn alle
 *                  Einheiten gespielt sind (z. B. 7:7 bei 14 Spielen bis 8 Punkte)
 * @param decider   `fixed`: bei Gleichstand nach `count` Einheiten entscheidet eine weitere Einheit
 *                  (Begegnung: Entscheidungsspiel, siehe [[EncounterGame.decider]])
 */
case class Level(
  id:        String,
  kind:      String,
  unit:      String       = "",
  name:      Names        = Map(),
  target:    Int          = 0,
  winBy:     Int          = 1,
  cap:       Option[Int]  = None,
  toWin:     Int          = 0,
  count:     Int          = 0,
  allowDraw: Boolean      = false,
  decider:   Boolean      = false
):

  /**
   * Prüft, ob ein Stand (a:b) ein gültiges, abgeschlossenes Ergebnis dieser Stufe ist.
   *
   * @param score Stand aus Sicht von Seite A
   * @return true, wenn das Ergebnis gültig ist
   */
  def isValid(score: (Int, Int)): Boolean =
    val (a, b) = score
    if a < 0 || b < 0 then false
    else kind match
      case Level.Race =>
        val (hi, lo) = if a >= b then (a, b) else (b, a)
        (hi == target && lo <= target - winBy) ||
        (hi > target && hi - lo == winBy && cap.forall(hi <= _)) ||
        cap.exists(c => hi == c && lo == c - 1)
      case Level.BestOf =>
        (a == toWin && b < toWin) || (b == toWin && a < toWin) || (allowDraw && a == toWin - 1 && b == toWin - 1)
      case Level.Fixed  =>
        (a + b == count && (allowDraw || a != b)) ||
        (decider && a + b == count + 1 && math.abs(a - b) == 1)
      case Level.Sum    => allowDraw || a != b
      case _            => false

  /**
   * Ausgang eines gültigen Ergebnisses aus Sicht von Seite A.
   *
   * @param score Stand aus Sicht von Seite A
   * @return 1 Sieg, -1 Niederlage, 0 Unentschieden oder ungültig
   */
  def outcome(score: (Int, Int)): Int =
    if !isValid(score) then 0 else Integer.signum(score._1 - score._2)

  /**
   * Liest die Schnelleingabe einer `race`-Einheit. Angegeben wird nur der Wert des Verlierers, negativ, wenn Seite B
   * gewinnt (TT: "7" = 11:7, "-8" = 8:11, "10" = 12:10, "0"/"+0" = 11:0, "-0" = 0:11).
   *
   * @param s Eingabe
   * @return Stand (a, b) oder None, wenn die Eingabe nicht lesbar ist
   */
  def parseShort(s: String): Option[(Int, Int)] =
    def win(lo: Int): Option[(Int, Int)] =
      if lo < 0 || lo > Level.MaxShort then None
      else if lo > target - winBy then Some((lo + winBy, lo))
      else Some((target, lo))

    s.trim match
      case ""          => None
      case "0" | "+0"  => Some((target, 0))
      case "-0"        => Some((0, target))
      case t           => t.toIntOption match
        case Some(v) if v > 0 => win(v)
        case Some(v) if v < 0 => win(-v).map(_.swap)
        case _                => None

  /**
   * Erzeugt die Schnelleingabe zu einem Stand (Umkehrung von [[parseShort]]).
   *
   * @param score Stand aus Sicht von Seite A
   * @return Wert des Verlierers, negativ wenn Seite B gewinnt
   */
  def formatShort(score: (Int, Int)): String =
    val (a, b) = score
    if a > b then b.toString else s"-$a"

object Level:
  val Atomic = "atomic"
  val Race   = "race"
  val BestOf = "bestOf"
  val Fixed  = "fixed"
  val Sum    = "sum"

  /** Größter Wert der Schnelleingabe (Schutz vor Tippfehlern). */
  val MaxShort = 500

  given rw: RW[Level] = macroRW


/**
 * Variante einer Sportart: ändert Parameter einzelner Stufen, ohne die Stufen zu wiederholen.
 *
 * @param name      Namen je Sprache
 * @param overrides abweichende Parameter je Stufen-ID (JSON-Feld `override`)
 */
case class Variant(name: Names = Map(), @key("override") overrides: Map[String, ujson.Value] = Map())

object Variant:
  given rw: RW[Variant] = macroRW

/**
 * Ein Spiel innerhalb einer Begegnung.
 *
 * @param type    single oder double
 * @param home    Position der Heimmannschaft
 * @param away    Position der Gastmannschaft
 * @param decider Entscheidungsspiel: nur bei Gleichstand nach allen übrigen Spielen und nur, wenn die Stufe der
 *                Begegnung `decider` erlaubt; sonst entfällt es
 */
case class EncounterGame(`type`: String, home: String, away: String, decider: Boolean = false):
  /** true bei einem Doppel. */
  def isDouble: Boolean = `type` == "double"

object EncounterGame:
  given rw: RW[EncounterGame] = macroRW

/**
 * Spielsystem einer Begegnung zweier Mannschaften.
 *
 * @param id             Kennung des Systems innerhalb des Formats (z. B. "bundes")
 * @param name           Name je Sprache
 * @param description    Beschreibung je Sprache (Absätze durch Leerzeile getrennt)
 * @param level          Wertungsstufe der Begegnung (über der Spielstufe)
 * @param variants       Spielweisen (z. B. "bis 2 Siege", "alle Spiele"), überschreiben die Stufe `level`
 * @param defaultVariant Spielweise, wenn die Stage keine wählt
 * @param teamSize       Anzahl der Stammspieler (für die Mannschaftswertung)
 * @param maxPlayers     größte Anzahl gemeldeter Spieler einschließlich Ersatz
 * @param positions      Positionen je Seite ("home", "away")
 * @param games          geordnete Spielfolge
 */
case class EncounterCfg(
  id:             String                    = "",
  name:           Names                     = Map(),
  description:    Map[String, String]       = Map(),
  level:          Level                     = Level("encounter", Level.BestOf),
  variants:       Map[String, Variant]      = Map(),
  defaultVariant: String                    = "",
  teamSize:       Int                       = 2,
  maxPlayers:     Int                       = 2,
  positions:      Map[String, Seq[String]]  = Map(),
  games:          Seq[EncounterGame]        = Seq()
):
  /** Positionen der Heimmannschaft (side 0) bzw. Gastmannschaft (side 1). */
  def sidePositions(side: Int): Seq[String] = positions.getOrElse(if side == 0 then "home" else "away", Seq())

  /** Position einer Seite in einem Spiel. */
  def gamePos(game: EncounterGame, side: Int): String = if side == 0 then game.home else game.away

  /**
   * true, wenn eine Position mit einem Doppel besetzt wird.
   *
   * @param side 0 = Heim, 1 = Gast
   * @param pos  Position
   */
  def isDoublePos(side: Int, pos: String): Boolean = games.exists(g => g.isDouble && gamePos(g, side) == pos)

  /** true, wenn das System ein Entscheidungsspiel kennt. */
  def hasDecider: Boolean = games.exists(_.decider)

  /**
   * Beschreibung in einer Sprache (Rückfall "en", dann "de").
   *
   * @param lang Sprachkürzel
   * @return Text, leer wenn keine Beschreibung vorhanden ist
   */
  def descriptionIn(lang: String): String =
    description.get(lang).orElse(description.get("en")).orElse(description.get("de")).getOrElse("")

  /**
   * Stufe der Begegnung in einer Spielweise.
   *
   * @param variant Kennung der Spielweise, leer = `defaultVariant`
   * @param ko      true in K.-o.-Runden: dort gibt es kein Unentschieden, ein mögliches Unentschieden wird durch das
   *                Entscheidungsspiel aufgelöst
   * @return Stufe mit den Parametern der Spielweise
   */
  def levelFor(variant: String, ko: Boolean = false): Level =
    val v = if variant.isEmpty || !variants.contains(variant) then defaultVariant else variant
    val lv = variants.get(v).flatMap(_.overrides.get(level.id)) match
      case Some(ov: ujson.Obj) =>
        val js = writeJs(level)
        ov.value.foreach { case (k, x) => js(k) = x }
        read[Level](js)
      case _ => level
    if ko && lv.allowDraw then lv.copy(allowDraw = false, decider = hasDecider) else lv

object EncounterCfg:
  given rw: RW[EncounterCfg] = macroRW

/**
 * Wettbewerbsformat (heute `CompTyp`): Einzel, Doppel, Mannschaft.
 *
 * @param type          single, double oder team
 * @param name          Namen je Sprache
 * @param encounter     ein einzelnes Spielsystem bei Mannschaften (Dateien vor Einführung von `systems`)
 * @param systems       wählbare Spielsysteme bei Mannschaften (Auswahl je Stage)
 * @param defaultSystem System, wenn die Stage keines wählt (leer = erstes)
 * @param teamSize      Anzahl der Stammspieler einer Mannschaft (gilt für alle Systeme)
 * @param maxPlayers    größte Anzahl gemeldeter Spieler einschließlich Ersatz (gilt für alle Systeme)
 * @param standings     abweichende Tabelle für dieses Format
 */
case class Format(
  `type`:        String,
  name:          Names                = Map(),
  encounter:     Option[EncounterCfg] = None,
  systems:       Seq[EncounterCfg]    = Seq(),
  defaultSystem: String               = "",
  teamSize:      Option[Int]          = None,
  maxPlayers:    Option[Int]          = None,
  standings:     Option[Standings]    = None
):
  /**
   * Alle Spielsysteme; ein einzelnes `encounter` erhält die Kennung "default". Kadergröße des Formats wird
   * übernommen.
   */
  def systemList: Seq[EncounterCfg] =
    (systems ++ encounter.map(e => if e.id.isEmpty then e.copy(id = "default") else e)).map { e =>
      e.copy(teamSize = teamSize.getOrElse(e.teamSize), maxPlayers = maxPlayers.getOrElse(e.maxPlayers))
    }

  /**
   * Spielsystem nach Kennung.
   *
   * @param id Kennung, leer oder unbekannt = Standard (`defaultSystem` bzw. erstes)
   * @return Spielsystem, None ohne Systeme
   */
  def system(id: String): Option[EncounterCfg] =
    val all = systemList
    all.find(_.id == id).orElse(all.find(_.id == defaultSystem)).orElse(all.headOption)

object Format:
  given rw: RW[Format] = macroRW

/**
 * Tabellenpunkte je Sieg, Unentschieden und Niederlage.
 */
case class TablePoints(win: Int = 2, draw: Int = 1, loss: Int = 0)

object TablePoints:
  given rw: RW[TablePoints] = macroRW

/**
 * Tabelle: Tabellenpunkte und Reihenfolge der Entscheidungskriterien.
 *
 * Kriterien: `points` (Tabellenpunkte), `diff:<level>`, `sum:<level>`, `ratio:<level>`.
 * `headToHead` ist noch nicht umgesetzt und wird ignoriert.
 *
 * @param points         Tabellenpunkte je Ausgang
 * @param pointsByResult ergebnisabhängige Tabellenpunkte, Schlüssel "a:b" auf der Spielstufe
 * @param tieBreakers    geordnete Liste der Entscheidungskriterien
 */
case class Standings(
  points:         TablePoints                       = TablePoints(),
  pointsByResult: Option[Map[String, (Int, Int)]]   = None,
  tieBreakers:    Seq[String]                       = Seq("points")
):

  /**
   * Tabellenpunkte eines Ergebnisses aus Sicht von Seite A.
   *
   * @param score   Ergebnis auf der Spielstufe
   * @param outcome Ausgang (1, 0, -1)
   * @return Tabellenpunkte (A, B)
   */
  def tablePoints(score: (Int, Int), outcome: Int): (Int, Int) =
    val byResult = pointsByResult.flatMap { m =>
      m.get(s"${score._1}:${score._2}").orElse(m.get(s"${score._2}:${score._1}").map(_.swap))
    }
    byResult.getOrElse {
      outcome match
        case 1  => (points.win, points.loss)
        case -1 => (points.loss, points.win)
        case _  => (points.draw, points.draw)
    }

object Standings:
  given rw: RW[Standings] = macroRW

/**
 * Sportart.
 *
 * @param id      Kennung (z. B. "TT")
 * @param version Version des Wertungssystems (Teil des Dateinamens, z. B. 1 in `tt_001.json`)
 * @param name    Namen je Sprache
 */
case class Sport(id: String, version: Int = 0, name: Names = Map())

object Sport:
  given rw: RW[Sport] = macroRW


/**
 * Wertungssystem einer Sportart (Dateien im Verzeichnis `shared/sports/`).
 *
 * @param schemaVersion Version des Dateiformats
 * @param sport         Sportart
 * @param levels        Wertungsstufen von unten nach oben
 * @param matchLevelId  Stufe, die ein einzelnes Spiel beschreibt (JSON-Feld `match`)
 * @param variants      Varianten mit abweichenden Parametern
 * @param formats       Wettbewerbsformate
 * @param standings     Tabelle und Entscheidungskriterien
 */
case class SportConfig(
  schemaVersion:                 Int,
  sport:                         Sport,
  levels:                        Seq[Level],
  @key("match") matchLevelId:    String,
  variants:                      Map[String, Variant] = Map(),
  formats:                       Map[String, Format]  = Map(),
  standings:                     Standings            = Standings()
):

  /** Stufe mit der Kennung `id`. */
  def level(id: String): Option[Level] = levels.find(_.id == id)

  /** Stufe eines einzelnen Spiels. */
  def matchLevel: Level = level(matchLevelId).get

  /** Position der Spielstufe in `levels`. */
  private def matchIndex: Int = levels.indexWhere(_.id == matchLevelId)

  /**
   * Stufe `depth` Ebenen unterhalb der Spielstufe (0 = Spiel, 1 = Satz, 2 = Ball bei TT).
   *
   * @param depth Abstand zur Spielstufe
   * @return Stufe oder None, wenn es sie nicht gibt
   */
  def levelBelowMatch(depth: Int): Option[Level] = levels.lift(matchIndex - depth)

  /**
   * Abstand einer Stufe zur Spielstufe (Umkehrung von [[levelBelowMatch]]).
   *
   * @param id Kennung der Stufe
   * @return Abstand oder None, wenn die Stufe nicht unterhalb der Spielstufe liegt
   */
  def depthBelowMatch(id: String): Option[Int] =
    val i = levels.indexWhere(_.id == id)
    if i < 0 || i > matchIndex then None else Some(matchIndex - i)

  /**
   * Wendet abweichende Parameter auf einzelne Stufen an.
   *
   * @param overrides abweichende Parameter je Stufen-ID
   * @return Konfiguration mit geänderten Stufen
   */
  def withOverrides(overrides: Map[String, ujson.Value]): SportConfig =
    copy(levels = levels.map { lv =>
      overrides.get(lv.id) match
        case Some(ov: ujson.Obj) =>
          val js = writeJs(lv)
          ov.value.foreach { case (k, v) => js(k) = v }
          read[Level](js)
        case _ => lv
    })

  /**
   * Wendet eine Variante an.
   *
   * @param id Kennung der Variante
   * @return Konfiguration der Variante, unverändert bei unbekannter Variante
   */
  def withVariant(id: String): SportConfig =
    variants.get(id).map(v => withOverrides(v.overrides)).getOrElse(this)

  /**
   * Setzt die Anzahl der Gewinneinheiten der Spielstufe (heute `Stage.noWinSets`).
   *
   * @param toWin Anzahl der Gewinneinheiten
   * @return Konfiguration mit geändertem `toWin`
   */
  def withToWin(toWin: Int): SportConfig =
    if matchLevel.toWin == toWin then this
    else copy(levels = levels.map(lv => if lv.id == matchLevelId then lv.copy(toWin = toWin) else lv))

  /**
   * Spielsystem der Begegnung eines Formats.
   *
   * @param formatId Kennung des Formats (z. B. "team2")
   * @param system   Kennung des Systems, leer = Standard des Formats
   * @return Spielsystem, None bei Formaten ohne Begegnung
   */
  def encounter(formatId: String, system: String = ""): Option[EncounterCfg] =
    formats.get(formatId).flatMap(_.system(system))

  /**
   * Alle Spielsysteme eines Formats.
   *
   * @param formatId Kennung des Formats
   * @return Systeme, leer bei Formaten ohne Begegnung
   */
  def systems(formatId: String): Seq[EncounterCfg] = formats.get(formatId).map(_.systemList).getOrElse(Seq())

  /**
   * Konfiguration aus Sicht eines Formats: Bei Mannschaften wird die Begegnung als oberste Stufe angehängt und ist
   * dann die Stufe, auf die sich Tabelle und Ergebnis eines Eintrags im Spielplan beziehen.
   *
   * @param formatId Kennung des Formats
   * @param variant  Spielweise der Begegnung, leer = Standard
   * @param system   Spielsystem der Begegnung, leer = Standard
   * @return Konfiguration des Formats, unverändert bei Formaten ohne Begegnung
   */
  def forFormat(formatId: String, variant: String = "", system: String = ""): SportConfig =
    formats.get(formatId) match
      case Some(f) =>
        val withStandings = f.standings.map(s => copy(standings = s)).getOrElse(this)
        f.system(system) match
          case Some(enc) =>
            val lv = enc.levelFor(variant).copy(unit = matchLevelId)
            withStandings.copy(levels = levels :+ lv, matchLevelId = lv.id)
          case None => withStandings
      case None => this

object SportConfig:
  given rw: RW[SportConfig] = macroRW

  /**
   * Liest eine Konfiguration aus JSON und prüft die Verweise der Stufen.
   *
   * @param json Inhalt der Konfigurationsdatei
   * @return Konfiguration oder Fehlermeldung
   */
  def parse(json: String): Either[String, SportConfig] =
    try
      val cfg = read[SportConfig](json)
      val ids = cfg.levels.map(_.id)
      if cfg.level(cfg.matchLevelId).isEmpty then Left(s"match level '${cfg.matchLevelId}' not found")
      else cfg.levels.zipWithIndex.collectFirst {
        case (lv, i) if i == 0 && lv.kind != Level.Atomic           => s"first level '${lv.id}' must be atomic"
        case (lv, i) if i > 0 && lv.unit != ids(i - 1)              => s"unit of level '${lv.id}' must be '${ids(i - 1)}'"
      } match
        case Some(err) => Left(err)
        case None      => Right(cfg)
    catch case e: Throwable => Left(s"invalid sport config: ${e.getMessage}")
