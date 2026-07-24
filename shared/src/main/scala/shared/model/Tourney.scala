package shared.model

import shared.basic.Pickle.*
import shared.basic.*
import scala.util.control.NonFatal
import scala.collection.mutable.ArrayBuffer
import shared.format.*

/**
 * Represents a tournament with all its related data.
 */
case class Tourney(
  var wpId:         Int,             // Wordpress Page Id, 0 for new entries
  var name:         String,          // Tournament name
  var organizer:    String,          // Organizer name (club or registered program user)
  var startDate:    Int,             // Format: yyyymmdd
  var endDate:      Int,
  var ident:        String,          // clickTT id
  var category:     CompCategory,
  var contact:      Option[Contact] = None,
  var address:      Option[Address] = None,
  var version:      Int = 0,
  var slug:         String = "",
  val clubs:        ArrayBuffer[Club] = ArrayBuffer(),
  val players:      ArrayBuffer[Player] = ArrayBuffer(),
  val competitions: ArrayBuffer[Competition] = ArrayBuffer.fill(64)(null),
  val stages:       ArrayBuffer[Stage] = ArrayBuffer.fill(128)(null),
  var certTemplate: String = "",
  var clicktt:      String = ""
):

  // --- Buffers for tracking changes ---
  val dirtyClubs: ArrayBuffer[Club] = ArrayBuffer()
  val dirtyPlayer: ArrayBuffer[Player] = ArrayBuffer()
  val dirtyCompetition: ArrayBuffer[Competition] = ArrayBuffer()
  val dirtyStage: ArrayBuffer[Stage] = ArrayBuffer()

  private def addDirtyClub(c: Club): Unit =
    val idx = dirtyClubs.indexWhere(_.id == c.id)
    if (idx == -1) dirtyClubs += c else dirtyClubs.update(idx, c)

  private def addDirtyPlayer(p: Player): Unit =
    val idx = dirtyPlayer.indexWhere(_.id == p.id)
    if (idx == -1) dirtyPlayer += p else dirtyPlayer.update(idx, p)

  private def addDirtyCompetition(c: Competition): Unit =
    val idx = dirtyCompetition.indexWhere(_.id == c.id)
    if (idx == -1) dirtyCompetition += c else dirtyCompetition.update(idx, c)

  private def addDirtyStage(s: Stage): Unit =
    val idx = dirtyStage.indexWhere(_.id == s.id)
    if (idx == -1) dirtyStage += s else dirtyStage.update(idx, s)

  // --- Callbacks for client-side synchronization ---
  private var onSyncClubs: Option[Seq[Club] => Unit] = None
  def setSyncHandler(handler: Seq[Club] => Unit): Unit = onSyncClubs = Some(handler)

  private var onSyncPlayers: Option[Seq[Player] => Unit] = None
  def setPlayerSyncHandler(handler: Seq[Player] => Unit): Unit = onSyncPlayers = Some(handler)

  private var onSyncCompetitions: Option[Seq[Competition] => Unit] = None
  def setCompSyncHandler(handler: Seq[Competition] => Unit): Unit = onSyncCompetitions = Some(handler)

  private var onSyncStages: Option[Seq[Stage] => Unit] = None
  def setStageSyncHandler(handler: Seq[Stage] => Unit): Unit = onSyncStages = Some(handler)

  // --- Helpers ---
  def nextClubId(): ClubId = ClubId(clubs.length + 1)
  def nextPlayerId(): PlayerId = PlayerId(players.length + 1)

  // ===========================================================================
  // Competition Management
  // ===========================================================================

  def addCompetition(
    name: String, 
    typ: CompTyp, 
    category: CompCategory, 
    startDate: String, 
    lowLevel: Option[Int] = None, 
    upperLevel: Option[Int] = None, 
    doSync: Boolean = true
  ): Either[AppError, Competition] =
    val firstNull = competitions.indexOf(null)
    val index = if (firstNull != -1) firstNull else competitions.indexWhere(c => c != null && c.deleted)
    
    if (index != -1) {
      val c = Competition(
        id = CompId(index + 1), 
        name = name, 
        typ = typ, 
        category = category,
        startDate = startDate, 
        status = CompStatus.CFG,
        startStage = None,
        activ = true,
        webRegister = false,
        lowLevel = lowLevel,
        upperLevel = upperLevel,
        cttInfo = None,
        pants1Stage = ArrayBuffer(),
        deleted = false,
        version = 1
      )
      competitions(index) = c
      addDirtyCompetition(c)
      if (doSync) triggerCompSync()
      Right(c)
    } else {
      Left(AppError("max.competitions.reached"))
    }

  /**
   * Checks if player registration modifications are locked for the given competition.
   * Registration is locked if the competition status is FIN or its start stage is no longer in CFG status.
   *
   * @param comp The competition to check.
   * @return True if registration changes should be disabled.
   */
  def isRegLocked(comp: Competition): Boolean =
    comp.status == CompStatus.FIN || {
      comp.startStage.flatMap { sid =>
        val sIdx = sid.value - 1
        if (sIdx >= 0 && sIdx < 128 && stages(sIdx) != null) Some(stages(sIdx)) else None
      }.exists(_.status != StageStatus.CFG)
    }

  /** Updates an existing competition. */
  def updateCompetition(comp: Competition, doSync: Boolean = true): Either[AppError, Competition] =
    val i = comp.id.value - 1
    if (i < 0 || i >= 64 || competitions(i) == null) {
      Left(AppError("competition.notFound"))
    } else {
      val originalComp = competitions(i)
      if (originalComp.status == CompStatus.FIN && comp.status == CompStatus.FIN) {
        Left(AppError("competition.finalized"))
      } else {
        val activeStages = stages.filter(s => s != null && s.coId == comp.id && !s.deleted)
        val finalComp = if (activeStages.isEmpty) {
          comp.copy(status = CompStatus.CFG, startStage = None)
        } else {
          comp
        }
        val updatedComp = finalComp.copy(version = finalComp.version + 1)
        competitions(i) = updatedComp
        addDirtyCompetition(updatedComp)
        if (doSync) triggerCompSync()
        Right(updatedComp)
      }
    }

  /** Performs a soft delete on a competition. */
  def deleteCompetition(id: CompId, doSync: Boolean = true): Either[AppError, Unit] =
    val i = id.value - 1
    if (i < 0 || i >= 64 || competitions(i) == null) {
      Left(AppError("competition.notFound"))
    } else {
      val oldComp = competitions(i)
      if (oldComp.status == CompStatus.FIN) {
        Left(AppError("competition.finalized"))
      } else {
        val c = oldComp.copy(deleted = true, version = oldComp.version + 1)
        competitions(i) = c
        addDirtyCompetition(c)
        if (doSync) triggerCompSync()
        Right(())
      }
    }

  /** Bulk synchronizes competitions from external data (e.g., ClickTT). */
  def syncCompetitions(newComps: Seq[Competition] = Nil): Unit = 
    if (newComps.nonEmpty) {
      newComps.foreach { c =>
        val i = c.id.value - 1
        if (i >= 0 && i < 64) {
           competitions(i) = c
           addDirtyCompetition(c)
        }
      }
    }
    triggerCompSync()

  /** Internal trigger for client-side competition synchronization. */
  private def triggerCompSync(): Unit = 
    if (dirtyCompetition.nonEmpty) {
      val dirty = dirtyCompetition.toSeq
      dirtyCompetition.clear()
      onSyncCompetitions.foreach(_(dirty))
    }

  // ===========================================================================
  // Stage Management
  // ===========================================================================

  /** Adds a new stage to a competition, optionally linking to a predecessor. */
  def addStage(coId: CompId, prefId: Option[StageId], name: String, stageConfig: StageConfig, size: Int, noPlayers: Int, doSync: Boolean = true): Either[AppError, Stage] =
    val cIdx = coId.value - 1
    val compIsFin = if (cIdx >= 0 && cIdx < 64 && competitions(cIdx) != null) competitions(cIdx).status == CompStatus.FIN else false
    if (compIsFin) {
      Left(AppError("competition.finalized"))
    } else if (stages.exists(s => s != null && s.coId == coId && !s.deleted && s.name.equalsIgnoreCase(name))) {
      Left(AppError("stage.duplicateName"))
    } else {
      val firstNull = stages.indexOf(null)
      val index = if (firstNull != -1) firstNull else stages.indexWhere(r => r != null && r.deleted)

      if (index != -1) {
        val id = StageId(index + 1)
        val r = Stage(
          id = id, 
          coId = coId, 
          name = name, 
          stageConfig = stageConfig, 
          status = StageStatus.CFG, 
          demo = false, 
          size = size, 
          noPlayers = noPlayers, 
          data = stageConfig.format match {
            case StageFormat.GR => StageData.GroupsStage(ArrayBuffer.empty)
            case StageFormat.KO => StageData.KnockoutStage(KoStage(id.value, name, coId.value.toLong, 0, 0))
            case StageFormat.SW => StageData.SwissStage(SwissSys(id.value.toLong, name, coId.value, 0))
            case StageFormat.RR => StageData.RoundRobinStage(Group(1, 0, 1, name, 0))
            case _              => StageData.GroupsStage(ArrayBuffer.empty)
          },
          noWinSets = 0,
          prefId = prefId, 
          nextIds = List(), 
          quali = QualifyTyp.ALL,
          deleted = false,
          version = 1
        )
        stages(index) = r
        addDirtyStage(r)

        // Update predecessor
        prefId.foreach { pid =>
          val pIdx = pid.value - 1
          if (pIdx >= 0 && pIdx < 128 && stages(pIdx) != null) {
            val pref = stages(pIdx)
            val updatedPref = pref.copy(nextIds = pref.nextIds :+ id, version = pref.version + 1)
            stages(pIdx) = updatedPref
            addDirtyStage(updatedPref)
          }
        }

        // Update Competition if no predecessor (sets as startStage)
        if (prefId.isEmpty) {
          val cIdx = coId.value - 1
          if (cIdx >= 0 && cIdx < 64 && competitions(cIdx) != null) {
            val comp = competitions(cIdx)
            if (comp.startStage.isEmpty) {
              val updatedComp = comp.copy(startStage = Some(id), status = CompStatus.RUN, version = comp.version + 1)
              competitions(cIdx) = updatedComp
              addDirtyCompetition(updatedComp)
              if (doSync) triggerCompSync()
            }
          }
        }

        if (doSync) triggerStageSync()
        Right(r)
      } else {
        Left(AppError("max.stages.reached"))
      }
    }

  /** Deletes a stage and its successors recursively (soft delete). */
  def deleteStage(id: StageId, doSync: Boolean = true): Either[AppError, Unit] =
    val i = id.value - 1
    if (i < 0 || i >= 128 || stages(i) == null) {
      Left(AppError("stage.notFound"))
    } else {
      val r = stages(i)
      val cIdx = r.coId.value - 1
      val compIsFin = if (cIdx >= 0 && cIdx < 64 && competitions(cIdx) != null) competitions(cIdx).status == CompStatus.FIN else false
      if (compIsFin) {
        Left(AppError("competition.finalized"))
      } else {
        // Delete successors recursively
        r.nextIds.foreach(nid => deleteStage(nid, doSync = false))

        // Soft delete current stage
        val updatedR = r.copy(deleted = true, version = r.version + 1)
        stages(i) = updatedR
        addDirtyStage(updatedR)

        // Remove from predecessor's nextIds
        r.prefId.foreach { pid =>
          val pIdx = pid.value - 1
          if (pIdx >= 0 && pIdx < 128 && stages(pIdx) != null) {
            val pref = stages(pIdx)
            val updatedPref = pref.copy(nextIds = pref.nextIds.filterNot(_ == id), version = pref.version + 1)
            stages(pIdx) = updatedPref
            addDirtyStage(updatedPref)
          }
        }

        // Update Competition if it was startStage or if no active stages remain
        val compCIdx = r.coId.value - 1
        if (compCIdx >= 0 && compCIdx < 64 && competitions(compCIdx) != null) {
          val comp = competitions(compCIdx)
          val activeStages = stages.filter(s => s != null && s.coId == r.coId && !s.deleted)
          if (activeStages.isEmpty || comp.startStage.contains(id)) {
            val updatedComp = comp.copy(startStage = None, status = CompStatus.CFG, version = comp.version + 1)
            competitions(compCIdx) = updatedComp
            addDirtyCompetition(updatedComp)
            if (doSync) triggerCompSync()
          }
        }

        if (doSync) triggerStageSync()
        Right(())
      }
    }

  /** Updates an existing stage. */
  def updateStage(stage: Stage, doSync: Boolean = true): Either[AppError, Stage] =
    val i = stage.id.value - 1
    if (i < 0 || i >= 128 || stages(i) == null) {
      Left(AppError("stage.notFound"))
    } else {
      val cIdx = stage.coId.value - 1
      val compIsFin = if (cIdx >= 0 && cIdx < 64 && competitions(cIdx) != null) competitions(cIdx).status == CompStatus.FIN else false
      if (compIsFin) {
        Left(AppError("competition.finalized"))
      } else {
        val updatedR = stage.copy(version = stage.version + 1)
        stages(i) = updatedR
        addDirtyStage(updatedR)
        if (doSync) triggerStageSync()
        Right(updatedR)
      }
    }

  /** Bulk synchronizes stages from external data. */
  def syncStages(newStages: Seq[Stage] = Nil): Unit = 
    if (newStages.nonEmpty) {
      newStages.foreach { r =>
        val i = r.id.value - 1
        if (i >= 0 && i < 128) {
           stages(i) = r
           addDirtyStage(r)
        }
      }
    }
    triggerStageSync()

  /** Internal trigger for client-side stage synchronization. */
  private def triggerStageSync(): Unit = 
    if (dirtyStage.nonEmpty) {
      val dirty = dirtyStage.toSeq
      dirtyStage.clear()
      onSyncStages.foreach(_(dirty))
    }

  // ===========================================================================
  // Player Management
  // ===========================================================================

  /** Internal helper to add or update a Player object in the buffer. */
  private def addPlayerObj(p: Player, doSync: Boolean = true): Unit = 
    players.find(_.id == p.id) match
      case Some(existing) => 
        val idx = players.indexOf(existing)
        players.update(idx, p)
        if (!dirtyPlayer.exists(_.id == p.id)) dirtyPlayer += p
        if (doSync) triggerPlayerSync()
      case None =>
        players += p
        if (!dirtyPlayer.exists(_.id == p.id)) dirtyPlayer += p
        if (doSync) triggerPlayerSync()

  /** Adds a new player with duplicate check. */
  def addPlayer(
    firstName: String, 
    lastName: String, 
    clubId: Int, 
    birthYear: Option[Int] = None,
    email: Option[String] = None,
    whatsApp: Option[String] = None,
    doSync: Boolean = true
  ): Either[AppError, Player] =
    val exists = players.exists(p =>
      p.firstName == firstName &&
      p.lastName == lastName &&
      p.clubId == clubId &&
      p.birthYear == birthYear
    )
    if (exists) {
      Left(AppError("player.already.exists", s"$firstName $lastName already exists"))
    } else {
      val player = Player(
        id = nextPlayerId(),
        firstName = firstName,
        lastName = lastName,
        clubId = clubId,
        birthYear = birthYear,
        email = email,
        whatsApp = whatsApp,
        active = true
      )
      addPlayerObj(player, doSync)
      Right(player)
    }

  /** Updates an existing player. */
  def updatePlayer(p: Player, doSync: Boolean = true): Either[AppError, Player] =
    players.find(_.id == p.id) match
      case Some(_) => 
        addPlayerObj(p, doSync)

        // Propagate changes to all competitions and stages
        val clubName = clubs.find(_.id.toInt == p.clubId).map(_.name).getOrElse("")

        // 1. Update competition participants (pants1Stage)
        competitions.filter(_ != null).foreach { comp =>
          var compChanged = false
          for (j <- 0 until comp.pants1Stage.length) {
            val pant = comp.pants1Stage(j)
            if (pant.id.isSingle && pant.id.singleId == p.id) {
              val updatedPant = pant.copy(
                name = p.displayName,
                club = clubName,
                rating = p.meta.ttr.getOrElse(0),
                birthYear = p.birthYear.map(_.toString).getOrElse("")
              )
              comp.pants1Stage.update(j, updatedPant)
              compChanged = true
            } else if (pant.id.isDouble) {
              val (p1, p2) = pant.id.doubleId
              if (p1 == p.id || p2 == p.id) {
                val p1Opt = players.find(_.id == p1)
                val p2Opt = players.find(_.id == p2)
                for {
                  pl1 <- p1Opt
                  pl2 <- p2Opt
                } {
                  val c1 = clubs.find(_.id.toInt == pl1.clubId).map(_.name).getOrElse("")
                  val c2 = clubs.find(_.id.toInt == pl2.clubId).map(_.name).getOrElse("")
                  val dClub = if (pl1.clubId == pl2.clubId) c1 else s"$c1, $c2"
                  val dRating = (pl1.meta.ttr.getOrElse(0) + pl2.meta.ttr.getOrElse(0)) / 2
                  val dName = s"${pl1.lastName} / ${pl2.lastName}"
                  
                  val updatedPant = pant.copy(
                    name = dName,
                    club = dClub,
                    rating = dRating
                  )
                  comp.pants1Stage.update(j, updatedPant)
                  compChanged = true
                }
              }
            }
          }
          if (compChanged) {
            updateCompetition(comp, doSync)
          }
        }

        // 2. Update stages candidates and stage format data
        stages.filter(_ != null).foreach { stage =>
          var stageChanged = false

          // 2a. Update candidates
          for (idx <- 0 until stage.candidates.length) {
            val (pant, active) = stage.candidates(idx)
            if (pant.id.isSingle && pant.id.singleId == p.id) {
              val updatedPant = pant.copy(
                name = p.displayName,
                club = clubName,
                rating = p.meta.ttr.getOrElse(0),
                birthYear = p.birthYear.map(_.toString).getOrElse("")
              )
              stage.candidates.update(idx, (updatedPant, active))
              stageChanged = true
            } else if (pant.id.isDouble) {
              val (p1, p2) = pant.id.doubleId
              if (p1 == p.id || p2 == p.id) {
                val p1Opt = players.find(_.id == p1)
                val p2Opt = players.find(_.id == p2)
                for {
                  pl1 <- p1Opt
                  pl2 <- p2Opt
                } {
                  val c1 = clubs.find(_.id.toInt == pl1.clubId).map(_.name).getOrElse("")
                  val c2 = clubs.find(_.id.toInt == pl2.clubId).map(_.name).getOrElse("")
                  val dClub = if (pl1.clubId == pl2.clubId) c1 else s"$c1, $c2"
                  val dRating = (pl1.meta.ttr.getOrElse(0) + pl2.meta.ttr.getOrElse(0)) / 2
                  val dName = s"${pl1.lastName} / ${pl2.lastName}"
                  
                  val updatedPant = pant.copy(
                    name = dName,
                    club = dClub,
                    rating = dRating
                  )
                  stage.candidates.update(idx, (updatedPant, active))
                  stageChanged = true
                }
              }
            }
          }

          // 2b. Update stage format data
          stage.data match {
            case StageData.GroupsStage(groups) =>
              groups.foreach { group =>
                for (idx <- 0 until group.pants.length) {
                  val pant = group.pants(idx)
                  if (pant != null) {
                    if (pant.id.isSingle && pant.id.singleId == p.id) {
                      val updatedPant = pant.copy(
                        name = p.displayName,
                        club = clubName,
                        rating = p.meta.ttr.getOrElse(0),
                        birthYear = p.birthYear.map(_.toString).getOrElse("")
                      )
                      group.pants(idx) = updatedPant
                      stageChanged = true
                    } else if (pant.id.isDouble) {
                      val (p1, p2) = pant.id.doubleId
                      if (p1 == p.id || p2 == p.id) {
                        val p1Opt = players.find(_.id == p1)
                        val p2Opt = players.find(_.id == p2)
                        for {
                          pl1 <- p1Opt
                          pl2 <- p2Opt
                        } {
                          val c1 = clubs.find(_.id.toInt == pl1.clubId).map(_.name).getOrElse("")
                          val c2 = clubs.find(_.id.toInt == pl2.clubId).map(_.name).getOrElse("")
                          val dClub = if (pl1.clubId == pl2.clubId) c1 else s"$c1, $c2"
                          val dRating = (pl1.meta.ttr.getOrElse(0) + pl2.meta.ttr.getOrElse(0)) / 2
                          val dName = s"${pl1.lastName} / ${pl2.lastName}"
                          
                          val updatedPant = pant.copy(
                            name = dName,
                            club = dClub,
                            rating = dRating
                          )
                          group.pants(idx) = updatedPant
                          stageChanged = true
                        }
                      }
                    }
                  }
                }
              }
            case StageData.KnockoutStage(koStage) =>
              for (idx <- 0 until koStage.pants.length) {
                val pant = koStage.pants(idx)
                if (pant != null) {
                  if (pant.id.isSingle && pant.id.singleId == p.id) {
                    val updatedPant = pant.copy(
                      name = p.displayName,
                      club = clubName,
                      rating = p.meta.ttr.getOrElse(0),
                      birthYear = p.birthYear.map(_.toString).getOrElse("")
                    )
                    koStage.pants.update(idx, updatedPant)
                    stageChanged = true
                  } else if (pant.id.isDouble) {
                    val (p1, p2) = pant.id.doubleId
                    if (p1 == p.id || p2 == p.id) {
                      val p1Opt = players.find(_.id == p1)
                      val p2Opt = players.find(_.id == p2)
                      for {
                        pl1 <- p1Opt
                        pl2 <- p2Opt
                      } {
                        val c1 = clubs.find(_.id.toInt == pl1.clubId).map(_.name).getOrElse("")
                        val c2 = clubs.find(_.id.toInt == pl2.clubId).map(_.name).getOrElse("")
                        val dClub = if (pl1.clubId == pl2.clubId) c1 else s"$c1, $c2"
                        val dRating = (pl1.meta.ttr.getOrElse(0) + pl2.meta.ttr.getOrElse(0)) / 2
                        val dName = s"${pl1.lastName} / ${pl2.lastName}"
                        
                        val updatedPant = pant.copy(
                          name = dName,
                          club = dClub,
                          rating = dRating
                        )
                        koStage.pants.update(idx, updatedPant)
                        stageChanged = true
                      }
                    }
                  }
                }
              }
            case StageData.RoundRobinStage(rrGroup) =>
              for (idx <- 0 until rrGroup.pants.length) {
                val pant = rrGroup.pants(idx)
                if (pant != null) {
                  if (pant.id.isSingle && pant.id.singleId == p.id) {
                    val updatedPant = pant.copy(
                      name = p.displayName,
                      club = clubName,
                      rating = p.meta.ttr.getOrElse(0),
                      birthYear = p.birthYear.map(_.toString).getOrElse("")
                    )
                    rrGroup.pants(idx) = updatedPant
                    stageChanged = true
                  } else if (pant.id.isDouble) {
                    val (p1, p2) = pant.id.doubleId
                    if (p1 == p.id || p2 == p.id) {
                      val p1Opt = players.find(_.id == p1)
                      val p2Opt = players.find(_.id == p2)
                      for {
                        pl1 <- p1Opt
                        pl2 <- p2Opt
                      } {
                        val c1 = clubs.find(_.id.toInt == pl1.clubId).map(_.name).getOrElse("")
                        val c2 = clubs.find(_.id.toInt == pl2.clubId).map(_.name).getOrElse("")
                        val dClub = if (pl1.clubId == pl2.clubId) c1 else s"$c1, $c2"
                        val dRating = (pl1.meta.ttr.getOrElse(0) + pl2.meta.ttr.getOrElse(0)) / 2
                        val dName = s"${pl1.lastName} / ${pl2.lastName}"
                        
                        val updatedPant = pant.copy(
                          name = dName,
                          club = dClub,
                          rating = dRating
                        )
                        rrGroup.pants(idx) = updatedPant
                        stageChanged = true
                      }
                    }
                  }
                }
              }
            case _ =>
          }

          if (stageChanged) {
            updateStage(stage, doSync)
          }
        }

        Right(p)
      case None => 
        Left(AppError("player.notFound"))

  /** Deactivates a player (soft delete). */
  def deletePlayer(id: PlayerId, doSync: Boolean = true): Either[AppError, Unit] =
    players.find(_.id == id) match
      case Some(p) =>
        if (p.active) {
          val updated = p.copy(active = false)
          val idx = players.indexOf(p)
          players.update(idx, updated)
          addDirtyPlayer(updated)
          if (doSync) triggerPlayerSync()
        }
        Right(())
      case None =>
        Left(AppError("player.notFound"))

  /** Merges one player into another. The merged player is deactivated. */
  def mergePlayer(mainId: PlayerId, mergedId: PlayerId, doSync: Boolean = true): Either[AppError, Unit] =
    if (mainId == mergedId) return Left(AppError("player.merge.sameId"))
    val mainOpt = players.find(_.id == mainId)
    val mergedOpt = players.find(_.id == mergedId)
    (mainOpt, mergedOpt) match
      case (Some(_), Some(merged)) =>
        val updatedMerged = merged.copy(active = false, merge = Some(mainId))
        val idx = players.indexOf(merged)
        players.update(idx, updatedMerged)
        addDirtyPlayer(updatedMerged)
        if (doSync) triggerPlayerSync()
        Right(())
      case _ => 
        Left(AppError("player.merge.notFound"))

  /** Bulk synchronizes players from external data. */
  def syncPlayers(newPlayers: Seq[Player] = Nil): Unit = 
    if (newPlayers.nonEmpty) {
      newPlayers.foreach(addDirtyPlayer)
    }
    triggerPlayerSync()

  /** Internal trigger for client-side player synchronization. */
  private def triggerPlayerSync(): Unit = 
    if (dirtyPlayer.nonEmpty) {
      dirtyPlayer.clear()
      onSyncPlayers.foreach(_(players.toSeq))
    }

  // ===========================================================================
  // Club Management
  // ===========================================================================

  /** Internal helper to add or reactivate a Club object in the buffer. */
  private def addClubObj(club: Club, doSync: Boolean = true): Unit = 
    clubs.find(_.id == club.id) match
      case Some(existing) => 
        if (!existing.active) {
          val updated = existing.copy(active = true)
          val idx = clubs.indexOf(existing)
          clubs.update(idx, updated)
          addDirtyClub(updated)
          if (doSync) triggerSync()
        }
      case None =>
        clubs += club
        addDirtyClub(club)
        if (doSync) triggerSync()

  /** Adds a club by name with similarity and normalization checks. */
  def addClub(name: String, checkSimilarity: Boolean = true, doSync: Boolean = true): Either[AppError, Club] =
    try
      val normalized = Club.normalize(name)
      val threshold = if (checkSimilarity) 0.90 else 1.0
      Club.findSimilar(name, clubs, threshold) match
        case Some((existingId, _)) =>
          val existing = clubs.find(_.id == existingId).get
          if (!existing.active) {
            addClubObj(existing, doSync)
            Right(existing.copy(active = true))
          } else {
            Right(existing)
          }
        case None =>
          val club = Club(nextClubId(), name.trim, normalized, None, true)
          addClubObj(club, doSync)
          Right(club)
    catch
      case NonFatal(e) => Left(AppError(s"club.add.failed: ${e.getMessage}"))

  /** Deactivates a club (soft delete). */
  def deleteClub(clubId: ClubId, doSync: Boolean = true): Either[AppError, Unit] = 
    clubs.find(_.id == clubId) match
      case Some(c) =>
        if (c.active) {
          val updated = c.copy(active = false)
          val idx = clubs.indexOf(c)
          clubs.update(idx, updated)
          addDirtyClub(updated)
          if (doSync) triggerSync()
        }
        Right(())
      case None =>
        Left(AppError("club.notFound"))

  /** Merges one club into another. Affects all players associated with the source club. */
  def mergeClubs(sourceId: ClubId, targetId: ClubId, doSync: Boolean = true): Either[String, Unit] = 
    if (sourceId == targetId) return Left("Source and target are identical")
    
    val sourceOpt = clubs.find(_.id == sourceId)
    val targetOpt = clubs.find(_.id == targetId)

    (sourceOpt, targetOpt) match
      case (Some(source), Some(target)) =>
        if (!source.active) return Left(s"Source club '${source.name}' is already inactive")
        
        // 1. Re-assign players locally
        players.indices.foreach { i =>
          if (players(i).clubId == sourceId.toInt) {
            val updatedPlayer = players(i).copy(clubId = targetId.toInt)
            players.update(i, updatedPlayer)
            addDirtyPlayer(updatedPlayer)
          }
        }
        triggerPlayerSync()

        // 2. Update target with source's CTT info if target doesn't have it
        val mergedCtt = target.ctt.orElse(source.ctt)
        val updatedTarget = target.copy(ctt = mergedCtt)
        clubs.update(clubs.indexOf(target), updatedTarget)
        addDirtyClub(updatedTarget)

        // 3. Deactivate source
        val deactivatedSource = source.copy(active = false)
        clubs.update(clubs.indexOf(source), deactivatedSource)
        addDirtyClub(deactivatedSource)

        if (doSync) triggerSync()
        Right(())
      case _ => Left("Source or target club not found")

  def syncClubs(newClubs: Seq[Club] = Nil): Unit = 
    if (newClubs.nonEmpty) {
      newClubs.foreach(addDirtyClub)
    }
    triggerSync()

  /** Internal trigger for client-side club synchronization. */
  private def triggerSync(): Unit = 
    if (dirtyClubs.nonEmpty) {
      dirtyClubs.clear()
      onSyncClubs.foreach(_(clubs.toSeq))
    }

  /** Triggers all client-side entity synchronizations in batch. */
  def triggerAllSyncs(): Unit = 
    triggerSync()       // Club sync
    triggerPlayerSync()  // Player sync
    triggerCompSync()    // Competition sync
    triggerStageSync()    // Stage sync

  /**
   * Validates tournament data.
   */
  def check(): Either[AppError, Boolean] =
    val (y, _, _) = int2ymd(startDate)

    if name.length <= 3 then
      Left(AppError("TODOerr0179.Tourney.name", "", "", "Tourney.check"))
    else if startDate > endDate then
      Left(AppError("TODOerr0180.Tourney.edate"))
    else if y < 2022 then
      Left(AppError("TODOerr0181.Tourney.sdate"))
    else
      Right(true)

  /**
   * Returns formatted contact name in form:
   * "Lastname, Firstname"
   */
  def getContactName: String =
    contact match
      case Some(person) =>
        val lname = person.lastname
        val fname = person.firstname

        if lname.isBlank && fname.isBlank then
          s"$lname, $fname"
        else
          lname + fname
      case None =>
        ""

  def getStartDate(lang: String, fmt: Int = 0): String =
    int2date(startDate, lang, fmt)

  def getEndDate(lang: String, fmt: Int = 0): String =
    int2date(endDate, lang, fmt)


