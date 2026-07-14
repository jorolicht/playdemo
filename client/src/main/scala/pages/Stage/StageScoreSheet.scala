package pages
package Stage

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import base.*
import shared.model.*
import shared.format.StageHelper
import scala.scalajs.js
import scala.scalajs.js.annotation.*

trait QRCodeParam extends js.Object {
  val width: Int
  val height: Int
}

@js.native
@JSGlobal
class QRCode(elem: HTMLElement, param: QRCodeParam) extends js.Object {
  def makeCode(url: String): js.Any = js.native
}

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
        
        var activeSecParam: Option[Int] = None
        var isPrintRound = false
        var isPrintSingle = false
        var targetGameNo = 0
        var targetRoundNo = 0

        if (param.nonEmpty) {
          if (param.startsWith("round_")) {
            try {
              targetRoundNo = param.substring(6).toInt
              activeSecParam = Some(targetRoundNo)
              isPrintRound = true
            } catch { case _: Exception => }
          } else {
            try {
              targetGameNo = param.toInt
              isPrintSingle = true
              stage.matches.find(_.gameNo == targetGameNo).foreach { m =>
                stage.stageConfig.format match
                  case StageFormat.GR => activeSecParam = Some(m.asInstanceOf[MEntryGr].grId)
                  case _ => activeSecParam = Some(m.round)
              }
            } catch { case _: Exception => }
          }
        }

        // Filter matches to only generate and print the selected round or current active round
        val filteredMatches = if (isPrintRound) {
          stage.matches.toSeq.filter(_.round == targetRoundNo)
        } else {
          activeSecParam match {
            case Some(secId) =>
              stage.stageConfig.format match {
                case StageFormat.GR =>
                  stage.matches.toSeq.filter(m => m.isInstanceOf[MEntryGr] && m.asInstanceOf[MEntryGr].grId == secId)
                case _ =>
                  stage.matches.toSeq.filter(_.round == secId)
              }
            case None =>
              val defaultRound = stage.matches.filterNot(_.finished).map(_.round).minOption
                .getOrElse(stage.matches.map(_.round).maxOption.getOrElse(1))
              
              stage.stageConfig.format match {
                case StageFormat.GR =>
                  stage.matches.toSeq.filter(m => m.isInstanceOf[MEntryGr] && m.asInstanceOf[MEntryGr].grId == 1)
                case _ =>
                  stage.matches.toSeq.filter(_.round == defaultRound)
              }
          }
        }

        val mList = filteredMatches.map { m =>
          val (nameA, clubA, _) = getPlayerInfo(m.stNoA, pants)
          val (nameB, clubB, _) = getPlayerInfo(m.stNoB, pants)
          val (info1, info2) = getInfoStrings(m, stage)
          (m, nameA, nameB, clubA, clubB, info1, info2)
        }

        setMain(cviews.comps.html.StageLayout(stage, "SCH")(cviews.pages.Stage.html.StageScoreSheet(stage, mList, activeSecParam)))
        
        // Load QRCode library and generate QRCodes
        loadQRCodeLib { () =>
          generateAllQRCodes(stage)
          
          if (isPrintRound) {
            dom.window.setTimeout(() => printRound(targetRoundNo), 300)
          } else if (isPrintSingle) {
            dom.window.setTimeout(() => printSingle(targetGameNo), 300)
          }
        }
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
        val original = cards.item(i).asInstanceOf[HTMLElement]
        val clone = original.cloneNode(true).asInstanceOf[HTMLElement]
        copyCanvasContents(original, clone)
        printArea.appendChild(clone)
      }
      
      body.classList.add("print-active")
      dom.window.setTimeout(() => {
        dom.window.print()
        dom.window.setTimeout(() => {
          body.classList.remove("print-active")
          printArea.innerHTML = ""
        }, 500)
      }, 150)
    }

  private def printSingle(gameNo: Int): Unit =
    val body = dom.document.body
    val printArea = dom.document.getElementById("scoresheet-print-area")
    val card = dom.document.getElementById(s"printable-card-single-$gameNo")
    
    if (printArea != null && card != null) {
      printArea.innerHTML = ""
      val original = card.asInstanceOf[HTMLElement]
      val clone = original.cloneNode(true).asInstanceOf[HTMLElement]
      copyCanvasContents(original, clone)
      printArea.appendChild(clone)
      
      body.classList.add("print-active")
      dom.window.setTimeout(() => {
        dom.window.print()
        dom.window.setTimeout(() => {
          body.classList.remove("print-active")
          printArea.innerHTML = ""
        }, 500)
      }, 150)
    }

  private def copyCanvasContents(original: HTMLElement, clone: HTMLElement): Unit =
    val origCanvases = original.querySelectorAll("canvas")
    val cloneCanvases = clone.querySelectorAll("canvas")
    for (i <- 0 until origCanvases.length) {
      if (i < cloneCanvases.length) {
        val origCanvas = origCanvases.item(i).asInstanceOf[dom.raw.HTMLCanvasElement]
        val cloneCanvas = cloneCanvases.item(i).asInstanceOf[dom.raw.HTMLCanvasElement]
        cloneCanvas.width = origCanvas.width
        cloneCanvas.height = origCanvas.height
        val ctx = cloneCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
        if (ctx != null) {
          ctx.drawImage(origCanvas, 0, 0)
        }
      }
    }

  def getPlayerInfo(sno: SNO, pants: Seq[Pant]): (String, String, String) =
    if (sno.isNN) (gM("+not_determined"), "", "")
    else if (sno.isBye) (gM("+bye"), "", "")
    else
      pants.find(_.id == sno) match
        case Some(p) =>
          val name = if (sno.isDouble) {
            val (id1, id2) = sno.doubleId
            val tourney = services.TourneyDB.tourney
            val p1Opt = tourney.players.find(_.id == id1)
            val p2Opt = tourney.players.find(_.id == id2)
            (p1Opt, p2Opt) match
              case (Some(pl1), Some(pl2)) => s"${pl1.firstName}/${pl2.firstName}"
              case _ => p.name.replace(" / ", "/")
          } else {
            val parts = p.name.split(",")
            if (parts.length > 0) parts(0).trim else p.name
          }
          (name, p.club, if (p.rating > 0) p.rating.toString else "")
        case None =>
          (sno.toString, "", "")

  def getInfoStrings(m: MEntry, stage: Stage): (String, String) =
    stage.stageConfig.format match
      case StageFormat.GR =>
        val grId = m.asInstanceOf[MEntryGr].grId
        val round = m.asInstanceOf[MEntryGr].round
        (s"Gruppe ${StageHelper.cvrt2ExcelCol(grId)}", s"Runde $round")
      case StageFormat.RR =>
        val round = m.asInstanceOf[MEntryGr].round
        ("", s"Runde $round")
      case StageFormat.KO =>
        val round = m.asInstanceOf[MEntryKo].round
        val nameStr = if (round > 0) gM(s"competition.koRound.$round") else ""
        (nameStr, "")
      case StageFormat.SW =>
        ("", s"Runde ${m.round}")
      case _ =>
        ("", "")

  private def getQRCodeClass(): js.Dynamic =
    val parts = Seq("QR", "Code")
    val globalObj = dom.window.asInstanceOf[js.Dynamic]
    globalObj.selectDynamic(parts.mkString)

  private def loadQRCodeLib(callback: () => Unit): Unit =
    val qrCodeClass = getQRCodeClass()
    if (!js.isUndefined(qrCodeClass)) {
      callback()
    } else {
      val script = dom.document.createElement("script").asInstanceOf[dom.html.Script]
      script.src = "https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"
      script.onload = (_: dom.Event) => callback()
      dom.document.head.appendChild(script)
    }

  private def generateAllQRCodes(stage: Stage): Unit =
    val t = services.TourneyDB.tourney
    val comp = t.competitions.filter(_ != null).find(_.id == stage.coId).get
    val slugName = t.slug.split('/').lastOption.getOrElse(t.slug)
    val slugParam = s"${slugName.trim}-${t.wpId}"
    val playBase = {
      val home = if (Global.homeUrl.nonEmpty) Global.homeUrl.trim else dom.window.location.origin
      val base = if (home.endsWith("/")) home.dropRight(1) else home
      s"$base/srv"
    }

    val qrCodeClass = getQRCodeClass()
    if (js.isUndefined(qrCodeClass)) {
      debug("QR Code library not loaded yet.")
      return
    }

    val grids = dom.document.querySelectorAll(".refereeGrid")
    for (i <- 0 until grids.length) {
      val grid = grids.item(i).asInstanceOf[HTMLElement]
      val qrElem = grid.querySelector(".RefereeQRCode").asInstanceOf[HTMLElement]
      val linkElem = grid.querySelector(".referee-qr-link").asInstanceOf[dom.raw.HTMLAnchorElement]

      if (qrElem != null && qrElem.innerHTML.trim.isEmpty) {
        val gameNo = qrElem.getAttribute("data-game-no")
        val nameA = qrElem.getAttribute("data-name-a")
        val nameB = qrElem.getAttribute("data-name-b")
        val info1 = qrElem.getAttribute("data-info-1")
        val info2 = qrElem.getAttribute("data-info-2")

        val roundInfo = s"${Seq(info1, info2).filter(s => s != null && s.nonEmpty).mkString(" / ")} (Spiel $gameNo)"
        val players = s"$nameA - $nameB"

        val refereeAddr = s"$playBase/referee?" +
          s"slug=${js.URIUtils.encodeURIComponent(slugParam)}&" +
          s"tourneyName=${js.URIUtils.encodeURIComponent(t.name)}&" +
          s"competition=${js.URIUtils.encodeURIComponent(comp.name)}&" +
          s"stage=${js.URIUtils.encodeURIComponent(stage.name)}&" +
          s"roundInfo=${js.URIUtils.encodeURIComponent(roundInfo)}&" +
          s"players=${js.URIUtils.encodeURIComponent(players)}&" +
          s"winSets=${stage.noWinSets}"

        val qrCodeParam = new QRCodeParam { val width = 80; val height = 80 }
        try {
          val qrCode = js.Dynamic.newInstance(qrCodeClass)(qrElem, qrCodeParam)
          qrCode.makeCode(refereeAddr)
        } catch {
          case e: Throwable =>
            println(s"Error generating QR Code: ${e.getMessage}")
        }

        if (linkElem != null) {
          linkElem.href = refereeAddr
        }
      }
    }

  // set referee page for a stage (round/group)
  def setPage(stage: Stage): Unit =
    val elem = gE(HtmlId(s"RefereeContent_${stage.coId.value}_${stage.id.value}"))
    if (elem != null) {
      debug(s"Referee init: coId: ${stage.coId.value} id: ${stage.id.value}")
      
      val noSchiris = stage.matches.length
      setHtml(elem, cviews.pages.Referee.html.RefereeCard(stage, noSchiris))
      
      val comp = services.TourneyDB.tourney.competitions.filter(_ != null).find(_.id == stage.coId).get
      val pants = comp.pants1Stage.toSeq
      
      loadQRCodeLib { () =>
        stage.matches.zipWithIndex.foreach { case (m, idx) =>
          val (nameA, clubA, _) = getPlayerInfo(m.stNoA, pants)
          val (nameB, clubB, _) = getPlayerInfo(m.stNoB, pants)
          val compTypStr = if (m.coTyp != null) gM(m.coTyp.msgCode) else ""
          
          val (info1, info2) = stage.stageConfig.format match {
            case StageFormat.GR =>
              val grId = m.asInstanceOf[MEntryGr].grId
              val round = m.asInstanceOf[MEntryGr].round
              (s"Gruppe ${StageHelper.cvrt2ExcelCol(grId)}", s"Runde $round")
            case StageFormat.RR =>
              val round = m.asInstanceOf[MEntryGr].round
              ("", s"Runde $round")
            case StageFormat.KO =>
              val round = m.asInstanceOf[MEntryKo].round
              val nameStr = if (round > 0) gM(s"competition.koRound.$round") else ""
              (nameStr, "")
            case _ =>
              ("", "")
          }
          
          val noteElem = gE(HtmlId(s"RefereeNote_${stage.coId.value}_${stage.id.value}_${idx + 1}"))
          if (noteElem != null) {
            setHtml(noteElem, cviews.pages.Referee.html.RefereeCardSingle(
              stage, m.gameNo,
              services.TourneyDB.tourney.name,
              comp.startDate.toString,
              stage.name,
              compTypStr,
              info1, info2,
              nameA, nameB, clubA, clubB
            ))
          }
        }
        
        generateAllQRCodes(stage)
      }
    }

