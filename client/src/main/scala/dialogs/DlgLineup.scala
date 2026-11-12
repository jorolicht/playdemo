package dialogs

import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import org.scalajs.dom.MouseEvent

import base.*
import base.Bootstrap.*
import shared.model.*
import shared.sport.EncounterCfg

/**
 * Dialog für die Aufstellung einer Mannschaft in einer Begegnung. Je Einzelposition wird ein Spieler, je
 * Doppelposition werden zwei Spieler aus dem Kader gewählt. Leere Positionen werden als verloren gewertet.
 */
object DlgLineup extends BaseDialog with JsWrapper:

  /** Name des Dialogs. */
  def name = PageNameTyp("DlgLineup")

  val LoadId:   HtmlId = genId(name)
  val ModalId:  HtmlId = genId(name)
  val SelPosId: HtmlId = genId(name)
  val ApplyId:  HtmlId = genId(name)
  val CancelId: HtmlId = genId(name)
  val CloseId:  HtmlId = genId(name)

  private var modal: Modal = null

  /** Kein eigenes Rendern, der Inhalt entsteht in [[show]]. */
  def render(param: String = ""): Boolean = true

  /**
   * HTML-Id der Auswahl einer Position (bei Doppeln mit Index 1 oder 2).
   *
   * @param pos Position
   * @param idx 1 bei Einzeln, 1 oder 2 bei Doppeln
   */
  def selId(pos: String, idx: Int): String = s"${SelPosId.id}-$pos-$idx"

  /**
   * Sperrt in jeder Auswahl die Spieler, die dort nicht mehr gewählt werden dürfen: In den Einzeln spielt jeder
   * Spieler höchstens einmal, im Doppel sind die beiden Partner verschieden. Ein Einzelspieler darf zusätzlich im
   * Doppel spielen.
   *
   * @param cfg  Spielsystem
   * @param side 0 = Mannschaft A, 1 = Mannschaft B
   */
  private def syncOptions(cfg: EncounterCfg, side: Int): Unit =
    def sel(pos: String, idx: Int) = dom.document.getElementById(selId(pos, idx)).asInstanceOf[dom.html.Select]
    def value(el: dom.html.Select) = if el == null then "0" else el.value
    def block(el: dom.html.Select, taken: Set[String]): Unit =
      if el != null then
        for i <- 0 until el.options.length do
          val o = el.options(i)
          o.disabled = o.value != "0" && o.value != el.value && taken.contains(o.value)
    val singles = cfg.sidePositions(side).filterNot(cfg.isDoublePos(side, _))
    singles.foreach { pos =>
      block(sel(pos, 1), singles.filter(_ != pos).map(p => value(sel(p, 1))).toSet - "0")
    }
    cfg.sidePositions(side).filter(cfg.isDoublePos(side, _)).foreach { pos =>
      block(sel(pos, 1), Set(value(sel(pos, 2))) - "0")
      block(sel(pos, 2), Set(value(sel(pos, 1))) - "0")
    }

  /**
   * Zeigt den Dialog.
   *
   * @param cfg     Spielsystem
   * @param side    0 = Mannschaft A (Heim), 1 = Mannschaft B (Gast)
   * @param team    Mannschaft
   * @param players Spieler des Turniers (für die Namen)
   * @param current bisherige Aufstellung
   * @param readOnly true, wenn die Begegnung bereits gestartet ist: nur Anzeige, "Übernehmen" ist inaktiv
   * @return neue Aufstellung oder Fehler "dlg.cancel" bei Abbruch
   */
  def show(cfg: EncounterCfg, side: Int, team: Team, players: Seq[Player], current: Lineup, readOnly: Boolean = false): Future[Either[AppError, Lineup]] =
    val p = Promise[Either[AppError, Lineup]]()
    val roster = team.players.flatMap(id => players.find(_.id == id))
    setHtml(eE(LoadId, "span"), cviews.dialogs.html.DlgLineup(cfg, side, team, roster, current, readOnly))
    modal = Modal(gE(ModalId))
    modal.show()

    def selected(pos: String, idx: Int): Option[PlayerId] =
      val el = dom.document.getElementById(selId(pos, idx)).asInstanceOf[dom.html.Select]
      if el == null then None else el.value.toIntOption.filter(_ > 0).map(PlayerId(_))

    syncOptions(cfg, side)
    for pos <- cfg.sidePositions(side); idx <- 1 to (if cfg.isDoublePos(side, pos) then 2 else 1) do
      val el = dom.document.getElementById(selId(pos, idx)).asInstanceOf[dom.html.Select]
      if el != null then el.onchange = (_: dom.Event) => syncOptions(cfg, side)

    gE(ApplyId).onclick = { (_: MouseEvent) =>
      val map = cfg.sidePositions(side).map { pos =>
        val ids = if cfg.isDoublePos(side, pos) then Seq(selected(pos, 1), selected(pos, 2)).flatten else selected(pos, 1).toSeq
        pos -> ids
      }.toMap
      val singles = map.filter { case (pos, _) => !cfg.isDoublePos(side, pos) }.values.flatten.toSeq
      val badDouble = map.exists { case (pos, ids) => cfg.isDoublePos(side, pos) && (ids.length == 1 || (ids.length == 2 && ids(0) == ids(1))) }
      if singles.distinct.length != singles.length then dom.window.alert(gM("DlgLineup.err_single"))
      else if badDouble then dom.window.alert(gM("DlgLineup.err_double"))
      else
        if !p.isCompleted then p.success(Right(Lineup(map)))
        modal.hide()
    }

    val onCancel = { (_: MouseEvent) =>
      if !p.isCompleted then p.success(Left(AppError("dlg.cancel")))
      modal.hide()
    }
    gE(CancelId).onclick = onCancel
    gE(CloseId).onclick  = onCancel
    p.future
