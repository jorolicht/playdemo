package base

import shared.model.{ CompCategory, CompTyp, Competition }
import shared.sport.{ Name, SportConfig, Sports, localized }

/**
 * Bezeichnungen aus dem Wertungssystem (Sportart, Formate, Stufen, Gewinnsätze) in der Sprache der Oberfläche.
 *
 * Die Namen stehen in den Dateien `shared/sports/<sport>_<nnn>.json` und nicht in den Message-Dateien, damit andere
 * Sportarten ihre eigenen Begriffe mitbringen (z. B. "Satz" im Tischtennis, "Leg" im Darts). Ohne Angabe des
 * Wertungssystems gilt das des ausgewählten Wettbewerbs ([[Sports.current]]).
 */
object SportText:

  private def lang: String = Global.lang

  /**
   * Format eines Wettbewerbs (z. B. "Einzel", "2er-Mannschaft").
   *
   * @param comp Wettbewerb
   * @return Name aus seinem Wertungssystem
   */
  def compTyp(comp: Competition): String = Sports.formatName(comp.scoring, comp.typ.formatId, lang)

  /**
   * Format ohne Wettbewerb (z. B. Auswahl beim Anlegen), aus der neuesten Version.
   *
   * @param typ Wettbewerbstyp
   * @return Name; Typen ohne Format (z. B. UNKN) mit ihrer Kennung
   */
  def typ(typ: CompTyp): String = Sports.latest.formatName(typ.formatId, lang).getOrElse(typ.toString)

  /**
   * Sportart eines Turniers bzw. Wettbewerbs.
   *
   * @param cat Kategorie (Kennung der Sportart)
   * @return Name aus dem Wertungssystem; ohne Wertungssystem (z. B. UNKNOWN) der Text aus den Messages
   */
  def category(cat: CompCategory): String = Sports.sportName(cat.toString, lang).getOrElse(Messages.gM(cat.msgCode))

  /**
   * Name einer Stufe.
   *
   * @param id  Kennung (z. B. "ball", "set", "match")
   * @param cfg Wertungssystem
   * @return Singular, Plural und Kurzform; ohne Eintrag die Kennung
   */
  def level(id: String, cfg: SportConfig = Sports.current): Name = cfg.levelName(id, lang).getOrElse(Name(id))

  /**
   * Oberste Stufe eines Formats: bei Mannschaften die Begegnung, sonst das Spiel.
   *
   * @param formatId Kennung des Formats
   * @param cfg      Wertungssystem
   * @return Name der Stufe
   */
  def matchLevel(formatId: String, cfg: SportConfig = Sports.current): Name =
    cfg.forFormat(formatId).matchLevel.name.localized(lang)

  /**
   * Art eines Spiels in einer Mannschaftsbegegnung ("Einzel" bzw. "Doppel").
   *
   * @param double true bei einem Doppel
   * @param cfg    Wertungssystem
   * @return Name des Formats
   */
  def game(double: Boolean, cfg: SportConfig = Sports.current): String =
    val id = if double then "double" else "single"
    cfg.formatName(id, lang).getOrElse(id)

  /**
   * Auswahl der Gewinneinheiten eines Spiels (z. B. "3 Gewinnsätze").
   *
   * @param cfg Wertungssystem
   * @return (Gewinneinheiten, Bezeichnung), aufsteigend; ohne passende Varianten 2 bis 4 mit der Zahl
   */
  def winOptions(cfg: SportConfig = Sports.current): Seq[(Int, String)] =
    val vs = cfg.winVariants.map((n, names) => (n, names.localized(lang).one)).filter(_._2.nonEmpty)
    if vs.nonEmpty then vs else (2 to 4).map(n => (n, n.toString))
