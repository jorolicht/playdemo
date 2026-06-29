package shared.format

import scala.collection.mutable.{ ArrayBuffer, HashMap, Map }
import shared.basic.Pickle.{ReadWriter => RW, macroRW, *}
import shared.model.*
import shared.basic.{AppError, Log}
import StageHelper.*

case class GroupConfig(id: Int, name: String, size: Int, quali: Int, pos: Int)

case class GroupEntry(
  var valid:    Boolean, 
  var points:   (Int,Int), 
  var sets:     (Int,Int), 
  var ballDiff: (Int,Int),
  var balls:    Array[String]
) {
  
  def toResultEntry(pos: (Int, Int), sno: (String, String)) =  ResultEntry(valid, pos, sno, sets, balls)
  def invert = new GroupEntry(valid,(points._2,points._1),(sets._2,sets._1),(ballDiff._2,ballDiff._1),balls.map(invBall(_)))
}

object GroupEntry {
  given rw: RW[GroupEntry] = macroRW

  def apply(valid: Boolean): GroupEntry =  GroupEntry(false,(0,0),(0,0),(0,0),Array[String]())

  // def fromResultEntry(re: ResultEntry, noSets: Int): Either[AppError, GroupEntry] = 
  //   try Right( if   (re.valid) GroupEntry(true, getPoints(re.sets, noSets), re.sets, getBalls(re.balls, noSets), re.balls)
  //              else            GroupEntry(false))
  //   catch { case _: Throwable => Left(AppError("GroupEntry.fromResultEntry")) }
}


/**
 * Group Definition for Table Tennis.
 * Represents a group of participants playing in a competition stage.
 *
 * @param grId the group identifier
 * @param size the size of the group
 * @param quali number of qualifying players from this group
 * @param name the name of the group
 * @param noWinSets number of winning sets required
 * @param pants participants in the group
 * @param results matching matrix results between players
 * @param points calculated group points for each player
 * @param sets calculated sets won/lost for each player
 * @param balls calculated ball points won/lost for each player
 * @param drawPos start position of the group for the draw
 * @param fillCnt count of players currently filled in the group
 * @param avgRating average rating of the group
 * @param occu helper map for tracking player associations
 */
