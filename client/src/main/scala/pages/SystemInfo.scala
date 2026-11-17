package pages

import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.raw.HTMLElement
import base.*
import shared.model.*
import shared.sport.{ EncounterCfg, Level, SportConfig, Sports, localized }

/**
 * Infoseite "Spielsysteme": beschreibt die Spielsysteme der Mannschaftsformate eines Wertungssystems (Name,
 * Beschreibung aus der Schema-Datei, Kader, Spielfolge, Optionen). Aufruf aus der Stage-Administration über den
 * Info-Button neben der Systemauswahl; das gewählte System wird hervorgehoben.
 */
object SystemInfo extends BasePage with JsWrapper:
  def name = PageNameTyp("SystemInfo")

  /** Button "Zurück" zur Stage-Administration. */
  val BtnBack: HtmlId = genId(name)

  /**
   * Abschnitt eines Formats.
   *
   * @param formatId   Kennung (z. B. "team2")
   * @param formatName Name des Formats
   * @param teamSize   Stammspieler
   * @param maxPlayers Spieler im Kader einschließlich Ersatz
   * @param systems    Systeme des Formats
   */
  case class FormatSection(formatId: String, formatName: String, teamSize: Int, maxPlayers: Int, systems: Seq[EncounterCfg])

  /**
   * Parameter der Seite für einen Wettbewerb und ein System.
   *
   * @param comp     Wettbewerb (Wertungssystem und Format)
   * @param systemId gewähltes System
   * @return Parameter "<wertungssystem>|<format>|<system>"
   */
  def param(comp: Competition, systemId: String): String =
    s"${Sports.resolveKey(comp.scoring)}|${comp.typ.formatId}|$systemId"

  /**
   * Text der Spielweise einer Stufe (z. B. "Bis 3 Punkte").
   *
   * @param lv Stufe der Begegnung
   * @return Beschreibung des Spielendes
   */
  def endRule(lv: Level): String =
    if lv.kind == Level.BestOf then
      val base = gM("SystemInfo.rule_best_of", lv.toWin.toString)
      if lv.allowDraw then s"$base ${gM("SystemInfo.rule_draw_at", (lv.toWin - 1).toString)}" else base
    else
      val base = gM("SystemInfo.rule_all", lv.count.toString)
      if lv.allowDraw then s"$base ${gM("SystemInfo.rule_draw")}"
      else if lv.decider then s"$base ${gM("SystemInfo.rule_decider")}"
      else base

  /**
   * Bezeichnung eines Spiels der Spielfolge.
   *
   * @param cfg System
   * @param i   Index in der Spielfolge
   * @return z. B. "Einzel A1 – B2" oder "Doppel (Entscheidungsspiel)"
   */
  def gameLabel(cfg: EncounterCfg, i: Int): String =
    val g = cfg.games(i)
    val kind = if g.isDouble then gM("SystemInfo.double") else gM("SystemInfo.single")
    val pair = if g.isDouble then "" else s" ${g.home} – ${g.away}"
    val dec  = if g.decider then s" (${gM("SystemInfo.decider_game")})" else ""
    s"$kind$pair$dec"

  /**
   * Rendert die Seite.
   *
   * @param param "<wertungssystem>|<format>|<system>", leer = neueste Version, nichts hervorgehoben
   * @return true
   */
  def render(param: String = ""): Boolean =
    comps.ContextHeader.render()
    val parts    = param.split('|')
    val key      = parts.lift(0).filter(_.nonEmpty).getOrElse(Sports.latestKey())
    val selFmt   = parts.lift(1).getOrElse("")
    val selSys   = parts.lift(2).getOrElse("")
    val cfg: SportConfig = Sports.get(key)
    val sections = cfg.formats.toSeq.sortBy(_._1).flatMap { case (fId, f) =>
      val systems = f.systemList
      if systems.isEmpty then None
      else Some(FormatSection(fId, f.name.localized(Global.lang).one, systems.head.teamSize, systems.head.maxPlayers, systems))
    }
    // gewähltes System (leer = Standard des Formats)
    val selected = cfg.formats.get(selFmt).flatMap(_.system(selSys)).map(s => (selFmt, s.id))
    setMain(cviews.pages.html.SystemInfo(Sports.resolveKey(key), sections, selected))
    selected.foreach { case (f, s) =>
      Option(dom.document.getElementById(s"sys-$f-$s")).foreach(_.asInstanceOf[scala.scalajs.js.Dynamic].scrollIntoView(true))
    }
    true

  /**
   * Verarbeitet die Klicks der Seite.
   *
   * @param elem  auslösendes Element
   * @param event Ereignis
   */
  override def handleEvent(elem: HTMLElement, event: Event): Unit =
    if elem.id == BtnBack.id then loadPage(PageNameTyp("StageAdmin"), "")
