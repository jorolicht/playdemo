package pages
package Stage

import org.scalajs.dom
import base.*
import shared.model.*
import scala.scalajs.js

object StageResult extends BasePage with JsWrapper:
  def name = PageNameTyp("StageResult")
  val NextStageBtn:   HtmlId = genId(name)

  private var currentStartIndex: Int = 0
  private var resizeListener: Option[js.Function1[dom.Event, Unit]] = None
  private var keydownListener: Option[js.Function1[dom.KeyboardEvent, Unit]] = None

  def render(param: String = ""): Boolean = 
    Global.currentSelection.stage match
      case Some(stage) => 
        comps.ContextHeader.render()
        stage.data match
          case StageData.GroupsStage(groups) => 
            setMain(cviews.comps.html.StageLayout(stage, "RES")(cviews.pages.Stage.Result.html.Groups(stage, groups.toSeq)))
            true
          case StageData.KnockoutStage(ko) => 
            setMain(cviews.comps.html.StageLayout(stage, "RES")(cviews.pages.Stage.Result.html.SingleElimination(stage)))
            initSlidingWindow()
            true
          case StageData.SwissStage(sw) => 
            // TODO: Implement Swiss System Result View
            setMain(cviews.comps.html.StageLayout(stage, "RES")(play.twirl.api.Html("<span>Schweizer System Ergebnisse (Platzhalter)</span>")))
            true
          case StageData.RoundRobinStage(rr) => 
            setMain(cviews.comps.html.StageLayout(stage, "RES")(cviews.pages.Stage.Result.html.RoundRobin(stage, Seq(rr))))
            true
      case None => 
        debug("StageResult: No stage selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false

  private def initSlidingWindow(): Unit =
    currentStartIndex = 0
    val wrapper = dom.document.getElementById("tournamentWrapper").asInstanceOf[dom.html.Div]
    val container = dom.document.getElementById("tournamentContainer").asInstanceOf[dom.html.Div]
    
    if (wrapper == null || container == null) return

    val rounds = dom.document.querySelectorAll(".bracket-column")
    val totalRounds = rounds.length
    if (totalRounds == 0) return

    def parseFloat(s: String): Double =
      val clean = s.replaceAll("[^0-9.]", "")
      if (clean.isEmpty) 0.0 else clean.toDouble

    def getRoundWidth(): Double =
      val roundElem = rounds.item(0).asInstanceOf[dom.html.Div]
      if (roundElem == null) return 320.0
      val containerStyle = dom.window.getComputedStyle(wrapper)
      val gapStr = containerStyle.getPropertyValue("gap")
      val gap = if (gapStr != null && gapStr.nonEmpty) parseFloat(gapStr) else 40.0
      roundElem.offsetWidth.toDouble + gap

    def getVisibleRoundsCount(): Int =
      val containerWidth = container.offsetWidth.toDouble
      val roundWidth = getRoundWidth()
      val count = scala.math.floor(containerWidth / roundWidth).toInt
      if (count < 1) 1 else count

    def updateView(): Unit =
      val visibleRounds = getVisibleRoundsCount()
      val roundWidth = getRoundWidth()
      
      if (currentStartIndex + visibleRounds > totalRounds) {
        currentStartIndex = scala.math.max(0, totalRounds - visibleRounds)
      }
      
      val offset = -(currentStartIndex * roundWidth)
      wrapper.style.transform = s"translateX(${offset}px)"
      
      val firstMatchElem = dom.document.querySelector(".bracket-match").asInstanceOf[dom.html.Div]
      val H = if (firstMatchElem != null) firstMatchElem.offsetHeight.toDouble else 130.0
      
      val firstContent = dom.document.querySelector(".bracket-round-content").asInstanceOf[dom.html.Div]
      val G_0 = if (firstContent != null) {
        val computedStyle = dom.window.getComputedStyle(firstContent)
        val gapStr = computedStyle.getPropertyValue("gap")
        if (gapStr != null && gapStr.nonEmpty) parseFloat(gapStr) else 16.0
      } else 16.0

      for (i <- 0 until totalRounds) {
        val col = rounds.item(i).asInstanceOf[dom.html.Div]
        val content = col.querySelector(".bracket-round-content").asInstanceOf[dom.html.Div]
        if (content != null) {
          val r = scala.math.max(0, i - currentStartIndex)
          
          val gap = if (r == 0) G_0 else (scala.math.pow(2.0, r.toDouble) * (H + G_0) - H)
          val marginTop = if (r == 0) 0.0 else ((scala.math.pow(2.0, r.toDouble) - 1.0) / 2.0 * (H + G_0))
          
          content.style.setProperty("justify-content", "flex-start")
          content.style.setProperty("gap", s"${gap}px")
          
          val children = content.children
          for (j <- 0 until children.length) {
            val child = children.item(j).asInstanceOf[dom.html.Element]
            if (child != null) {
              if (j == 0) {
                child.style.setProperty("margin-top", s"${marginTop}px")
              } else {
                child.style.removeProperty("margin-top")
              }
            }
          }
          
          val matches = content.querySelectorAll(".bracket-match")
          val isFirstColumn = i == 0

          for (j <- 0 until matches.length) {
            val matchElem = matches.item(j).asInstanceOf[dom.html.Div]
            if (matchElem != null) {
              val isThirdPlaceCard = matchElem.classList.contains("third-place-card")
              
              if (r > 0 && !isFirstColumn && !isThirdPlaceCard) {
                val D = scala.math.pow(2.0, (r - 1).toDouble) * (H + G_0) / 2.0
                matchElem.classList.add("has-connector")
                matchElem.style.setProperty("--parent-distance", s"${D}px")
              } else {
                matchElem.classList.remove("has-connector")
                matchElem.style.removeProperty("--parent-distance")
              }
            }
          }
        }
      }
      
      val prevBtn = dom.document.getElementById("prevBtn").asInstanceOf[dom.html.Button]
      val nextBtn = dom.document.getElementById("nextBtn").asInstanceOf[dom.html.Button]
      
      if (prevBtn != null) {
        prevBtn.disabled = currentStartIndex == 0
      }
      if (nextBtn != null) {
        nextBtn.disabled = currentStartIndex + visibleRounds >= totalRounds
      }

    val prevBtn = dom.document.getElementById("prevBtn").asInstanceOf[dom.html.Button]
    val nextBtn = dom.document.getElementById("nextBtn").asInstanceOf[dom.html.Button]

    if (prevBtn != null) {
      prevBtn.addEventListener("click", (_: dom.Event) => {
        if (currentStartIndex > 0) {
          currentStartIndex -= 1
          updateView()
        }
      })
    }
    if (nextBtn != null) {
      nextBtn.addEventListener("click", (_: dom.Event) => {
        val visibleRounds = getVisibleRoundsCount()
        if (currentStartIndex + visibleRounds < totalRounds) {
          currentStartIndex += 1
          updateView()
        }
      })
    }

    resizeListener.foreach(l => dom.window.removeEventListener("resize", l))
    keydownListener.foreach(l => dom.document.removeEventListener("keydown", l))

    val rListener: js.Function1[dom.Event, Unit] = (_: dom.Event) => updateView()
    val kListener: js.Function1[dom.KeyboardEvent, Unit] = (event: dom.KeyboardEvent) => {
      if (event.key == "ArrowLeft" && currentStartIndex > 0) {
        currentStartIndex -= 1
        updateView()
      } else if (event.key == "ArrowRight") {
        val visibleRounds = getVisibleRoundsCount()
        if (currentStartIndex + visibleRounds < totalRounds) {
          currentStartIndex += 1
          updateView()
        }
      }
    }

    resizeListener = Some(rListener)
    keydownListener = Some(kListener)

    dom.window.addEventListener("resize", rListener)
    dom.document.addEventListener("keydown", kListener)

    dom.window.setTimeout(() => updateView(), 100)

  override def handleEvent(elem: org.scalajs.dom.raw.HTMLElement, event: org.scalajs.dom.Event): Unit =
    HtmlId(elem.id) match
      case `NextStageBtn` =>
        import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
        Global.currentSelection.stage.foreach { currentStage =>
          val comp = Global.currentSelection.competition.get
          val existingStages = services.TourneyDB.tourney.stages.toSeq.filter(s => s != null && s.coId == comp.id && !s.deleted)
          
          dialogs.DlgStageStart.show(existingStages, Some(currentStage.id)).map {
            case Right(res) =>
              val initialNoPlayers = if (existingStages.isEmpty) comp.pants1Stage.count(_.active) else 0

              services.TourneyDB.tourney.addStage(
                coId = comp.id, 
                prefId = res.prefId, 
                name = res.name, 
                stageConfig = StageConfig.CFG, 
                size = 8, 
                noPlayers = initialNoPlayers
              ) match {
                case Right(newStage) => 
                  val updatedCompOpt = services.TourneyDB.tourney.competitions.find(c => c != null && c.id == comp.id)
                  Global.currentSelection = Global.currentSelection.copy(stage = Some(newStage), competition = updatedCompOpt)
                  comps.ContextHeader.render()
                  loadPage(PageNameTyp("StageAdmin"), "")
                case Left(err) => 
                  error(s"Failed to start stage: ${err.msgCode}")
              }
            case _ => debug("Start stage cancelled")
          }
        }
      case _ =>
