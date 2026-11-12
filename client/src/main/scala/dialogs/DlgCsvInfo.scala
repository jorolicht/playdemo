package dialogs

import org.scalajs.dom.MouseEvent

import base.*
import base.Bootstrap.*
import shared.model.*

/**
 * Info-Dialog zum CSV-Format des Uploads auf der Seite "Anmeldung" mit Beispiel; der Hinweis zur Verarbeitung
 * richtet sich nach dem Typ des Wettbewerbs (Einzel, Doppel/Mixed, Mannschaft).
 */
object DlgCsvInfo extends BaseDialog with JsWrapper:

  /** Name des Dialogs. */
  def name = PageNameTyp("DlgCsvInfo")

  val LoadId:  HtmlId = genId(name)
  val ModalId: HtmlId = genId(name)
  val CloseId: HtmlId = genId(name)
  val OkId:    HtmlId = genId(name)

  /** Kein eigenes Rendern, der Inhalt entsteht in [[show]]. */
  def render(param: String = ""): Boolean = true

  /**
   * Schlüssel des typabhängigen Hinweises zur Verarbeitung.
   *
   * @param typ Typ des Wettbewerbs
   * @return Nachrichtenschlüssel
   */
  def modeKey(typ: CompTyp): String =
    if shared.format.TeamEncounter.isTeam(typ) then "DlgCsvInfo.mode_team"
    else if typ == CompTyp.DOUBLE || typ == CompTyp.MIXED then "DlgCsvInfo.mode_double"
    else "DlgCsvInfo.mode_single"

  /**
   * Zeigt den Dialog.
   *
   * @param typ Typ des ausgewählten Wettbewerbs
   */
  def show(typ: CompTyp): Unit =
    setHtml(eE(LoadId, "span"), cviews.dialogs.html.DlgCsvInfo(typ))
    val modal = Modal(gE(ModalId))
    gE(OkId).onclick = (_: MouseEvent) => modal.hide()
    modal.show()
