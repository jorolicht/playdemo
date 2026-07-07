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

object DlgEditPlayer extends BaseDialog with JsWrapper:
  def name = PageNameTyp("DlgEditPlayer")
  
  val LoadId:             HtmlId = genId(name)
  val ModalId:            HtmlId = genId(name)
  val FormId:             HtmlId = genId(name)
  
  val InputLastId:        HtmlId = genId(name)
  val InputFirstId:       HtmlId = genId(name)
  val InputClubId:        HtmlId = genId(name)
  val InputYearId:        HtmlId = genId(name)
  val InputEmailId:       HtmlId = genId(name)
  val InputSexId:         HtmlId = genId(name)
  
  val InputTtrId:         HtmlId = genId(name)
  val InputInternalNrId:  HtmlId = genId(name)
  val InputLicenceNrId:   HtmlId = genId(name)
  val InputClubNrId:      HtmlId = genId(name)
  val InputClubFedId:     HtmlId = genId(name)
  val InputNatId:         HtmlId = genId(name)
  val InputForeignerId:   HtmlId = genId(name)
  val InputRegionId:      HtmlId = genId(name)
  val InputSubRegionId:   HtmlId = genId(name)
  
  val BtnDeleteCttId:     HtmlId = genId(name)
  val ApplyId:            HtmlId = genId(name)
  val CancelId:           HtmlId = genId(name)
  val CloseId:            HtmlId = genId(name)

  private var modal: Modal = null
  private var activePlayer: Player = null
  private var isCttDeleted: Boolean = false

  def render(param: String = ""): Boolean = true

  def show(player: Player): Future[Either[AppError, Player]] = {
    val p = Promise[Either[AppError, Player]]()
    val f = p.future

    activePlayer = player
    isCttDeleted = false

    val clubs = TourneyDB.tourney.clubs.toSeq
    val currentYear = 2026

    setHtml(eE(LoadId, "span"), cviews.dialogs.html.DlgEditPlayer(player, clubs, currentYear))
    modal = Modal(gE(ModalId))

    setInputValue(InputLastId, player.lastName)
    setInputValue(InputFirstId, player.firstName)
    setSelectedValue(InputClubId, player.clubId.toString)
    setSelectedValue(InputYearId, player.birthYear.getOrElse(0).toString)
    setInputValue(InputEmailId, player.email.getOrElse(""))
    setSelectedValue(InputSexId, player.sex.id.toString)

    setInputValue(InputTtrId, player.meta.ttr.map(_.toString).getOrElse(""))
    setInputValue(InputInternalNrId, player.meta.internalNr.getOrElse(""))
    setInputValue(InputLicenceNrId, player.meta.licenceNr.getOrElse(""))
    setInputValue(InputClubNrId, player.meta.clubNr.getOrElse(""))
    setInputValue(InputClubFedId, player.meta.clubFedNick.getOrElse(""))
    setInputValue(InputNatId, player.meta.nationality.getOrElse(""))
    setInputValue(InputForeignerId, player.meta.foreignerEqState.getOrElse(""))
    setInputValue(InputRegionId, player.meta.region.getOrElse(""))
    setInputValue(InputSubRegionId, player.meta.subRegion.getOrElse(""))

    val hasCtt = player.meta.licenceNr.isDefined || player.meta.internalNr.isDefined
    updateFieldStates(hasCtt)

    gE(BtnDeleteCttId).onclick = { (_: MouseEvent) =>
      isCttDeleted = true
      updateFieldStates(hasCtt = false)
    }

    gE(ApplyId).onclick = { (_: MouseEvent) =>
      val last = getInput(gE(InputLastId)).trim
      val first = getInput(gE(InputFirstId)).trim
      val clubIdVal = try getInput(gE(InputClubId)).toInt catch { case _: Exception => 0 }
      val yearVal = try getInput(gE(InputYearId)).toInt catch { case _: Exception => 0 }
      val birthYear = if (yearVal == 0) None else Some(yearVal)
      val emailVal = getInput(gE(InputEmailId)).trim
      val email = if (emailVal.isEmpty) None else Some(emailVal)
      val sexVal = try Sex.fromInt(getInput(gE(InputSexId)).toInt) catch { case _: Exception => Sex.Unknown }

      if (last.isEmpty || first.isEmpty) {
        dom.window.alert("Bitte Vorname und Nachname ausfüllen.")
      } else {
        val updatedMeta = if (isCttDeleted) {
          PlayerMeta()
        } else if (hasCtt) {
          player.meta
        } else {
          val ttrVal = try Some(getInput(gE(InputTtrId)).toInt) catch { case _: Exception => None }
          val internalNr = Option(getInput(gE(InputInternalNrId)).trim).filter(_.nonEmpty)
          val licenceNr = Option(getInput(gE(InputLicenceNrId)).trim).filter(_.nonEmpty)
          val clubNr = Option(getInput(gE(InputClubNrId)).trim).filter(_.nonEmpty)
          val clubFedNick = Option(getInput(gE(InputClubFedId)).trim).filter(_.nonEmpty)
          val nationality = Option(getInput(gE(InputNatId)).trim).filter(_.nonEmpty)
          val foreignerEqState = Option(getInput(gE(InputForeignerId)).trim).filter(_.nonEmpty)
          val region = Option(getInput(gE(InputRegionId)).trim).filter(_.nonEmpty)
          val subRegion = Option(getInput(gE(InputSubRegionId)).trim).filter(_.nonEmpty)
          
          PlayerMeta(
            internalNr = internalNr,
            licenceNr = licenceNr,
            clubNr = clubNr,
            clubFedNick = clubFedNick,
            ttr = ttrVal,
            nationality = nationality,
            foreignerEqState = foreignerEqState,
            region = region,
            subRegion = subRegion
          )
        }

        val updatedPlayer = player.copy(
          firstName = first,
          lastName = last,
          clubId = clubIdVal,
          birthYear = birthYear,
          email = email,
          sex = sexVal,
          meta = updatedMeta
        )

        p.success(Right(updatedPlayer))
        modal.hide()
      }
    }

    val onCancel = { (_: MouseEvent) =>
      p.success(Left(AppError("dlg.cancel")))
      modal.hide()
    }
    gE(CancelId).onclick = onCancel
    gE(CloseId).onclick = onCancel

    modal.show()
    f
  }

  private def updateFieldStates(hasCtt: Boolean): Unit = {
    val editablePlayer = !hasCtt
    val editableMeta = !hasCtt && !isCttDeleted

    setElementEnabled(InputLastId, editablePlayer)
    setElementEnabled(InputFirstId, editablePlayer)
    setElementEnabled(InputClubId, editablePlayer)
    setElementEnabled(InputYearId, editablePlayer)
    setElementEnabled(InputEmailId, true)
    setElementEnabled(InputSexId, editablePlayer)

    setElementEnabled(InputTtrId, editableMeta)
    setElementEnabled(InputInternalNrId, editableMeta)
    setElementEnabled(InputLicenceNrId, editableMeta)
    setElementEnabled(InputClubNrId, editableMeta)
    setElementEnabled(InputClubFedId, editableMeta)
    setElementEnabled(InputNatId, editableMeta)
    setElementEnabled(InputForeignerId, editableMeta)
    setElementEnabled(InputRegionId, editableMeta)
    setElementEnabled(InputSubRegionId, editableMeta)

    val btn = gE(BtnDeleteCttId)
    if (btn != null) {
      setVisible(btn, hasCtt)
    }
  }

  private def setInputValue(id: HtmlId, value: String): Unit = {
    val el = gE(id).asInstanceOf[dom.html.Input]
    if (el != null) el.value = value
  }

  private def setSelectedValue(id: HtmlId, value: String): Unit = {
    val el = gE(id).asInstanceOf[dom.html.Select]
    if (el != null) el.value = value
  }

  private def setElementEnabled(id: HtmlId, enabled: Boolean): Unit = {
    val el = gE(id)
    if (el != null) {
      if (enabled) el.removeAttribute("disabled")
      else el.setAttribute("disabled", "disabled")
    }
  }
