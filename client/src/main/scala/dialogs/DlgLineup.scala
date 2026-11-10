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
   * Zeigt den Dialog.
   *
   * @param cfg     Spielsystem
   * @param side    0 = Mannschaft A (Heim), 1 = Mannschaft B (Gast)
   * @param team    Mannschaft
   * @param players Spieler des Turniers (für die Namen)
   * @param current bisherige Aufstellung
   * @return neue Aufstellung oder Fehler "dlg.cancel" bei Abbruch
   */
  def show(cfg: EncounterCfg, side: Int, team: Team, players: Seq[Player], current: Lineup): Future[Either[AppError, Lineup]] =
    val p = Promise[Either[AppError, Lineup]]()
    val roster = team.players.flatMap(id => players.find(_.id == id))
    setHtml(eE(LoadId, "span"), cviews.dialogs.html.DlgLineup(cfg, side, team, roster, current))
    modal = Modal(gE(ModalId))
    modal.show()

    def selected(pos: String, idx: Int): Option[PlayerId] =
      val el = dom.document.getElementById(selId(pos, idx)).asInstanceOf[dom.html.Select]
      if el == null then None else el.value.toIntOption.filter(_ > 0).map(PlayerId(_))

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
