package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.MainIds.*
import shared.model.*
import dialogs.*

object RoundCFG extends BasePage with JsWrapper:
  def name = PageNameTyp("RoundCFG")

  val BtnStartPlaying: HtmlId = genId(name)
  val BtnResetInp:     HtmlId = genId(name)
  val BtnResetDrw:     HtmlId = genId(name)
  val BtnResetCfg:     HtmlId = genId(name)
  val BtnDeleteFull:   HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    // If param is provided, try to find and select that round
    if (param.nonEmpty) {
      val rId = RoundId(param.toInt)
      services.TourneyDB.tourney.rounds.find(r => r != null && r.id == rId).foreach { r =>
        Global.currentSelection = Global.currentSelection.copy(round = Some(r))
        comps.ContextHeader.render()
      }
    }

    Global.currentSelection.round match
      case Some(r) => 
        val comp = Global.currentSelection.competition
        val participants = comp.map(_.pants.toSeq).getOrElse(Seq.empty)
        setMain(cviews.comps.html.RoundLayout(r, "CFG")(cviews.pages.html.RoundCFG(r, participants)))
        true
      case None => 
        debug("RoundCFG: No round selected, redirecting to Competition Info")
        loadPage(InfoCompetition.name, "")
        false

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnStartPlaying` => 
        // Logic to start playing (move to DRW or INP based on status)
        loadPage(RoundDRW.name, "")

      case `BtnResetInp` => 
        debug("Resetting results...")
        loadPage(RoundINP.name, "")

      case `BtnResetDrw` => 
        debug("Resetting draw and results...")
        loadPage(RoundDRW.name, "")

      case `BtnResetCfg` => 
        debug("Resetting full configuration...")
        render() // Stay here

      case `BtnDeleteFull` => 
        import shared.BoxValues.*
        val r = Global.currentSelection.round.get
        val msg = if (r.nextIds.nonEmpty) {
          s"Möchten Sie diese Runde und ALLE ${r.nextIds.length} nachfolgenden Runden wirklich löschen?"
        } else {
          "Möchten Sie diese Runde wirklich löschen?"
        }
        
        DlgMsgbox.show(msg, "Runde löschen", List(Yes, No)).map {
          case Yes => 
            services.TourneyDB.tourney.deleteRound(r.id) match {
              case Right(_) => 
                Global.currentSelection = Global.currentSelection.copy(round = None)
                comps.ContextHeader.render()
                loadPage(InfoCompetition.name, "")
              case Left(err) => 
                error(s"Failed to delete round: ${err.msgCode}")
            }
          case _ => debug("Delete cancelled")
        }

      case _ => 
        debug(s"RoundCFG handleEvent: ${elem.id}")
