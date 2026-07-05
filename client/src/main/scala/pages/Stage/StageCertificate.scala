package pages
package Stage

import org.scalajs.dom
import base.*
import shared.model.*
import shared.format.*

object StageCertificate extends BasePage with JsWrapper:
  def name = PageNameTyp("StageCertificate")

  def render(param: String = ""): Boolean = 
    Global.currentSelection.stage match
      case Some(stage) => 
        comps.ContextHeader.render()
        val comp = Global.currentSelection.competition.get
        val playersWithRank = getPlayersWithRank(stage, comp)
        setMain(cviews.comps.html.StageLayout(stage, "CERT")(cviews.pages.Stage.html.StageCertificate(stage, comp, playersWithRank)))
        true
      case _ => 
        debug("StageCertificate: No stage selected, redirecting to Competition Info")
        loadPage(CompetitionInfo.name, "")
        false

  private def getPlayersWithRank(stage: Stage, comp: Competition): Seq[(Pant, String)] =
    stage.data match
      case StageData.GroupsStage(groups) =>
        groups.toSeq.flatMap { g =>
          g.pants.filter(p => p != null && !p.id.isBye && !p.id.isNN).zipWithIndex.map { case (p, idx) =>
            val placeNum = if (p.place._1 > 0) p.place._1 else idx + 1
            (p, s"Gruppe ${g.grId}, ${placeNum}.")
          }
        }
      case StageData.RoundRobinStage(rr) =>
        rr.pants.filter(p => p != null && !p.id.isBye && !p.id.isNN).zipWithIndex.map { case (p, idx) =>
          val placeNum = if (p.place._1 > 0) p.place._1 else idx + 1
          (p, s"${placeNum}.")
        }
      case StageData.SwissStage(sw) =>
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
        val sorted = finalData.sortBy { case (sp, w, l, sw, sl, bh) =>
          (-w, -bh, -(sw - sl), -sp.rating)
        }
        sorted.zipWithIndex.map { case ((sp, _, _, _, _, _), idx) =>
          val p = comp.pants1Stage.find(_.id == sp.sno).getOrElse(Pant(sp.sno, name = sp.sno.toString))
          (p, s"${idx + 1}.")
        }.toSeq
      case StageData.KnockoutStage(ko) =>
        ko.pants.filter(p => p != null && !p.id.isBye && !p.id.isNN).zipWithIndex.map { case (p, idx) =>
          (p, s"${idx + 1}.")
        }.toSeq
      case null => Seq.empty
