package shared.model

import shared.basic.Pickle.*
import shared.basic.*
import scala.util.control.NonFatal
import scala.collection.mutable.ArrayBuffer

/**
 * Represents a tournament with all its related data.
 */
case class Tourney(
  var id:           Int,             // Wordpress Page Id, 0 for new entries
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
  val rounds:       ArrayBuffer[Round] = ArrayBuffer.fill(128)(null)
):

  // --- Buffers for tracking changes ---
  val dirtyClubs: ArrayBuffer[Club] = ArrayBuffer()
  val dirtyPlayer: ArrayBuffer[Player] = ArrayBuffer()
  val dirtyCompetition: ArrayBuffer[Competition] = ArrayBuffer()
  val dirtyRound: ArrayBuffer[Round] = ArrayBuffer()

  // --- Callbacks for client-side synchronization ---
  private var onSyncClubs: Option[Seq[Club] => Unit] = None
  def setSyncHandler(handler: Seq[Club] => Unit): Unit = onSyncClubs = Some(handler)

  private var onSyncPlayers: Option[Seq[Player] => Unit] = None
  def setPlayerSyncHandler(handler: Seq[Player] => Unit): Unit = onSyncPlayers = Some(handler)

  private var onSyncCompetitions: Option[Seq[Competition] => Unit] = None
  def setCompSyncHandler(handler: Seq[Competition] => Unit): Unit = onSyncCompetitions = Some(handler)

  private var onSyncRounds: Option[Seq[Round] => Unit] = None
  def setRoundSyncHandler(handler: Seq[Round] => Unit): Unit = onSyncRounds = Some(handler)

  // --- Helpers ---
  def nextClubId(): ClubId = ClubId(clubs.length + 1)
  def nextPlayerId(): PlayerId = PlayerId(players.length + 1)

  // ===========================================================================
  // Competition Management
  // ===========================================================================

  /** Adds a new competition to the tournament. */
  def addCompetition(name: String, typ: CompTyp, category: CompCategory, startDate: String, doSync: Boolean = true): Either[AppError, Competition] =
    val firstNull = competitions.indexOf(null)
    val index = if (firstNull != -1) firstNull else competitions.indexWhere(c => c != null && c.deleted)
    
    if (index != -1) {
      val c = Competition(
        id = CompId(index + 1), 
        name = name, 
        typ = typ, 
        category = category,
        startDate = startDate, 
        status = CompStatus.READY,
        startRound = None,
        activ = true,
        webRegister = false,
        lowLevel = None,
        upperLevel = None,
        cttInfo = None,
        pants = ArrayBuffer(),
        deleted = false,
        version = 1
      )
      competitions(index) = c
      if (!dirtyCompetition.exists(_.id == c.id)) dirtyCompetition += c
      if (doSync) triggerCompSync()
      Right(c)
    } else {
      Left(AppError("max.competitions.reached"))
    }

  /** Updates an existing competition. */
  def updateCompetition(comp: Competition, doSync: Boolean = true): Either[AppError, Competition] =
    val i = comp.id.value - 1
    if (i < 0 || i >= 64 || competitions(i) == null) {
      Left(AppError("competition.notFound"))
    } else {
      val updatedComp = comp.copy(version = comp.version + 1)
      competitions(i) = updatedComp
      if (!dirtyCompetition.exists(_.id == updatedComp.id)) dirtyCompetition += updatedComp
      if (doSync) triggerCompSync()
      Right(updatedComp)
    }

  /** Performs a soft delete on a competition. */
  def deleteCompetition(id: CompId, doSync: Boolean = true): Either[AppError, Unit] =
    val i = id.value - 1
    if (i < 0 || i >= 64 || competitions(i) == null) {
      Left(AppError("competition.notFound"))
    } else {
      val oldComp = competitions(i)
      val c = oldComp.copy(deleted = true, version = oldComp.version + 1)
      competitions(i) = c
      if (!dirtyCompetition.exists(_.id == c.id)) dirtyCompetition += c
      if (doSync) triggerCompSync()
      Right(())
    }

  /** Bulk synchronizes competitions from external data (e.g., ClickTT). */
  def syncCompetitions(newComps: Seq[Competition] = Nil): Unit = 
    if (newComps.nonEmpty) {
      newComps.foreach { c =>
        val i = c.id.value - 1
        if (i >= 0 && i < 64) {
           competitions(i) = c
           if (!dirtyCompetition.exists(_.id == c.id)) dirtyCompetition += c
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
  // Round Management
  // ===========================================================================

  /** Adds a new round to a competition, optionally linking to a predecessor. */
  def addRound(coId: CompId, prefId: Option[RoundId], name: String, rndCfg: RoundCfg, size: Int, noPlayers: Int, doSync: Boolean = true): Either[AppError, Round] =
    val firstNull = rounds.indexOf(null)
    val index = if (firstNull != -1) firstNull else rounds.indexWhere(r => r != null && r.deleted)

    if (index != -1) {
      val id = RoundId(index + 1)
      val r = Round(
        id = id, 
        coId = coId, 
        name = name, 
        rndCfg = rndCfg, 
        status = RoundStatus.CFG, 
        demo = false, 
        size = size, 
        noPlayers = noPlayers, 
        noWinSets = 0,
        prefId = prefId, 
        nextIds = List(), 
        quali = QualifyTyp.ALL,
        deleted = false,
        version = 1
      )
      rounds(index) = r
      if (!dirtyRound.exists(_.id == r.id)) dirtyRound += r

      // Update predecessor
      prefId.foreach { pid =>
        val pIdx = pid.value - 1
        if (pIdx >= 0 && pIdx < 128 && rounds(pIdx) != null) {
          val pref = rounds(pIdx)
          val updatedPref = pref.copy(nextIds = pref.nextIds :+ id, version = pref.version + 1)
          rounds(pIdx) = updatedPref
          if (!dirtyRound.exists(_.id == updatedPref.id)) dirtyRound += updatedPref
        }
      }

      // Update Competition if no predecessor (sets as startRound)
      if (prefId.isEmpty) {
        val cIdx = coId.value - 1
        if (cIdx >= 0 && cIdx < 64 && competitions(cIdx) != null) {
          val comp = competitions(cIdx)
          if (comp.startRound.isEmpty) {
            val updatedComp = comp.copy(startRound = Some(id), version = comp.version + 1)
            competitions(cIdx) = updatedComp
            if (!dirtyCompetition.exists(_.id == updatedComp.id)) dirtyCompetition += updatedComp
            if (doSync) triggerCompSync()
          }
        }
      }

      if (doSync) triggerRoundSync()
      Right(r)
    } else {
      Left(AppError("max.rounds.reached"))
    }

  /** Deletes a round and its successors recursively (soft delete). */
  def deleteRound(id: RoundId, doSync: Boolean = true): Either[AppError, Unit] =
    val i = id.value - 1
    if (i < 0 || i >= 128 || rounds(i) == null) {
      Left(AppError("round.notFound"))
    } else {
      val r = rounds(i)
      // Delete successors recursively
      r.nextIds.foreach(nid => deleteRound(nid, doSync = false))

      // Soft delete current round
      val updatedR = r.copy(deleted = true, version = r.version + 1)
      rounds(i) = updatedR
      if (!dirtyRound.exists(_.id == updatedR.id)) dirtyRound += updatedR

      // Remove from predecessor's nextIds
      r.prefId.foreach { pid =>
        val pIdx = pid.value - 1
        if (pIdx >= 0 && pIdx < 128 && rounds(pIdx) != null) {
          val pref = rounds(pIdx)
          val updatedPref = pref.copy(nextIds = pref.nextIds.filterNot(_ == id), version = pref.version + 1)
          rounds(pIdx) = updatedPref
          if (!dirtyRound.exists(_.id == updatedPref.id)) dirtyRound += updatedPref
        }
      }

      // Update Competition if it was startRound
      val cIdx = r.coId.value - 1
      if (cIdx >= 0 && cIdx < 64 && competitions(cIdx) != null) {
        val comp = competitions(cIdx)
        if (comp.startRound.contains(id)) {
          val updatedComp = comp.copy(startRound = None, version = comp.version + 1)
          competitions(cIdx) = updatedComp
          if (!dirtyCompetition.exists(_.id == updatedComp.id)) dirtyCompetition += updatedComp
          if (doSync) triggerCompSync()
        }
      }

      if (doSync) triggerRoundSync()
      Right(())
    }

  /** Updates an existing round. */
  def updateRound(round: Round, doSync: Boolean = true): Either[AppError, Round] =
    val i = round.id.value - 1
    if (i < 0 || i >= 128 || rounds(i) == null) {
      Left(AppError("round.notFound"))
    } else {
      val updatedR = round.copy(version = round.version + 1)
      rounds(i) = updatedR
      if (!dirtyRound.exists(_.id == updatedR.id)) dirtyRound += updatedR
      if (doSync) triggerRoundSync()
      Right(updatedR)
    }

  /** Bulk synchronizes rounds from external data. */
  def syncRounds(newRounds: Seq[Round] = Nil): Unit = 
    if (newRounds.nonEmpty) {
      newRounds.foreach { r =>
        val i = r.id.value - 1
        if (i >= 0 && i < 128) {
           rounds(i) = r
           if (!dirtyRound.exists(_.id == r.id)) dirtyRound += r
        }
      }
    }
    triggerRoundSync()

  /** Internal trigger for client-side round synchronization. */
  private def triggerRoundSync(): Unit = 
    if (dirtyRound.nonEmpty) {
      val dirty = dirtyRound.toSeq
      dirtyRound.clear()
      onSyncRounds.foreach(_(dirty))
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
          if (!dirtyPlayer.exists(_.id == updated.id)) dirtyPlayer += updated
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
        if (!dirtyPlayer.exists(_.id == updatedMerged.id)) dirtyPlayer += updatedMerged
        if (doSync) triggerPlayerSync()
        Right(())
      case _ => 
        Left(AppError("player.merge.notFound"))

  /** Bulk synchronizes players from external data. */
  def syncPlayers(newPlayers: Seq[Player] = Nil): Unit = 
    if (newPlayers.nonEmpty) {
      newPlayers.foreach(p => if (!dirtyPlayer.exists(_.id == p.id)) dirtyPlayer += p)
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
          if (!dirtyClubs.exists(_.id == updated.id)) dirtyClubs += updated
          if (doSync) triggerSync()
        }
      case None =>
        clubs += club
        if (!dirtyClubs.exists(_.id == club.id)) dirtyClubs += club
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
          if (!dirtyClubs.exists(_.id == updated.id)) dirtyClubs += updated
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
            if (!dirtyPlayer.exists(_.id == updatedPlayer.id)) dirtyPlayer += updatedPlayer
          }
        }
        triggerPlayerSync()

        // 2. Update target with source's CTT info if target doesn't have it
        val mergedCtt = target.ctt.orElse(source.ctt)
        val updatedTarget = target.copy(ctt = mergedCtt)
        clubs.update(clubs.indexOf(target), updatedTarget)
        if (!dirtyClubs.exists(_.id == updatedTarget.id)) dirtyClubs += updatedTarget

        // 3. Deactivate source
        val deactivatedSource = source.copy(active = false)
        clubs.update(clubs.indexOf(source), deactivatedSource)
        if (!dirtyClubs.exists(_.id == deactivatedSource.id)) dirtyClubs += deactivatedSource

        if (doSync) triggerSync()
        Right(())
      case _ => Left("Source or target club not found")

  def syncClubs(newClubs: Seq[Club] = Nil): Unit = 
    if (newClubs.nonEmpty) {
      newClubs.foreach(c => if (!dirtyClubs.exists(_.id == c.id)) dirtyClubs += c)
    }
    triggerSync()

  /** Internal trigger for client-side club synchronization. */
  private def triggerSync(): Unit = 
    if (dirtyClubs.nonEmpty) {
      dirtyClubs.clear()
      onSyncClubs.foreach(_(clubs.toSeq))
    }

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
    id = 0, 
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
    rounds = ArrayBuffer.fill(128)(null)
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
