package pages

import org.scalajs.dom
import base.*
import shared.model.*

/**
 * Page displaying tournament results.
 * Displays a collapsible Card for ALL competitions in both HOME-Mode and VIEW-Mode,
 * showing the results of the stage where certificate == true (or finished stage/SwissSystem).
 */
object ResultList extends BasePage with JsWrapper:
  def name = PageNameTyp("ResultList")
  
  case class CompResultCard(
    comp: Competition,
    certStage: Option[Stage],
    pants: Seq[Pant]
  )

  /**
   * Computes ranked participants for a stage based on its format (Swiss, Groups, KO, RoundRobin).
   *
   * @param comp The competition object.
   * @param certStageOpt Optional stage configured for certificates or active.
   * @return Sequence of participants with updated placement tuples.
   */
  def getRankedPantsForStage(comp: Competition, certStageOpt: Option[Stage]): Seq[Pant] =
    certStageOpt match {
      case Some(stage) =>
        stage.data match {
          case StageData.SwissStage(sw) =>
            // Dynamic standings calculation for SwissSystem
            val playersData = sw.swPants.map { sp =>
              val sno = sp.sno
              var wins = 0
              var losses = 0
              var setsWon = 0
              var setsLost = 0
              val opponents = scala.collection.mutable.ListBuffer[SNO]()
              
              stage.matches.foreach { m =>
                if (m.finished) {
                  if (m.stNoA == sno) {
                    setsWon += m.sets._1
                    setsLost += m.sets._2
                    if (m.sets._1 > m.sets._2) wins += 1 else losses += 1
                    opponents += m.stNoB
                  } else if (m.stNoB == sno) {
                    setsWon += m.sets._2
                    setsLost += m.sets._1
                    if (m.sets._2 > m.sets._1) wins += 1 else losses += 1
                    opponents += m.stNoA
                  }
                }
              }
              (sp, wins, losses, setsWon, setsLost, opponents.toList)
            }

            val winsMap = playersData.map(d => (d._1.sno, d._2)).toMap

            val finalData = playersData.map { case (sp, w, l, sw, sl, opps) =>
              val bh = opps.filter(o => !o.isBye && !o.isNN).map(o => winsMap.getOrElse(o, 0)).sum
              (sp, w, l, sw, sl, bh)
            }

            // Sort by wins desc, Buchholz desc, sets diff desc, rating desc
            val sortedSwiss = finalData.sortBy { case (sp, w, l, sw, sl, bh) =>
              (-w, -bh, -(sw - sl), -sp.rating)
            }

            // Convert sorted Swiss players to Pants with assigned place
            sortedSwiss.zipWithIndex.flatMap { case ((sp, _, _, _, _, _), idx) =>
              val rank = idx + 1
              comp.pants1Stage.find(_.id == sp.sno).map { p =>
                p.copy(place = (rank, 0))
              }.orElse {
                if (!sp.sno.isBye && !sp.sno.isNN) {
                  val pName = services.TourneyDB.tourney.players.find(_.id.value == sp.id).map(pl => s"${pl.lastName}, ${pl.firstName}").getOrElse(s"Spieler ${sp.id}")
                  val clubName = services.TourneyDB.tourney.clubs.find(_.id.toInt == sp.club).map(_.name).getOrElse("")
                  Some(Pant(sp.sno, name = pName, club = clubName, place = (rank, 0)))
                } else None
              }
            }.toSeq

          case StageData.GroupsStage(groups) =>
            val groupPants = groups.flatMap(_.pants).filter(_ != null).filter(_.place._1 > 0)
            if (groupPants.nonEmpty) groupPants.sortBy(_.place._1).toSeq
            else comp.pants1Stage.filter(_.place._1 > 0).sortBy(_.place._1).toSeq

          case StageData.RoundRobinStage(rr) =>
            val rrPants = rr.pants.filter(_ != null).filter(_.place._1 > 0)
            if (rrPants.nonEmpty) rrPants.sortBy(_.place._1).toSeq
            else comp.pants1Stage.filter(_.place._1 > 0).sortBy(_.place._1).toSeq

          case StageData.KnockoutStage(_) =>
            val rankedFromComp = comp.pants1Stage.filter(_.place._1 > 0).sortBy(_.place._1).toSeq
            if (rankedFromComp.nonEmpty) rankedFromComp
            else {
              val finishedMatches = stage.matches.filter(_.finished)
              if (finishedMatches.nonEmpty) {
                val lastRound = finishedMatches.map(_.round).maxOption.getOrElse(1)
                val finalMatch = finishedMatches.find(_.round == lastRound)
                val pList = scala.collection.mutable.ListBuffer[Pant]()

                finalMatch.foreach { m =>
                  val winnerSno = if (m.sets._1 > m.sets._2) m.stNoA else m.stNoB
                  val loserSno  = if (m.sets._1 > m.sets._2) m.stNoB else m.stNoA

                  comp.pants1Stage.find(_.id == winnerSno).foreach(p => pList += p.copy(place = (1, 0)))
                  comp.pants1Stage.find(_.id == loserSno).foreach(p => pList += p.copy(place = (2, 0)))
                }
                pList.toSeq
              } else Seq.empty
            }
        }

      case None =>
        comp.pants1Stage.filter(_.place._1 > 0).sortBy(_.place._1).toSeq
    }

  def render(param: String = ""): Boolean = 
    val allComps = services.CompetitionDB.competitions.toSeq.filter(c => c != null && !c.deleted)
    
    val selectedCompId = try { if (param.nonEmpty) Some(param.toInt) else None } catch { case _: Exception => None }
    
    selectedCompId match {
      case Some(id) =>
        allComps.find(_.id.value == id).foreach { c =>
          Global.currentSelection = Global.currentSelection.copy(competition = Some(c))
        }
      case None =>
        Global.currentSelection = Global.currentSelection.copy(competition = None, stage = None)
    }
    
    comps.ContextHeader.render()

    val targetComps = selectedCompId match {
      case Some(id) => allComps.filter(_.id.value == id)
      case None     => allComps
    }
    
    val allStages = services.TourneyDB.tourney.stages.toSeq.filter(s => s != null && !s.deleted)

    // Render Cards for ALL target competitions
    val cards = targetComps.map { comp =>
      val compStages = allStages.filter(_.coId == comp.id)
      val certStageOpt = compStages.find(_.certificate).orElse(compStages.lastOption)
      val rankedPants = getRankedPantsForStage(comp, certStageOpt)
      
      CompResultCard(comp, certStageOpt, rankedPants)
    }

    setMain(cviews.pages.html.ResultList(cards))
    true
