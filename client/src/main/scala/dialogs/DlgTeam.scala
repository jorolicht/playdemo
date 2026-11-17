package dialogs

import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import org.scalajs.dom.MouseEvent

import base.*
import base.Bootstrap.*
import shared.model.*

/**
 * Ergebnis von [[DlgTeam]].
 *
 * @param name     Name der Mannschaft
 * @param clubName Verein (leer = ohne)
 * @param players  Spieler in Reihenfolge, Stammspieler zuerst
 */
case class DlgTeamResult(name: String, clubName: String, players: Seq[PlayerId])

/**
 * Dialog zum Anlegen oder Bearbeiten einer Mannschaft mit ihren Spielern.
 */
object DlgTeam extends BaseDialog with JsWrapper:

  /** Name des Dialogs. */
  def name = PageNameTyp("DlgTeam")

  val LoadId:      HtmlId = genId(name)
  val ModalId:     HtmlId = genId(name)
  val InputNameId: HtmlId = genId(name)
  val InputClubId: HtmlId = genId(name)
  val ClubListId:  HtmlId = genId(name)
  val SelPlayerId: HtmlId = genId(name)
  /** Button "Spieler neu anlegen" hinter jedem Auswahlfeld, Suffix = Nummer des Felds. */
  val BtnNewPlayerId: HtmlId = genId(name)
  val ApplyId:     HtmlId = genId(name)
  val CancelId:    HtmlId = genId(name)
  val CloseId:     HtmlId = genId(name)

  private var modal: Modal = null

  /** Auswahlfeld des i-ten Spielers (1-basiert). */
  private def selPlayer(i: Int): dom.html.Select =
    gE(HtmlId(s"${SelPlayerId.id}-$i")).asInstanceOf[dom.html.Select]

  /** Gewählte Spieler-Id des i-ten Auswahlfelds, 0 = keiner. */
  private def selectedId(i: Int): Int = selPlayer(i).value.toIntOption.getOrElse(0)

  /**
   * Sperrt in jedem Auswahlfeld die Spieler, die bereits in einem anderen Feld gewählt sind,
   * damit ein Spieler nicht mehrfach in die Mannschaft aufgenommen werden kann.
   *
   * @param maxPlayers Anzahl der Auswahlfelder
   */
  private def syncPlayerOptions(maxPlayers: Int): Unit =
    val chosen = (1 to maxPlayers).map(i => i -> selectedId(i))
    (1 to maxPlayers).foreach { i =>
      val taken = chosen.collect { case (j, id) if j != i && id > 0 => id.toString }.toSet
      val opts  = selPlayer(i).options
      (0 until opts.length).foreach { k =>
        val o = opts(k)
        o.disabled = taken.contains(o.value)
      }
    }

  /** Kein eigenes Rendern, der Inhalt entsteht in [[show]]. */
  def render(param: String = ""): Boolean = true

  /**
   * Zeigt den Dialog.
   *
   * @param title      Titel
   * @param team       zu bearbeitende Mannschaft, None = neue Mannschaft
   * @param clubName   Verein der Mannschaft
   * @param players    wählbare Spieler
   * @param clubs      Vereine (Vorschläge und Anzeige)
   * @param teamSize   Anzahl der Stammspieler
   * @param maxPlayers   größte Anzahl Spieler
   * @param createPlayer legt aus den Eingaben von [[DlgAddSingle]] einen neuen Spieler im Turnier an
   *                     (Button "Spieler neu anlegen"); liefert Spieler und aktuelle Vereine oder einen Fehler
   * @return Ergebnis oder Fehler "dlg.cancel" bei Abbruch
   */
  def show(title: String, team: Option[Team], clubName: String, players: Seq[Player], clubs: Seq[Club],
           teamSize: Int, maxPlayers: Int,
           createPlayer: DlgAddSingleResult => Either[AppError, (Player, Seq[Club])]): Future[Either[AppError, DlgTeamResult]] =
    val p = Promise[Either[AppError, DlgTeamResult]]()
    setHtml(eE(LoadId, "span"), cviews.dialogs.html.DlgTeam(title, team, clubName, players, clubs, teamSize, maxPlayers))
    modal = Modal(gE(ModalId))
    syncPlayerOptions(maxPlayers)
    (1 to maxPlayers).foreach { i =>
      selPlayer(i).onchange = (_: dom.Event) => syncPlayerOptions(maxPlayers)
    }
    var curClubs = clubs
    (1 to maxPlayers).foreach { i =>
      gE(HtmlId(s"${BtnNewPlayerId.id}-$i")).asInstanceOf[dom.html.Element].onclick = (_: MouseEvent) =>
        newPlayerFromDialog(modal, gE(ModalId), curClubs, createPlayer) { (pl, cl) =>
          curClubs = cl
          val club  = cl.find(_.id.toInt == pl.clubId).map(_.name).getOrElse("")
          val ttr   = pl.meta.ttr.map(t => s", $t").getOrElse("")
          // neuen Spieler in allen Auswahlfeldern anbieten und im gewählten Feld setzen
          (1 to maxPlayers).foreach { j =>
            val opt = dom.document.createElement("option").asInstanceOf[dom.html.Option]
            opt.value = pl.id.value.toString
            opt.text  = s"${pl.displayName} ($club$ttr)"
            selPlayer(j).appendChild(opt)
          }
          selPlayer(i).value = pl.id.value.toString
          syncPlayerOptions(maxPlayers)
        }
    }
    modal.show()

    gE(ApplyId).onclick = { (_: MouseEvent) =>
      val tName = getInput(gE(InputNameId)).trim
      val club  = getInput(gE(InputClubId)).trim
      val ids   = (1 to maxPlayers).map(selectedId).filter(_ > 0).map(PlayerId(_))
      if tName.isEmpty then dom.window.alert(gM("DlgTeam.err_name"))
      else if ids.distinct.length != ids.length then dom.window.alert(gM("DlgTeam.err_duplicate"))
      else
        if !p.isCompleted then p.success(Right(DlgTeamResult(tName, club, ids)))
        modal.hide()
    }

    val onCancel = { (_: MouseEvent) =>
      if !p.isCompleted then p.success(Left(AppError("dlg.cancel")))
      modal.hide()
    }
    gE(CancelId).onclick = onCancel
    gE(CloseId).onclick  = onCancel
    p.future
