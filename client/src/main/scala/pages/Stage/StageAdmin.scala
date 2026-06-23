package pages
package Stage

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

/**
 * Page handling the configuration and administration of a game stage.
 * Allows drawing generation, selecting participants, resetting data, and deleting stages.
 */
object StageAdmin extends BasePage with JsWrapper:
  def name = PageNameTyp("StageAdmin")

  /** Button to generate/start the draw. */
  val BtnStartDrawing: HtmlId = genId(name)
  /** Button to add a dummy player to the stage. */
  val BtnAddDummy:     HtmlId = genId(name)
  /** Button to reset input results. */
  val BtnResetInp:     HtmlId = genId(name)
  /** Button to reset the draw. */
  val BtnResetDrw:     HtmlId = genId(name)
  /** Button to reset configuration. */
  val BtnResetCfg:     HtmlId = genId(name)
  /** Button to delete the entire stage. */
  val BtnDeleteFull:   HtmlId = genId(name)
  /** Checkbox to select all available players. */
  val ChkBoxAll:       HtmlId = genId(name)
  /** Checkbox prefix for selecting/deselecting individual players. */
  val ChkBoxPlayer:    HtmlId = genId(name)
  /** Selection dropdown for game draw mode. */
  val SelectDrawMode:  HtmlId = genId(name)
  /** Label displaying count of selected players. */
  val LabelDrawCnt:    HtmlId = genId(name)

  val RadAll:          HtmlId = genId(name)
  val RadTop:          HtmlId = genId(name)
  val RadBottom:       HtmlId = genId(name)
  val BtnAddPlayer:    HtmlId = genId(name)
  val BtnSortSelected: HtmlId = genId(name)

  var currentFilterOption: String = "ALL"
  var sortBySelected: Boolean = false
  private var lastStageId: Option[StageId] = None

  def render(param: String = ""): Boolean = 
    // Selection logic
    if (param.nonEmpty) {
      val rId = StageId(param.toInt)
      services.TourneyDB.tourney.stages.find(r => r != null && r.id == rId).foreach { r =>
        Global.currentSelection = Global.currentSelection.copy(stage = Some(r))
        currentFilterOption = "ALL"
        sortBySelected = false
        lastStageId = Some(rId)
        comps.ContextHeader.render()
      }
    }

    Global.currentSelection.stage match
      case Some(r) => 
        comps.ContextHeader.render()
        val comp = Global.currentSelection.competition
        
        if (!lastStageId.contains(r.id)) {
          currentFilterOption = "ALL"
          sortBySelected = false
          lastStageId = Some(r.id)
        }

        val participants = comp.map { c =>
          val all = c.pants1Stage.toSeq.filter(_.active)
          if (sortBySelected) {
            all.sortBy(p => (p.status != PantStatus.PLAY, p.name.toLowerCase))
          } else {
            all
          }
        }.getOrElse(Seq.empty)

        // Dynamic detection of currentFilterOption from active selection
        val prevStageOpt = r.prefId.flatMap { pId =>
          services.TourneyDB.tourney.stages.find(s => s != null && s.id == pId)
        }
        currentFilterOption = prevStageOpt match {
          case Some(prevStage) =>
            val checkedPants = participants.filter(_.status == PantStatus.PLAY)
            val topHalfPants = participants.filter { p =>
              prevStage.data match {
                case StageData.GroupsStage(groups) =>
                  groups.find(_.pants.exists(gp => gp != null && gp.id == p.id)).exists { g =>
                    val place = g.pants.find(gp => gp != null && gp.id == p.id).map(_.place._1).getOrElse(0)
                    val maxPlace = g.pants.count(_ != null)
                    val threshold = (maxPlace + 1) / 2
                    place > 0 && place <= threshold
                  }
                case _ => false
              }
            }
            val bottomHalfPants = participants.filter { p =>
              prevStage.data match {
                case StageData.GroupsStage(groups) =>
                  groups.find(_.pants.exists(gp => gp != null && gp.id == p.id)).exists { g =>
                    val place = g.pants.find(gp => gp != null && gp.id == p.id).map(_.place._1).getOrElse(0)
                    val maxPlace = g.pants.count(_ != null)
                    val threshold = (maxPlace + 1) / 2
                    place > threshold
                  }
                case _ => false
              }
            }
            if (checkedPants.nonEmpty && checkedPants.toSet == topHalfPants.toSet) "TOP"
            else if (checkedPants.nonEmpty && checkedPants.toSet == bottomHalfPants.toSet) "BOTTOM"
            else "ALL"
          case None => "ALL"
        }
        
        // Active count for rules
        val activeCount = participants.count(_.status == PantStatus.PLAY)
        val modes = DrawRules.getAvailableModes(activeCount, base.Global.lang)

        setMain(cviews.comps.html.StageLayout(r, "CFG")(
          cviews.pages.Stage.html.StageAdmin(r, participants, modes)
        ))
        
        // Initial state update
        dom.window.setTimeout(() => updateModes(), 50)
        true
      case None => 
        debug("StageAdmin: No stage selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    val isFin = Global.currentSelection.competition.exists(_.status == CompStatus.FIN)
    if (isFin) {
      debug(s"StageAdmin handleEvent blocked for event ${elem.id} because competition is finalized.")
      return
    }
    HtmlId(elem.id) match
      case `BtnStartDrawing` => 
        generateDraw()

      case `BtnAddDummy` =>
        Global.currentSelection.competition.foreach { comp =>
          val dummyIndex = comp.pants1Stage.length + 1
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
          comp.pants1Stage += newPant
          debug(s"Added dummy player: ${newPant.name}")
          render()
        }

      case `BtnResetInp` => 
        import shared.BoxButton
        Global.currentSelection.stage.foreach { stage =>
          DlgMsgbox.show("Möchten Sie wirklich alle Ergebnisse dieser Stage löschen?", "Ergebnisse löschen", List(BoxButton.Yes, BoxButton.No)).map {
            case BoxButton.Yes =>
              stage.resetMatchesPropagate() match {
                case Left(err) => 
                  error(s"StageAdmin: resetMatchesPropagate failed: ${err.msgCode}")
                case Right(_) =>
                  services.TourneyDB.tourney.updateStage(stage) match {
                    case Right(updatedStage) =>
                      Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
                      debug("StageAdmin: Results successfully reset.")
                      loadPage(StageInput.name, "")
                    case Left(err) =>
                      error(s"StageAdmin: Failed to update stage in database: ${err.msgCode}")
                  }
              }
            case _ => 
              debug("StageAdmin: Reset cancelled")
          }
        }

      case `BtnResetDrw` => 
        import shared.BoxButton
        Global.currentSelection.stage.foreach { stage =>
          DlgMsgbox.show("Möchten Sie wirklich die gesamte Auslosung löschen? Alle Ergebnisse werden gelöscht!", "Auslosung löschen", List(BoxButton.Yes, BoxButton.No)).map {
            case BoxButton.Yes =>
              stage.matches.clear()
              stage.status = StageStatus.CFG
              services.TourneyDB.tourney.updateStage(stage) match {
                case Right(updatedStage) =>
                  Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
                  debug("StageAdmin: Draw successfully deleted.")
                  render()
                case Left(err) =>
                  error(s"StageAdmin: Failed to update stage in database: ${err.msgCode}")
              }
            case _ => 
              debug("StageAdmin: Reset draw cancelled")
          }
        }

      case `BtnResetCfg` => 
        import shared.BoxButton
        Global.currentSelection.stage.foreach { stage =>
          DlgMsgbox.show("Möchten Sie wirklich die gesamte Konfiguration dieser Stage löschen und zurücksetzen?", "Konfiguration löschen", List(BoxButton.Yes, BoxButton.No)).map {
            case BoxButton.Yes =>
              stage.stageConfig = StageConfig.CFG
              stage.status = StageStatus.CFG
              stage.size = 0
              stage.noPlayers = 0
              stage.noWinSets = 3
              stage.data = StageData.GroupsStage(ArrayBuffer.empty)
              stage.matches.clear()
              
              services.TourneyDB.tourney.updateStage(stage) match {
                case Right(updatedStage) =>
                  Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
                  debug("StageAdmin: Configuration successfully reset.")
                  render()
                case Left(err) =>
                  error(s"StageAdmin: Failed to reset stage config: ${err.msgCode}")
              }
            case _ => 
              debug("StageAdmin: Reset config cancelled")
          }
        }

      case `BtnDeleteFull` => 
        handleDeleteStage()

      case `BtnSortSelected` =>
        sortBySelected = !sortBySelected
        render()

      case `ChkBoxAll` =>
        Global.currentSelection.competition.foreach { comp =>
          comp.pants1Stage.foreach { p =>
            p.status = PantStatus.PLAY
          }
          services.TourneyDB.tourney.updateCompetition(comp) match {
            case Right(updatedComp) =>
              Global.currentSelection = Global.currentSelection.copy(competition = Some(updatedComp))
            case _ =>
          }
        }
        render()

      case `RadAll` =>
        currentFilterOption = "ALL"
        Global.currentSelection.competition.foreach { comp =>
          comp.pants1Stage.foreach { p =>
            if (p.active) {
              p.status = PantStatus.PLAY
            }
          }
          services.TourneyDB.tourney.updateCompetition(comp) match {
            case Right(updatedComp) =>
              Global.currentSelection = Global.currentSelection.copy(competition = Some(updatedComp))
            case _ =>
          }
        }
        render()

      case `RadTop` =>
        currentFilterOption = "TOP"
        Global.currentSelection.stage.flatMap { stage =>
          stage.prefId.flatMap { pId =>
            services.TourneyDB.tourney.stages.find(s => s != null && s.id == pId)
          }
        }.foreach { prevStage =>
          Global.currentSelection.competition.foreach { comp =>
            comp.pants1Stage.foreach { p =>
              if (p.active) {
                val isTop = prevStage.data match {
                  case StageData.GroupsStage(groups) =>
                    groups.find(_.pants.exists(gp => gp != null && gp.id == p.id)).exists { g =>
                      val place = g.pants.find(gp => gp != null && gp.id == p.id).map(_.place._1).getOrElse(0)
                      val maxPlace = g.pants.count(_ != null)
                      val threshold = (maxPlace + 1) / 2
                      place > 0 && place <= threshold
                    }
                  case _ => false
                }
                p.status = if (isTop) PantStatus.PLAY else PantStatus.REGI
              }
            }
            services.TourneyDB.tourney.updateCompetition(comp) match {
              case Right(updatedComp) =>
                Global.currentSelection = Global.currentSelection.copy(competition = Some(updatedComp))
              case _ =>
            }
          }
        }
        render()

      case `RadBottom` =>
        currentFilterOption = "BOTTOM"
        Global.currentSelection.stage.flatMap { stage =>
          stage.prefId.flatMap { pId =>
            services.TourneyDB.tourney.stages.find(s => s != null && s.id == pId)
          }
        }.foreach { prevStage =>
          Global.currentSelection.competition.foreach { comp =>
            comp.pants1Stage.foreach { p =>
              if (p.active) {
                val isBottom = prevStage.data match {
                  case StageData.GroupsStage(groups) =>
                    groups.find(_.pants.exists(gp => gp != null && gp.id == p.id)).exists { g =>
                      val place = g.pants.find(gp => gp != null && gp.id == p.id).map(_.place._1).getOrElse(0)
                      val maxPlace = g.pants.count(_ != null)
                      val threshold = (maxPlace + 1) / 2
                      place > threshold
                    }
                  case _ => false
                }
                p.status = if (isBottom) PantStatus.PLAY else PantStatus.REGI
              }
            }
            services.TourneyDB.tourney.updateCompetition(comp) match {
              case Right(updatedComp) =>
                Global.currentSelection = Global.currentSelection.copy(competition = Some(updatedComp))
              case _ =>
            }
          }
        }
        render()

      case `BtnAddPlayer` =>
        val tourney = services.TourneyDB.tourney
        val comp = Global.currentSelection.competition.get
        val activeSnos = comp.pants1Stage.filter(_.active).map(_.id).toSet
        val sortedPlayers = tourney.players.toSeq
          .filterNot(p => activeSnos.contains(SNO.single(p.id)))
          .sortBy(_.displayName.toLowerCase)
        val clubsMap = tourney.clubs.map(c => c.id.toInt -> c.name).toMap
        
        dialogs.DlgAddPlayer.show(sortedPlayers, clubsMap).map {
          case Right(selectedPlayer) =>
            Global.currentSelection.competition.foreach { comp =>
              val sno = SNO.single(selectedPlayer.id)
              val existingOpt = comp.pants1Stage.find(_.id == sno)
              existingOpt match {
                case Some(existing) =>
                  existing.active = true
                  existing.status = PantStatus.PLAY
                case None =>
                  val newPant = Pant(
                    id = sno,
                    name = selectedPlayer.displayName,
                    club = tourney.clubs.find(_.id.toInt == selectedPlayer.clubId).map(_.name).getOrElse(""),
                    rating = selectedPlayer.meta.ttr.getOrElse(0),
                    birthYear = selectedPlayer.birthYear.map(_.toString).getOrElse(""),
                    active = true,
                    status = PantStatus.PLAY
                  )
                  comp.pants1Stage += newPant
              }
              tourney.updateCompetition(comp) match {
                case Right(updatedComp) =>
                  Global.currentSelection = Global.currentSelection.copy(competition = Some(updatedComp))
                case _ =>
              }
              render()
            }
          case _ =>
            debug("DlgAddPlayer: Add player cancelled or error.")
        }

      case id if id.id.startsWith(ChkBoxPlayer.id) =>
        val sno = SNO.fromString(elem.id.substring(ChkBoxPlayer.id.length + 1))
        val checked = elem.asInstanceOf[dom.html.Input].checked
        Global.currentSelection.competition.foreach { comp =>
          comp.pants1Stage.find(_.id == sno).foreach { p =>
            p.status = if (checked) PantStatus.PLAY else PantStatus.REGI
          }
          services.TourneyDB.tourney.updateCompetition(comp) match {
            case Right(updatedComp) =>
              Global.currentSelection = Global.currentSelection.copy(competition = Some(updatedComp))
            case _ =>
          }
        }
        render()

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
    
    val modes = DrawRules.getAvailableModes(count, base.Global.lang)
    val select = gE(SelectDrawMode).asInstanceOf[dom.html.Select]
    
    if (select != null) {
      if (resetSelection) select.value = "UNKN"
      
      // Update options (skip the first "Please select" option at index 0)
      modes.zipWithIndex.foreach { case (m, i) =>
        val option = select.options.item(i + 1).asInstanceOf[dom.html.Option]
        if (option != null) {
          option.disabled = !m.isEnabled
          val suffix = if (!m.isEnabled) s" (${gM("+not_possible")})" else ""
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
    val stageOpt = Global.currentSelection.stage
    if (select != null && btn != null && stageOpt.isDefined) {
      val stage = stageOpt.get
      val isDefault = select.value == "UNKN"
      val isAllowed = stage.status == StageStatus.CFG && base.Global.hasTourneyAccess(services.TourneyDB.tourney)
      
      btn.disabled = isDefault || !isAllowed
      if (isDefault || !isAllowed) {
        btn.classList.add("opacity-50")
      } else {
        btn.classList.remove("opacity-50")
      }
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
        val allPants = comp.pants1Stage.toSeq
        val selectedPants = allPants.filter(p => selectedSnos.contains(p.id.toString)).sortBy(-_.rating)

        if (selectedPants.length < 3) {
          dom.window.alert(gM("+err_min_players"))
        } else {
          // Generation Logic
          r.stageConfig = cfg
          r.noPlayers = selectedPants.length
          
          cfg.format match
            case StageFormat.RR => 
              r.data = RoundRobin.draw(r, comp.typ, cfg, selectedPants, DrawOption.Unknown)
     
            case StageFormat.KO =>
              val prevStageOpt = r.prefId.flatMap { pId =>
                services.TourneyDB.tourney.stages.find(s => s != null && s.id == pId)
              }
              val hasPrevGrStage = prevStageOpt.exists(_.stageConfig.format == StageFormat.GR)
              val drawOption = if (hasPrevGrStage) DrawOption.KoAfterGrp else DrawOption.KoStart

              val preparedPants = if (hasPrevGrStage) {
                prevStageOpt.map(_.data) match {
                  case Some(StageData.GroupsStage(groups)) =>
                    selectedPants.map { p =>
                      val grpOpt = groups.find(_.pants.exists(gp => gp != null && gp.id == p.id))
                      grpOpt match {
                        case Some(g) =>
                          val pos = g.pants.find(gp => gp != null && gp.id == p.id).map(_.place._1).getOrElse(1)
                          val pCopy = p.copy()
                          pCopy.qInfo = s"${g.name};${g.grId};${pos};0"
                          pCopy.place = (pos, 0)
                          pCopy
                        case None => p
                      }
                    }
                  case _ => selectedPants
                }
              } else {
                selectedPants
              }

              r.data = SingleElimination.draw(r, comp.typ, cfg, preparedPants, drawOption)
              
            case StageFormat.SW =>
              r.data = SwissSystem.draw(selectedPants, r.noWinSets, DrawOption.Unknown)
              
            case StageFormat.GR =>
              // select draw option based on whether there is a previous group stage
              val hasPrevGrStage = r.prefId.flatMap { pId =>
                services.TourneyDB.tourney.stages.find(s => s != null && s.id == pId)
              }.exists(_.stageConfig.format == StageFormat.GR)
              
              val drawOption = if (hasPrevGrStage) DrawOption.GrpAfterGrp else DrawOption.GrpStart
              r.data = Groups.draw(r, comp.typ, cfg, selectedPants, drawOption)
        
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
            val updatedCompOpt = Global.currentSelection.competition.flatMap { comp =>
              services.TourneyDB.tourney.competitions.find(c => c != null && c.id == comp.id)
            }
            Global.currentSelection = Global.currentSelection.copy(stage = None, competition = updatedCompOpt)
            comps.ContextHeader.render()
            loadPage(CompetitionInfo.name, "")
          case Left(err) => 
            error(s"Failed to delete stage: ${err.msgCode}")
        }
      case _ => debug("Delete cancelled")
    }

  def getPredecessorResultDesc(stage: Stage, sno: SNO): String =
    stage.prefId.flatMap { pId =>
      services.TourneyDB.tourney.stages.find(s => s != null && s.id == pId)
    }.map { prevStage =>
      prevStage.data match {
        case StageData.GroupsStage(groups) =>
          groups.find(_.pants.exists(gp => gp != null && gp.id == sno)).map { g =>
            val place = g.pants.find(gp => gp != null && gp.id == sno).map(_.place._1).getOrElse(0)
            if (place > 0) s"${g.name}, PLATZ $place"
            else g.name
          }.getOrElse("-")
        case _ => "-"
      }
    }.getOrElse("-")
