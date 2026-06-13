package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.{MouseEvent, Event}
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.collection.mutable.ArrayBuffer

import base.*
import shared.MainIds.*
import shared.model.*
import shared.format.*
import shared.utils.DrawRules
import dialogs.*

object StageAdmin extends BasePage with JsWrapper:
  def name = PageNameTyp("StageAdmin")

  val BtnStartDrawing: HtmlId = genId(name)
  val BtnAddDummy:     HtmlId = genId(name)
  val BtnResetInp:     HtmlId = genId(name)
  val BtnResetDrw:     HtmlId = genId(name)
  val BtnResetCfg:     HtmlId = genId(name)
  val BtnDeleteFull:   HtmlId = genId(name)
  val ChkBoxAll:       HtmlId = genId(name)
  val ChkBoxPlayer:    HtmlId = genId(name)
  val SelectDrawMode:  HtmlId = genId(name)
  val LabelDrawCnt:    HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    // Selection logic
    if (param.nonEmpty) {
      val rId = StageId(param.toInt)
      services.TourneyDB.tourney.stages.find(r => r != null && r.id == rId).foreach { r =>
        Global.currentSelection = Global.currentSelection.copy(stage = Some(r))
        comps.ContextHeader.render()
      }
    }

    Global.currentSelection.stage match
      case Some(r) => 
        comps.ContextHeader.render()
        val comp = Global.currentSelection.competition
        val participants = comp.map(_.pants.toSeq).getOrElse(Seq.empty)
        
        // Active count for rules
        val activeCount = participants.count(_.status == PantStatus.PLAY)
        val modes = DrawRules.getAvailableModes(activeCount)

        setMain(cviews.comps.html.StageLayout(r, "CFG")(
          cviews.pages.html.StageAdmin(r, participants, modes)
        ))
        
        // Initial state update
        dom.window.setTimeout(() => updateModes(), 50)
        true
      case None => 
        debug("StageAdmin: No stage selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnStartDrawing` => 
        generateDraw()

      case `BtnAddDummy` =>
        Global.currentSelection.competition.foreach { comp =>
          val dummyIndex = comp.pants.length + 1
          val newId = SNO.single(PlayerId(9000 + dummyIndex))
          val dummyRating = 1200 + scala.util.Random.nextInt(400)
          val newPant = Pant(
            id = newId,
            name = s"Dummy $dummyIndex",
            club = "TTC Dummy",
            rating = dummyRating,
            birthYear = "2000",
            status = PantStatus.PLAY,
            active = true
          )
          comp.pants += newPant
          debug(s"Added dummy player: ${newPant.name}")
          render()
        }

      case `BtnResetInp` => 
        debug("Resetting results...")
        loadPage(StageInput.name, "")

      case `BtnResetDrw` => 
        debug("Resetting draw and results...")
        loadPage(StageDraw.name, "")

      case `BtnResetCfg` => 
        debug("Resetting full configuration...")
        render()

      case `BtnDeleteFull` => 
        handleDeleteStage()

      case `ChkBoxAll` =>
        // ChkBoxAll is now a button
        dom.document.querySelectorAll(".player-draw-check").foreach { node =>
          node.asInstanceOf[dom.html.Input].checked = true
        }
        updateModes(resetSelection = true)

      case id if id.id.startsWith(ChkBoxPlayer.id) =>
        updateModes(resetSelection = true)

      case `SelectDrawMode` =>
        updateButtonState()

      case _ => 
        debug(s"StageAdmin handleEvent: ${elem.id}")

  /**
   * Updates the game mode dropdown and count info based on checkboxes.
   */
  private def updateModes(resetSelection: Boolean = false): Unit =
    val checks = dom.document.querySelectorAll(".player-draw-check")
    var count = 0
    for (i <- 0 until checks.length) {
      if (checks.item(i).asInstanceOf[dom.html.Input].checked) count += 1
    }
    
    val modes = DrawRules.getAvailableModes(count)
    val select = gE(SelectDrawMode).asInstanceOf[dom.html.Select]
    
    if (select != null) {
      if (resetSelection) select.value = "UNKN"
      
      // Update options (skip the first "Please select" option at index 0)
      modes.zipWithIndex.foreach { case (m, i) =>
        val option = select.options.item(i + 1).asInstanceOf[dom.html.Option]
        if (option != null) {
          option.disabled = !m.isEnabled
          val suffix = if (!m.isEnabled) " (nicht mgl.)" else ""
          option.text = m.label + suffix
          if (!m.isEnabled) option.classList.add("text-muted")
          else option.classList.remove("text-muted")
        }
      }
    }

    // Update count label
    val label = gE(LabelDrawCnt)
    if (label != null) label.innerHTML = s"$count Teilnehmer ausgewählt"
    
    updateButtonState()

  private def updateButtonState(): Unit =
    val select = gE(SelectDrawMode).asInstanceOf[dom.html.Select]
    val btn = gE(BtnStartDrawing).asInstanceOf[dom.html.Button]
    if (select != null && btn != null) {
      val isDefault = select.value == "UNKN"
      btn.disabled = isDefault
      if (isDefault) btn.classList.add("opacity-50")
      else btn.classList.remove("opacity-50")
    }

  private def generateDraw(): Unit =
    Global.currentSelection.stage.foreach { r =>
      val select = gE(SelectDrawMode).asInstanceOf[dom.html.Select]
      val modeStr = select.value
      
      if (modeStr != "UNKN") {
        val cfg = try StageConfig.valueOf(modeStr) catch { case _: Exception => StageConfig.UNKN }

        // Read configuration from UI
        val setsSelect = dom.document.getElementById("rnd-sets").asInstanceOf[dom.html.Select]
        if (setsSelect != null) r.noWinSets = setsSelect.value.toInt
        
        // Get selected participants
        val checks = dom.document.querySelectorAll(".player-draw-check")
        val selectedSnos = ArrayBuffer[String]()
        for (i <- 0 until checks.length) {
          val input = checks.item(i).asInstanceOf[dom.html.Input]
          if (input.checked) {
            selectedSnos += input.getAttribute("data-sno")
          }
        }

        val comp = Global.currentSelection.competition.get
        val allPants = comp.pants.toSeq
        val selectedPants = allPants.filter(p => selectedSnos.contains(p.id.toString)).sortBy(-_.rating)

        if (selectedPants.isEmpty) {
          dom.window.alert("Bitte wählen Sie mindestens einen Teilnehmer aus.")
        } else {
          // Generation Logic
          r.stageConfig = cfg
          
          cfg.format match
            case StageFormat.RR => 
              r.data = RoundRobin.init(selectedPants, r.noWinSets)
     
            case StageFormat.KO => 
              r.data = SingleElimination.init(r.id.value, r.name, r.coId.value.toLong, r.noWinSets, selectedPants)
              
            case StageFormat.SW =>
              r.data = SwissSystem.init(selectedPants, r.noWinSets)
              
            case StageFormat.GR =>
              r.data = Groups.init(cfg, selectedPants, r.noWinSets)
        
            case _ => debug(s"Unsupported generation for mode $cfg")

          r.status = StageStatus.AUS
          services.TourneyDB.tourney.updateStage(r)
          
          // Navigate to Draw page to show results
          loadPage(StageDraw.name, "")
        }
      }
    }

  private def handleDeleteStage(): Unit =
    import shared.BoxButton
    val r = Global.currentSelection.stage.get
    val msg = if (r.nextIds.nonEmpty) {
      s"Möchten Sie diese Stage und ALLE ${r.nextIds.length} nachfolgenden Stages wirklich löschen?"
    } else {
      "Möchten Sie diese Stage wirklich löschen?"
    }
    
    DlgMsgbox.show(msg, "Stage löschen", List(BoxButton.Yes, BoxButton.No)).map {
      case BoxButton.Yes => 
        services.TourneyDB.tourney.deleteStage(r.id) match {
          case Right(_) => 
            Global.currentSelection = Global.currentSelection.copy(stage = None)
            comps.ContextHeader.render()
            loadPage(CompetitionInfo.name, "")
          case Left(err) => 
            error(s"Failed to delete stage: ${err.msgCode}")
        }
      case _ => debug("Delete cancelled")
    }
