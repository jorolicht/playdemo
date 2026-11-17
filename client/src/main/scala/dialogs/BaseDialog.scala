package dialogs

import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.raw.HTMLElement
import base.Bootstrap.Modal
import shared.model.{ Club, Player }
import shared.basic.AppError
import shared.DialogTypes.DialogId


// dlgMap maps dialog names to dialog objects   
val dlgMap = List(DlgMsgbox, DlgPrompt, DlgClickTT, DlgCompetition, DlgStageStart, DlgAddSingle, DlgAddDouble, DlgAddPlayer, DlgEditPlayer, DlgPublicRegistration, DlgExportTourney, DlgStartDemo, DlgStartTourney, DlgStartCompetition, DlgTeam, DlgLineup, DlgCsvInfo)
                    .map(dlg => dlg.name -> dlg).toMap

object Ids:
  import shared.DialogTypes.DialogId
  val DlgMsgboxId:  DialogId = DialogId.myName
  val DlgPromptId:  DialogId = DialogId.myName
  val DlgClickTTId: DialogId = DialogId.myName


abstract class BaseDialog extends comps.BaseComp:
  
  override def handleEvent(elem: HTMLElement, event: Event): Unit = {}

  /**
   * Führt `f` aus, sobald das Bootstrap-Modal `el` vollständig ausgeblendet ist (sofort, wenn es das bereits ist).
   *
   * @param el Modal-Element
   * @param f  auszuführende Aktion
   */
  protected def onceHidden(el: dom.Element)(f: => Unit): Unit =
    if el.asInstanceOf[dom.html.Element].style.display == "none" then f
    else
      lazy val h: js.Function1[dom.Event, Unit] = (_: dom.Event) =>
        el.removeEventListener("hidden.bs.modal", h)
        f
      el.addEventListener("hidden.bs.modal", h)

  /**
   * "Spieler neu anlegen" aus einem offenen Dialog: blendet den Dialog aus, zeigt [[DlgAddSingle]] ohne
   * Anmeldeoption, legt den Spieler über `createPlayer` an und blendet den Dialog danach wieder ein.
   *
   * @param modal        Modal des aufrufenden Dialogs
   * @param modalEl      Element dieses Modals
   * @param clubs        Vereine (Vorschläge)
   * @param createPlayer legt den Spieler an; liefert Spieler und aktuelle Vereine oder einen Fehler
   * @param onCreated    wird mit dem neuen Spieler aufgerufen, bevor der Dialog wieder erscheint
   */
  protected def newPlayerFromDialog(modal: Modal, modalEl: dom.Element, clubs: Seq[Club],
                                    createPlayer: DlgAddSingleResult => Either[AppError, (Player, Seq[Club])])
                                   (onCreated: (Player, Seq[Club]) => Unit): Unit =
    modal.hide()
    onceHidden(modalEl) {
      DlgAddSingle.show(clubs, playerOnly = true).map { res =>
        onceHidden(dom.document.getElementById(DlgAddSingle.ModalId.id)) {
          res.flatMap(createPlayer) match
            case Right((pl, cl)) => onCreated(pl, cl)
            case Left(err) if err.msgCode != "dlg.cancel" => dom.window.alert(base.Messages.getErr(err))
            case Left(_) => ()
          modal.show()
        }
      }
    }
