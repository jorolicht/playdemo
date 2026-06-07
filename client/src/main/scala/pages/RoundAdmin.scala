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
import shared.utils.DrawRules
import dialogs.*

object RoundAdmin extends BasePage with JsWrapper:
  def name = PageNameTyp("RoundAdmin")

  val BtnStartPlaying: HtmlId = genId(name)
  val BtnResetInp:     HtmlId = genId(name)
  val BtnResetDrw:     HtmlId = genId(name)
  val BtnResetCfg:     HtmlId = genId(name)
  val BtnDeleteFull:   HtmlId = genId(name)
  val ChkBoxAll:       HtmlId = genId(name)
  val ChkBoxPlayer:    HtmlId = genId(name)
  val SelectDrawMode:  HtmlId = genId(name)
  val LabelDrawCnt:    HtmlId = genId(name)

  //private val IdModeSelect  = "draw-mode-select"
  //private val IdCountLabel  = "draw-count-info"

  def render(param: String = ""): Boolean = 
    // Selection logic
    if (param.nonEmpty) {
      val rId = RoundId(param.toInt)
      services.TourneyDB.tourney.rounds.find(r => r != null && r.id == rId).foreach { r =>
        Global.currentSelection = Global.currentSelection.copy(round = Some(r))
        comps.ContextHeader.render()
      }
    }

    Global.currentSelection.round match
      case Some(r) => 
        comps.ContextHeader.render()
        val comp = Global.currentSelection.competition
        val participants = comp.map(_.pants.toSeq).getOrElse(Seq.empty)
        
        // Active count for rules
        val activeCount = participants.count(_.status == PantStatus.PLAY)
        val modes = DrawRules.getAvailableModes(activeCount)

        setMain(cviews.comps.html.RoundLayout(r, "CFG")(
          cviews.pages.html.RoundAdmin(r, participants, modes)
        ))
        
        // Initial state update
        dom.window.setTimeout(() => updateModes(), 50)
        true
      case None => 
        debug("RoundAdmin: No round selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnStartPlaying` => 
        generateDraw()

      case `BtnResetInp` => 
        debug("Resetting results...")
        loadPage(RoundInput.name, "")

      case `BtnResetDrw` => 
        debug("Resetting draw and results...")
        loadPage(RoundDraw.name, "")

      case `BtnResetCfg` => 
        debug("Resetting full configuration...")
        render()

      case `BtnDeleteFull` => 
        handleDeleteRound()

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
        debug(s"RoundAdmin handleEvent: ${elem.id}")

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
        val option = select.options.item(i + 1)
        if (option != null) {
          option.disabled = !m.isEnabled
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
    val btn = gE(BtnStartPlaying).asInstanceOf[dom.html.Button]
    if (select != null && btn != null) {
      val isDefault = select.value == "UNKN"
      btn.disabled = isDefault
      if (isDefault) btn.classList.add("opacity-50")
      else btn.classList.remove("opacity-50")
    }

  private def generateDraw(): Unit =
    Global.currentSelection.round.foreach { r =>
      val select = gE(SelectDrawMode).asInstanceOf[dom.html.Select]
      val modeStr = select.value
      
      if (modeStr != "UNKN") {
        val cfg = try RoundCfg.valueOf(modeStr) catch { case _: Exception => RoundCfg.UNKN }

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
          r.groups.clear()
          r.rndCfg = cfg
          
          cfg match
            case RoundCfg.RR =>
              val g = Group(1, selectedPants.length, 1, "Gruppe 1", r.noWinSets)
              selectedPants.zipWithIndex.foreach { case (p, i) => g.pants(i) = p }
              r.groups += g
              
            case RoundCfg.KO =>
              val g = Group(1, selectedPants.length, 1, "KO-Baum (Setzung)", r.noWinSets)
              selectedPants.zipWithIndex.foreach { case (p, i) => g.pants(i) = p }
              r.groups += g
              
            case RoundCfg.SW =>
              selectedPants.grouped(2).zipWithIndex.foreach { case (pair, i) =>
                val g = Group(i + 1, pair.length, 1, s"Paarung ${i + 1}", r.noWinSets)
                pair.zipWithIndex.foreach { case (p, j) => g.pants(j) = p }
                r.groups += g
              }
              
            case _ if cfg.typ == RoundTyp.GR =>
              val dist = DrawRules.calculateDistribution(cfg, selectedPants.length)
              var currentPants = selectedPants
              dist.zipWithIndex.foreach { case (size, i) =>
                val groupPants = currentPants.take(size)
                currentPants = currentPants.drop(size)
                val g = Group(i + 1, size, 2, s"Gruppe ${i + 1}", r.noWinSets)
                groupPants.zipWithIndex.foreach { case (p, j) => g.pants(j) = p }
                r.groups += g
              }
            
            case _ => debug(s"Unsupported generation for mode $cfg")

          r.status = RoundStatus.AUS
          services.TourneyDB.tourney.updateRound(r)
          
          // Navigate to Draw page to show results
          loadPage(RoundDraw.name, "")
        }
      }
    }

  private def handleDeleteRound(): Unit =
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
            loadPage(CompetitionInfo.name, "")
          case Left(err) => 
            error(s"Failed to delete round: ${err.msgCode}")
        }
      case _ => debug("Delete cancelled")
    }
