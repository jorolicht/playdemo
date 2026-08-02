package dialogs

import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.{ MouseEvent, Event }
import org.scalajs.dom.raw.HTMLElement

import base.*
import base.Bootstrap.*
import shared.model.*
import services.*

/**
 * Result case class for DlgAddDouble.
 *
 * @param player1 The first player of the double pair.
 * @param player2 The second player of the double pair.
 * @param enroll Whether the double pair is actively enrolled or just registered.
 */
case class DlgAddDoubleResult(
  player1: Player,
  player2: Player,
  enroll:  Boolean
)

/**
 * Dialog for adding a double pair to a competition or player registration.
 */
object DlgAddDouble extends BaseDialog with JsWrapper:

  /** Returns the page/dialog name identifier. */
  def name = PageNameTyp("DlgAddDouble")
  
  val LoadId:         HtmlId = genId(name)
  val ModalId:        HtmlId = genId(name)
  val FormId:         HtmlId = genId(name)
  val InputP1Id:      HtmlId = genId(name)
  val InputP2Id:      HtmlId = genId(name)
  val Plist1Id:       HtmlId = genId(name)
  val Plist2Id:       HtmlId = genId(name)
  val RadioPlayId:    HtmlId = genId(name)
  val RadioRegId:     HtmlId = genId(name)
  
  val ApplyId:        HtmlId = genId(name)
  val CancelId:       HtmlId = genId(name)
  val CloseId:        HtmlId = genId(name)

  private var modal: Modal = null

  /**
   * Renders the dialog component.
   *
   * @param param Optional rendering parameter.
   * @return true when rendered.
   */
  def render(param: String = ""): Boolean = true

  /**
   * Displays the add double dialog and returns a Future containing the result.
   *
   * @param players Sequence of available players to select from.
   * @param clubs Sequence of clubs for resolving player club names.
   * @return Future containing Either an AppError (on cancel) or DlgAddDoubleResult.
   */
  def show(players: Seq[Player], clubs: Seq[Club]): Future[Either[AppError, DlgAddDoubleResult]] =
    val p = Promise[Either[AppError, DlgAddDoubleResult]]()
    val f = p.future

    if isEmpty(eE(LoadId, "span")) then
      setHtml(gE(LoadId), cviews.dialogs.html.DlgAddDouble(players, clubs))
      modal = Modal(gE(ModalId))
    else
      setHtml(gE(LoadId), cviews.dialogs.html.DlgAddDouble(players, clubs))
      modal = Modal(gE(ModalId))
    
    modal.show()

    // Helper to generate HTML option tags for a given list of players
    val buildOptionsHtml = (playersToRender: Seq[Player]) =>
      playersToRender.map { pl =>
        val clubName = clubs.find(_.id.toInt == pl.clubId).map(_.name).getOrElse("?")
        s"""<option value="${pl.lastName}, ${pl.firstName} [$clubName]" data-id="${pl.id.value}"></option>"""
      }.mkString("\n")

    // Helper to update both datalists reactively so that a selected player is excluded from the opposite list
    val updateDatalists = () =>
      val val1 = getInput(gE(InputP1Id)).trim
      val val2 = getInput(gE(InputP2Id)).trim

      val p1Opt = players.find(pl => s"${pl.lastName}, ${pl.firstName}".contains(val1.takeWhile(_ != '[').trim))
      val p2Opt = players.find(pl => s"${pl.lastName}, ${pl.firstName}".contains(val2.takeWhile(_ != '[').trim))

      val p1Filtered = p2Opt match
        case Some(p2) => players.filterNot(_.id == p2.id)
        case None     => players

      val p2Filtered = p1Opt match
        case Some(p1) => players.filterNot(_.id == p1.id)
        case None     => players

      setHtml(gE(Plist1Id), buildOptionsHtml(p1Filtered))
      setHtml(gE(Plist2Id), buildOptionsHtml(p2Filtered))

    // Attach event listeners to both input fields
    gE(InputP1Id).addEventListener("input", (_: dom.Event) => updateDatalists())
    gE(InputP1Id).addEventListener("change", (_: dom.Event) => updateDatalists())

    gE(InputP2Id).addEventListener("input", (_: dom.Event) => updateDatalists())
    gE(InputP2Id).addEventListener("change", (_: dom.Event) => updateDatalists())

    gE(ApplyId).onclick = { (_: MouseEvent) =>
      val val1 = getInput(gE(InputP1Id)).trim
      val val2 = getInput(gE(InputP2Id)).trim
      val enroll = gE(RadioPlayId).asInstanceOf[dom.html.Input].checked

      // Try to find the players by their display string
      val p1Opt = players.find(p => s"${p.lastName}, ${p.firstName}".contains(val1.takeWhile(_ != '[').trim))
      val p2Opt = players.find(p => s"${p.lastName}, ${p.firstName}".contains(val2.takeWhile(_ != '[').trim))

      (p1Opt, p2Opt) match
        case (Some(p1), Some(p2)) if p1.id != p2.id =>
          if (!p.isCompleted) p.success(Right(DlgAddDoubleResult(p1, p2, enroll)))
          modal.hide()
        case (Some(_), Some(_)) =>
          dom.window.alert("Ein Spieler kann nicht mit sich selbst im Doppel spielen.")
        case _ =>
          dom.window.alert("Bitte wählen Sie zwei gültige Spieler aus der Liste aus.")
    }

    val onCancel = { (_: MouseEvent) =>
      if (!p.isCompleted) p.success(Left(AppError("dlg.cancel")))
      modal.hide()
    }
    gE(CancelId).onclick = onCancel
    gE(CloseId).onclick = onCancel

    f