case class Group(
  grId: Int,
  size: Int,
  quali: Int,
  name: String,
  noWinSets: Int,
  var pants: Array[Pant],
  var results: Array[Array[GroupEntry]],
  var points: Array[(Int, Int)],
  var sets: Array[(Int, Int)],
  var balls: Array[(Int, Int)],
  var drawPos: Int = 0,
  var fillCnt: Int = 0,
  var avgRating: Int = 0,
  var occu: Map[String, Int] = Map[String, Int]().withDefaultValue(0)
):
  // add participant
  def addPant(pant: Pant, avgPantRating: Int) = {
    pants(fillCnt) = pant
    fillCnt = fillCnt +  1
    if (pant.club != "") occu(pant.club) = occu(pant.club) + 1
    val (sum, pantCnt) = pants.foldLeft((0,0))((a, e) => if (e.rating == 0) (a._1 + avgPantRating, a._2+1) else (a._1 + e.rating, a._2+1) )
    avgRating = sum/pantCnt
  }

  /**
   * Berechnet die Platzierungen (Rankings) der Teilnehmer in dieser Gruppe.
   *
   * Die Platzierung wird lexikografisch anhand folgender Kriterien ermittelt:
   * 1. Punktdifferenz (höchste Priorität)
   * 2. Satzdifferenz (mittlere Priorität)
   * 3. Balldifferenz (niedrigste Priorität)
   *
   * Die errechneten Platzierungen werden direkt in das `place`-Feld jedes
   * Teilnehmers (`Pant`) eingetragen.
   *
   * @return Left(AppError) im Fehlerfall oder Right(()) bei erfolgreicher Berechnung.
   */
  def calc(): Either[AppError, Unit] =
    try
      if size > 0 then
        // Hilfsfunktion zur Summierung der Punkte eines Teilnehmers
        def sumPoints(pos: Int): (Int, Int) =
          results(pos).filter(_.valid).foldLeft((0, 0)) { case ((acc1, acc2), entry) =>
            (acc1 + entry.points._1, acc2 + entry.points._2)
          }

        // Hilfsfunktion zur Summierung der Sätze eines Teilnehmers
        def sumSets(pos: Int): (Int, Int) =
          results(pos).filter(_.valid).foldLeft((0, 0)) { case ((acc1, acc2), entry) =>
            (acc1 + entry.sets._1, acc2 + entry.sets._2)
          }

        // Hilfsfunktion zur Summierung der Balldifferenzen eines Teilnehmers
        def sumBallDiffs(pos: Int): (Int, Int) =
          results(pos).filter(_.valid).foldLeft((0, 0)) { case ((acc1, acc2), entry) =>
            (acc1 + entry.ballDiff._1, acc2 + entry.ballDiff._2)
          }

        var tmpPos = Array.ofDim[(Int, Long)](size)
        for i <- 0 until size do
          balls(i)  = sumBallDiffs(i)
          sets(i)   = sumSets(i)
          points(i) = sumPoints(i)
          
          // Gewichtungsbasierte Formel zur Sortierung nach Punkten, Sätzen und Bällen
          val score = (balls(i)._1 - balls(i)._2) + 2000L +
                      ((sets(i)._1 - sets(i)._2) + 50) * 10000L +
                      ((points(i)._1 - points(i)._2) + 50) * 10000000L
          tmpPos(i) = (i, score)

        // Absteigend nach Score sortieren
        tmpPos = tmpPos.sortBy(_._2).reverse
        
        var cnt = 1 
        pants(tmpPos(0)._1).place = (cnt, 0)
        for i <- 1 until size do 
          if tmpPos(i)._2 < tmpPos(i-1)._2 then cnt = cnt + 1
          pants(tmpPos(i)._1).place = (cnt, 0)
      
      Right(())
    catch
      case e: Throwable =>
        Log.error(s"ERROR Group.calc (grId: ${grId}): ${e.getMessage}")
        Left(AppError("Group_calc"))


  /**
   * Setzt alle Spielergebnisse in der Ergebnismatrix zurück (valid = false)
   * und berechnet die Platzierungen neu.
   *
   * @return Left(AppError) bei Berechnungsfehlern oder Right(()) bei Erfolg.
   */
  def resetResults(): Either[AppError, Unit] =
    for
      i <- 0 until size
      j <- 0 until size
      if j != i
    do
      results(i)(j).valid = false
    calc()


  /**
   * Alias für resetResults. Setzt alle Spielergebnisse in der Ergebnismatrix zurück
   * und berechnet die Platzierungen neu.
   *
   * @return Left(AppError) bei Berechnungsfehlern oder Right(()) bei Erfolg.
   */
  def resetMatches(): Either[AppError, Unit] = resetResults()


  /**
   * Trägt das Spielergebnis eines Gruppenspiels in die Ergebnismatrix ein.
   *
   * Validiert zunächst die Spieler-Indizes. Wenn das Spiel beendet ist (Status MS_FIN,
   * MS_FIX oder MS_DRAW) und die Sätze gültig sind, werden die Ergebnisse, Sätze,
   * Punkte und Balldifferenzen für das Spiel sowie der invertierte Eintrag für den
   * gegnerischen Spieler eingetragen. Falls das Ergebnis und die Sätze leer sind,
   * wird der Eintrag zurückgesetzt.
   *
   * @param m Der einzutragende Match-Eintrag (MEntryGr).
   * @return Left(AppError) im Fehlerfall oder Right(Boolean) zur Bestätigung (true bei
   *         erfolgreicher Eintragung/Rücksetzung, false bei ungültigen Eingabedaten).
   */
  def setMatch(m: MEntryGr): Either[AppError, Boolean] =
    import shared.model.MEntry.*

    try
      val balls = if m.result == "" then new Array[String](0) else m.result.split('·')

      if m.wgw._1 < 1 || m.wgw._1 > size || m.wgw._2 < 1 || m.wgw._2 > size then
        Left(AppError("err0225.systemgroup.invalid.whoagainstwho"))
      else if (m.status == MS_FIN || m.status == MS_FIX || m.status == MS_DRAW) && validSets(m.sets, noWinSets) then
        results(m.wgw._1 - 1)(m.wgw._2 - 1).valid    = true
        results(m.wgw._1 - 1)(m.wgw._2 - 1).balls    = balls
        results(m.wgw._1 - 1)(m.wgw._2 - 1).sets     = m.sets
        results(m.wgw._1 - 1)(m.wgw._2 - 1).points   = getPoints(m.sets, noWinSets)
        results(m.wgw._1 - 1)(m.wgw._2 - 1).ballDiff = getBalls(balls, noWinSets)
        results(m.wgw._2 - 1)(m.wgw._1 - 1)          = results(m.wgw._1 - 1)(m.wgw._2 - 1).invert
        Right(true)
      else if m.result == "" && m.sets == (0, 0) then
        results(m.wgw._1 - 1)(m.wgw._2 - 1).valid    = false
        results(m.wgw._1 - 1)(m.wgw._2 - 1).balls    = new Array[String](0)
        results(m.wgw._1 - 1)(m.wgw._2 - 1).sets     = (0, 0)
        results(m.wgw._1 - 1)(m.wgw._2 - 1).points   = (0, 0)
        results(m.wgw._1 - 1)(m.wgw._2 - 1).ballDiff = (0, 0)
        results(m.wgw._2 - 1)(m.wgw._1 - 1)          = results(m.wgw._1 - 1)(m.wgw._2 - 1).invert
        Right(true)
      else
        results(m.wgw._1 - 1)(m.wgw._2 - 1).valid = false
        results(m.wgw._2 - 1)(m.wgw._1 - 1).valid = false
        Right(false)
    catch
      case e: Throwable =>
        Log.error(s"ERROR Group.setMatch (grId: ${grId}): ${e.getMessage} für Match: $m")
        Left(AppError("Group_setMatch"))




