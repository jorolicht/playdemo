package pages
package Stage

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import base.*
import shared.model.*
import scala.scalajs.js

object StageScoreSheet extends BasePage with JsWrapper:
  def name = PageNameTyp("StageScoreSheet")
  val PrintRoundBtn: HtmlId = genId(name)
  val PrintSingleBtn: HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    Global.currentSelection.stage match
      case Some(stage) => 
        comps.ContextHeader.render()
        val comp = Global.currentSelection.competition
        val pants = comp.map(_.pants1Stage.toSeq).getOrElse(Seq.empty)
        
        val matches = stage.matches.toSeq.map { m =>
          (m, formatSnoName(m.stNoA, pants), formatSnoName(m.stNoB, pants))
        }

        setMain(cviews.comps.html.StageLayout(stage, "SCH")(cviews.pages.Stage.html.StageScoreSheet(stage, matches)))
        true
      case None => 
        debug("StageScoreSheet: No stage selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false

  override def handleEvent(elem: org.scalajs.dom.raw.HTMLElement, event: org.scalajs.dom.Event): Unit =
    val id = HtmlId(elem.id)
    if (id.id.startsWith(PrintRoundBtn.id)) {
      val roundNo = elem.getAttribute("data-round").toInt
      printRound(roundNo)
    } else if (id.id.startsWith(PrintSingleBtn.id)) {
      val gameNo = elem.getAttribute("data-game").toInt
      printSingle(gameNo)
    }

  private def printRound(roundNo: Int): Unit =
    val body = dom.document.body
    val printArea = dom.document.getElementById("scoresheet-print-area")
    val cards = dom.document.querySelectorAll(s".printable-card-round-$roundNo")
    
    if (printArea != null && cards.length > 0) {
      printArea.innerHTML = ""
      for (i <- 0 until cards.length) {
        val clone = cards.item(i).cloneNode(true).asInstanceOf[HTMLElement]
        printArea.appendChild(clone)
      }
      
      body.classList.add("print-active")
      dom.window.print()
      dom.window.setTimeout(() => {
        body.classList.remove("print-active")
        printArea.innerHTML = ""
      }, 500)
    }

  private def printSingle(gameNo: Int): Unit =
    val body = dom.document.body
    val printArea = dom.document.getElementById("scoresheet-print-area")
    val card = dom.document.getElementById(s"printable-card-single-$gameNo")
    
    if (printArea != null && card != null) {
      printArea.innerHTML = ""
      val clone = card.cloneNode(true).asInstanceOf[HTMLElement]
      printArea.appendChild(clone)
      
      body.classList.add("print-active")
      dom.window.print()
      dom.window.setTimeout(() => {
        body.classList.remove("print-active")
        printArea.innerHTML = ""
      }, 500)
    }

  def formatSnoName(sno: SNO, pants: Seq[Pant]): String =
    if (sno.isNN) gM("+not_determined")
    else if (sno.isBye) gM("+bye")
    else
      pants.find(_.id == sno) match
        case Some(p) =>
          if (sno.isDouble) {
            val (id1, id2) = sno.doubleId
            val tourney = services.TourneyDB.tourney
            val p1Opt = tourney.players.find(_.id == id1)
            val p2Opt = tourney.players.find(_.id == id2)
            (p1Opt, p2Opt) match
              case (Some(pl1), Some(pl2)) =>
                s"${pl1.firstName}/${pl2.firstName}"
              case _ =>
                p.name.replace(" / ", "/")
          } else {
            val parts = p.name.split(",")
            if (parts.length > 0) parts(0).trim else p.name
          }
        case None =>
          sno.toString
