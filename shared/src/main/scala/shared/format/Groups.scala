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
    val groupsStage: StageData.GroupsStage = drawOption match {
      case DrawOption.GrpStart    => draw_GrpStart(cfg, selectedPants, stage.noWinSets)
      case DrawOption.GrpAfterGrp => draw_GrpAfterGrp(cfg, selectedPants, stage.noWinSets)
      case _                      => StageData.GroupsStage(ArrayBuffer.empty[Group])
    }
    initGrMatches(stage.coId, coTyp, stage.id, cfg.format, stage.noWinSets, groupsStage.groups) match {
      case Right(grMatches) =>
        stage.matches.clear()
        stage.matches ++= grMatches
        stage.data = groupsStage
        groupsStage
      case Left(err) =>
        Log.error(s"Error initializing group matches: ${err.msg}")
        val emptyStage: StageData.GroupsStage = StageData.GroupsStage(ArrayBuffer.empty[Group])
        stage.data = emptyStage
        emptyStage
    }
  }


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


  // //*****************************************************************************
  // // Initialize Match Routines
  // //*****************************************************************************
  // // initialize Group matches
  // def initGrMatches(coTyp: CompTyp.Value): Either[Error, Boolean] = {
  //   import shared.utils.GroupPlan
  //   matches = ArrayBuffer[MEntry]()

  //   try { groups.foreach { g =>
  //     val gPE = GroupPlan.get(g.size)
  //     for (rnd <-1 to gPE.noRounds) { gPE.rounds(rnd-1).foreach { wgw =>
  //       matches += MEntryGr.init(coId, coTyp, coPhId, getTyp, 0, g.pants(wgw._1-1).sno, g.pants(wgw._2-1).sno, rnd, g.grId, wgw, noWinSets)
  //     }}  
  //   }} catch { case _: Throwable => println("ERROR: initGrMatches -> exception generating matches according to plan"); Left(Error("err0197.msg.initGrMatches.generating")) }

  //   matches = matches.sortBy(r => (r.round, r.asInstanceOf[MEntryGr].grId))
  //   for (i <- 0 until matches.size) { matches(i).setGameNo(i+1) } 
  //   genGrMatchDependencies() match {
  //     case Left(err)  => Left(err)
  //     case Right(res) => {
  //       for (i <- 0 until matches.size) { if (matches(i).asInstanceOf[MEntryGr].hasDepend) { matches(i).setStatus(MEntry.MS_BLOCK)} } 
  //       Right(res)
  //     }
  //   }
  // } 

  // // initialize matches for KO-System
  // def initKoMatches(coTyp: CompTyp.Value): Either[Error, Int] = {
  //   matches = ArrayBuffer[MEntry]()
  //   var err      = Error.dummy
  //   var gameNo   = 0
  //   var byeCount = 0

  //   for (r <- ko.rnds to 0 by -1) {
  //     for (m <- 1 to KoRound.getMatchesPerRound(r)) {
  //       gameNo = gameNo + 1
  //       if (r == ko.rnds) {
  //         // first/highest round initialize with participants
  //         val pantNo = (m-1)*2
  //         val byeStatus = (SNO.isBye(ko.pants(pantNo).sno), SNO.isBye(ko.pants(pantNo+1).sno))
  //         val mtch = byeStatus match {
  //           case (false, false) => MEntryKo.init(coId, coTyp, coPhId, getTyp, ko.pants(pantNo).sno, ko.pants(pantNo+1).sno, gameNo, r, m, "","", MEntry.MS_READY, (0,0), noWinSets)
  //           case (false, true)  => {
  //             byeCount = byeCount +1
  //             MEntryKo.init(coId, coTyp, coPhId, getTyp, ko.pants(pantNo).sno, ko.pants(pantNo+1).sno, gameNo, r, m, "","", MEntry.MS_FIX, (noWinSets, 0), noWinSets)
  //           }  
  //           case (true, false)  => {
  //             byeCount = byeCount +1
  //             MEntryKo.init(coId, coTyp, coPhId, getTyp, ko.pants(pantNo).sno, ko.pants(pantNo+1).sno, gameNo, r, m, "","", MEntry.MS_FIX, (0, noWinSets), noWinSets)
  //           }
  //           case (true, true)   => {
  //             err = Error("initKoMatches_invalid_ko_match")
  //             MEntryKo.init(coId, coTyp, coPhId, getTyp, ko.pants(pantNo).sno, ko.pants(pantNo+1).sno, gameNo, r, m, "","", MEntry.MS_UNKN, (0,0), noWinSets)
  //           }  
  //         }
  //         matches += mtch
  //       } else {
  //         matches += MEntryKo.init(coId, coTyp, coPhId, getTyp, "", "", gameNo, r, m, "","", MEntry.MS_MISS, (0,0), noWinSets)
  //       }
  //     }
  //   }

  //   // propagate bye matches
  //   for (g <- 1 to KoRound.getMatchesPerRound(ko.rnds)) { val x = propMatch(g) }
  //   if (err.isDummy) Right(byeCount) else Left(err)
  // }