object Group {
  given rw: RW[Group] = macroRW

  /**
   * Helper apply method to construct a Group with initialized default arrays.
   */
  def apply(grId: Int, size: Int, quali: Int, name: String, noWinSets: Int): Group =
    new Group(
      grId = grId,
      size = size,
      quali = quali,
      name = name,
      noWinSets = noWinSets,
      pants = Array.fill[Pant](size)(Pant(SNO.nn)),
      results = Array.fill[GroupEntry](size, size)(GroupEntry(false, (0,0), (0,0), (0,0), Array(""))),
      points = Array.fill[(Int, Int)](size)((0,0)),
      sets = Array.fill[(Int, Int)](size)((0,0)),
      balls = Array.fill[(Int, Int)](size)((0,0))
    )
}

/**
 * Companion object for Groups format providing initialization logic.
 */
object Groups:
  def draw(stage: Stage, coTyp: CompTyp, cfg: StageConfig, selectedPants: Seq[Pant], drawOption: DrawOption = DrawOption.Unknown): StageData.GroupsStage = {
    drawOption match {
      case DrawOption.GrpStart    => draw_GrpStart(cfg, selectedPants, stage.noWinSets)
      case DrawOption.GrpAfterGrp => draw_GrpAfterGrp(cfg, selectedPants, stage.noWinSets)
      case _                      => StageData.GroupsStage(ArrayBuffer.empty[Group])
    }
  }


  /**
   * Vertauscht zwei Spieler zwischen oder innerhalb von Gruppen in einer Gruppenphase.
   * Berechnet anschließend das durchschnittliche Rating sowie die Vereinsbelegung der betroffenen Gruppen neu.
   *
   * @param stage Die Wettbewerbsstufe (Stage), in der getauscht werden soll.
   * @param gId1  Die Gruppen-ID der ersten Gruppe.
   * @param sno1  Die Startnummer (SNO) des ersten Spielers.
   * @param gId2  Die Gruppen-ID der zweiten Gruppe.
   * @param sno2  Die Startnummer (SNO) des zweiten Spielers.
   */
  def swapPlayers(stage: Stage, gId1: Int, sno1: SNO, gId2: Int, sno2: SNO): Unit =
    stage.data match
      case StageData.SwissStage(sw) =>
        val idx1 = sw.swPants.indexWhere(p => p != null && p.sno == sno1)
        val idx2 = sw.swPants.indexWhere(p => p != null && p.sno == sno2)
        if idx1 != -1 && idx2 != -1 then
          val p1 = sw.swPants(idx1)
          val p2 = sw.swPants(idx2)
          sw.swPants(idx1) = p1.copy(sno = p2.sno)
          sw.swPants(idx2) = p2.copy(sno = p1.sno)
          sw.sno2pos.clear()
          for (i <- 0 until sw.size) sw.sno2pos += (sw.swPants(i).sno -> i)

      case other =>
        val groupsOpt = other match
          case StageData.GroupsStage(groups) => Some(groups)
          case StageData.RoundRobinStage(rr) => Some(ArrayBuffer(rr))
          case _ => None

        groupsOpt.foreach { groups =>
          val g1Opt = groups.find(_.grId == gId1)
          val g2Opt = groups.find(_.grId == gId2)
          
          for
            g1 <- g1Opt
            g2 <- g2Opt
            idx1 = g1.pants.indexWhere(p => p != null && p.id == sno1)
            idx2 = g2.pants.indexWhere(p => p != null && p.id == sno2)
            if idx1 != -1 && idx2 != -1
          do
            val temp = g1.pants(idx1)
            g1.pants(idx1) = g2.pants(idx2)
            g2.pants(idx2) = temp
            
            recalcGroupAvgRating(g1)
            recalcGroupAvgRating(g2)
            recalcGroupOccu(g1)
            recalcGroupOccu(g2)
        }

  /**
   * Berechnet das durchschnittliche Rating (avgRating) einer Gruppe basierend auf ihren aktiven Teilnehmern neu.
   *
   * @param g Die Gruppe, deren durchschnittliches Rating neu berechnet werden soll.
   */
  def recalcGroupAvgRating(g: Group): Unit =
    val activePants = g.pants.filter(p => p != null && p.id != SNO.nn)
    if activePants.nonEmpty then
      g.avgRating = activePants.map(_.rating).sum / activePants.length
    else
      g.avgRating = 0

  /**
   * Berechnet die Vereinsbelegung (occu) einer Gruppe basierend auf ihren aktiven Teilnehmern neu.
   *
   * @param g Die Gruppe, deren Vereinsbelegung neu berechnet werden soll.
   */
  def recalcGroupOccu(g: Group): Unit =
    val newOccu = scala.collection.mutable.Map[String, Int]().withDefaultValue(0)
    g.pants.foreach { p =>
      if p != null && p.id != SNO.nn && p.club.trim.nonEmpty then
        newOccu(p.club) = newOccu(p.club) + 1
    }
    g.occu = newOccu


  def draw_GrpAfterGrp(cfg: StageConfig, selectedPants: Seq[Pant], noWinSets: Int): StageData.GroupsStage = {
    val dist = shared.utils.DrawRules.calculateDistribution(cfg, selectedPants.length)
    var currentPants = selectedPants
    val buf = ArrayBuffer.empty[Group]
    dist.zipWithIndex.foreach { case (size, i) =>
      val groupPants = currentPants.take(size)
      currentPants = currentPants.drop(size)
      val g = Group(i + 1, size, 2, s"Gruppe ${i + 1}", noWinSets)
      groupPants.zipWithIndex.foreach { case (p, j) => g.pants(j) = p }
      buf += g
    }
    StageData.GroupsStage(buf)
  }


  def draw_GrpStart(cfg: StageConfig, selectedPants: Seq[Pant], noWinSets: Int): StageData.GroupsStage = {
    val dist = shared.utils.DrawRules.calculateDistribution(cfg, selectedPants.length)
    var currentPants = selectedPants
    val groups = ArrayBuffer.empty[Group]
    dist.zipWithIndex.foreach { case (size, i) =>
      val g = Group(i + 1, size, (size/2)+(size%2) , s"Gruppe ${convertToExcelColumn(i+1)}", noWinSets)
      groups += g
    }
    val MAX_RATING = 3000
    val noGroups = groups.size
    val noPants = currentPants.size

    // Calculate average Pant rating (skip Dummy players)
    val (sum, cnt, maxRating) = currentPants.foldLeft((0,0,0))((a, e) => if (e.rating == 0) (a._1, a._2, a._3) else (a._1 + e.rating, a._2+1, e.rating.max(a._3) ) )
    val avgPantRating = sum/cnt

    // Step 1 - position the best players, one in each group  (take given rating)
    // noGroups-Anzahl der Bestplazierten in pantsTop ganze Rest in pantsRest 
    val (pantsTop, pantsRest) = currentPants.sortBy(_.rating).reverse.splitAt(noGroups)
    // distribute best players to all groups
    for (i <- 0 until pantsTop.size) {  groups(i).addPant(pantsTop(i), avgPantRating) }

    // Step 2 - sort rest players ascending rating
    val pantsRestAsc = pantsRest.sortBy(_.rating)

    // Rate every player in a possible  group setting 
    for (i <- 0 until pantsRestAsc.size) {
      val ratings = getMinOccBestAvg(pantsRestAsc(i), groups, noPants, MAX_RATING, noGroups, avgPantRating)  
      // get index of element with highest rating
      val bestRatingPos = ratings.zipWithIndex.maxBy(_._1)._2
      groups(bestRatingPos).addPant(pantsRestAsc(i), avgPantRating)
    }
    StageData.GroupsStage(groups)
  }


  /**
   * Initializes group match entries according to the GroupPlan for each group.
   */
  def initGrMatches(
    coId: CompId,
    coTyp: CompTyp,
    stageId: StageId,
    stageFormat: StageFormat,
    noWinSets: Int,
    groups: ArrayBuffer[Group]
  ): Either[AppError, Seq[MEntry]] = {
    import shared.utils.GroupPlan
    import scala.util.control.NonFatal
    val buf = ArrayBuffer[MEntry]()
    try {
      groups.foreach { g =>
        val gPE = GroupPlan.get(g.size)
        for (rnd <- 1 to gPE.noRounds) {
          gPE.rounds(rnd - 1).foreach { wgw =>
            buf += MEntryGr.init(
              coId        = coId, 
              coTyp       = coTyp, 
              stageId     = stageId, 
              stageFormat = stageFormat, 
              gameNo      = 0, 
              stNoA       = g.pants(wgw._1 - 1).id, 
              stNoB       = g.pants(wgw._2 - 1).id, 
              round       = rnd, 
              grId        = g.grId, 
              wgw         = wgw, 
              winSets     = noWinSets
            )
          }
        }
      }
      val sorted = buf.collect { case m: MEntryGr => m }.sortBy(m => (m.round, m.grId))
      for (i <- 0 until sorted.size) { sorted(i).setGameNo(i + 1) }
      genGrMatchDependencies(groups, sorted.toSeq).map { _ =>
        for (i <- 0 until sorted.size) {
          if (sorted(i).hasDepend) {
            sorted(i).setStatus(MEntry.MS_BLOCK)
          }
        }
        sorted.toSeq
      }
    } catch {
      case NonFatal(e) =>
        Left(AppError("stage.initGrMatches.failed", e.getMessage))
    }
  }

  /**
   * Generates match dependency (depend) and trigger values for group matches.
   * Calculates which games block a player, and which next game is triggered by this match's completion.
   * Works for both SINGLE and DOUBLE competitions.
   *
   * @param groups  The list of groups in this stage.
   * @param matches The sequence of matches generated for the groups.
   * @return Either an AppError on failure, or true on successful calculation.
   */
  def genGrMatchDependencies(groups: ArrayBuffer[Group], matches: Seq[MEntryGr]): Either[AppError, Boolean] = {
    import scala.collection.mutable.ListBuffer
    import scala.collection.mutable.HashSet
    import scala.util.control.NonFatal

    try {
      val depMap = scala.collection.mutable.Map[PlayerId, ListBuffer[Int]]()
      
      // Initialize dependency map with empty ListBuffers for all valid player IDs in the groups
      groups.foreach { g =>
        g.pants.foreach { p =>
          if (p != null) {
            getPlayerIdsFromSNO(p.id).foreach { pid =>
              depMap(pid) = ListBuffer()
            }
          }
        }
      }
      
      // Setup list player -> game numbers
      for (m <- matches) {
        val playerIds = getPlayerIdsFromSNO(m.stNoA) ++ getPlayerIdsFromSNO(m.stNoB)
        playerIds.foreach { pid =>
          if (depMap.contains(pid)) {
            depMap(pid) += m.gameNo
          }
        }
      }         

      // Calculate depend and trigger values, splitting on current game number
      for (m <- matches) {
        val pidsA = getPlayerIdsFromSNO(m.stNoA)
        val pidsB = getPlayerIdsFromSNO(m.stNoB)

        val depend  = HashSet[Int]()
        val trigger = HashSet[Int]()

        pidsA.foreach { pid =>
          if (depMap.contains(pid)) {
            val (before, after) = depMap(pid).partition(_ <= m.gameNo)
            if (after.nonEmpty) trigger += after.sorted.head
            if (before.size > 1) depend += before.sorted.reverse(1)
          }
        }

        pidsB.foreach { pid =>
          if (depMap.contains(pid)) {
            val (before, after) = depMap(pid).partition(_ <= m.gameNo)
            if (after.nonEmpty) trigger += after.sorted.head
            if (before.size > 1) depend += before.sorted.reverse(1)
          }
        }

        m.depend  = depend.mkString("·")
        m.trigger = trigger.mkString("·")          
      }  
      Right(true)
    } catch {
      case NonFatal(e) =>
        Log.error(s"genGrMatchDependencies - ${e.getMessage}")
        Left(AppError("err0249.genGrMatchDependencies", e.getMessage))
    }
  }

  /**
   * Extracts valid PlayerIds from a Start Number (SNO).
   * Supports both singles and doubles, returning an empty sequence for BYEs and NNs.
   *
   * @param s The start number (SNO) to extract player IDs from.
   * @return A sequence of PlayerIds associated with the start number.
   */
  private def getPlayerIdsFromSNO(s: SNO): Seq[PlayerId] = {
    if (s.isNN || s.isBye) {
      Seq.empty
    } else if (s.isDouble) {
      val (id1, id2) = s.doubleId
      Seq(id1, id2)
    } else {
      Seq(s.singleId)
    }
  }