object Tourney:
  given rw: ReadWriter[Tourney] = macroRW

  def default: Tourney = Tourney(
    wpId = 0, 
    name = "", 
    organizer = "", 
    startDate = 0, 
    endDate = 0, 
    ident = "", 
    category = CompCategory.UNKNOWN,
    contact = None,
    address = None,
    version = 0,
    slug = "",
    clubs = ArrayBuffer(),
    players = ArrayBuffer(),
    competitions = ArrayBuffer.fill(64)(null),
    stages = ArrayBuffer.fill(128)(null),
    certTemplate = "",
    clicktt = ""
  )

  /**
   * Decodes a single Tourney from JSON.
   */
  def decode(json: String): Either[AppError, Tourney] =
    if json.isBlank then
      Left(AppError("TODOerr0062.decode.Tourney", "<empty input>", "", "Tourney.decode"))
    else
      try Right(read[Tourney](json))
      catch
        case NonFatal(_) =>
          Left(AppError("TODOerr0062.decode.Tourney", json.take(20), "", "Tourney.decode"))

  /**
   * Encodes a sequence of tournaments into JSON.
   */
  def encSeq(values: Seq[Tourney]): String =
    write(values)

  /**
   * Decodes a sequence of tournaments from JSON.
   */
  def decSeq(json: String): Either[AppError, Seq[Tourney]] =
    try Right(read[Seq[Tourney]](json))
    catch
      case NonFatal(_) =>
        Left(AppError("TODOerr0144.decode.Tourney", json.take(20), "", "Tourney.decSeq"))

case class ACI(
  homepageInfo: String = "",
  allowRegistration: Boolean = false,
  dateFormat: String = "DE"
) derives ReadWriter

case class SaveAciResponse(success: Boolean) derives ReadWriter
