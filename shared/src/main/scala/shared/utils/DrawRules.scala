package shared.utils

import shared.model.*

/**
 * DrawRules defines the availability and configuration of game modes 
 * based on the number of active participants.
 */
object DrawRules:

  /**
   * Defines a possible game mode option for the UI.
   */
  case class ModeOption(
    stageConfig: StageConfig, 
    label: String, 
    isEnabled: Boolean,
    info: String = ""
  )

  /**
   * Returns all available mode options for a given number of players.
   */
  def getAvailableModes(count: Int, lang: String = "de"): Seq[ModeOption] = {
    val isDe = lang.toLowerCase.startsWith("de")
    Seq(
      ModeOption(StageConfig.RR, if (isDe) "Round Robin / Jeder-gegen-Jeden" else "Round Robin", count >= 3),
      ModeOption(StageConfig.SW, if (isDe) "Schweizer-System" else "Swiss System", count > 6),
      ModeOption(StageConfig.KO, if (isDe) "KO-System" else "Knockout System", count >= 4),
      
      // 3er Gruppen
      ModeOption(StageConfig.GRPS3, if (isDe) "3er Gruppen" else "Groups of 3", (count / 3 >= 2) && (count % 3 == 0)),
      
      // 3er und 4er Gruppen
      ModeOption(StageConfig.GRPS34, if (isDe) "3er und 4er Gruppen" else "Groups of 3 and 4", 
        ((count / 3 == 2) && (count % 3 == 1)) || 
        ((count / 3 > 2) && (count % 3 == 1 || count % 3 == 2))
      ),
      
      // 4er Gruppen
      ModeOption(StageConfig.GRPS4, if (isDe) "4er Gruppen" else "Groups of 4", (count / 4 >= 2) && (count % 4 == 0)),
      
      // 4er und 5er Gruppen
      ModeOption(StageConfig.GRPS45, if (isDe) "4er und 5er Gruppen" else "Groups of 4 and 5", 
        ((count / 4 == 2) && (count % 4 == 1)) ||
        ((count / 4 == 3) && (count % 4 == 1 || count % 4 == 2)) ||
        ((count / 4 > 3) && (count % 4 >= 1 && count % 4 <= 3))
      ),
      
      // 5er Gruppen
      ModeOption(StageConfig.GRPS5, if (isDe) "5er Gruppen" else "Groups of 5", (count / 5 >= 2) && (count % 5 == 0)),
      
      // 5er und 6er Gruppen
      ModeOption(StageConfig.GRPS56, if (isDe) "5er und 6er Gruppen" else "Groups of 5 and 6",
        ((count / 5 == 2) && (count % 5 == 1)) ||
        ((count / 5 == 3) && (count % 5 == 1 || count % 5 == 2)) ||
        ((count / 5 == 4) && (count % 5 >= 1 && count % 5 <= 3)) ||
        ((count / 5 > 4) && (count % 5 >= 1 && count % 5 <= 4))
      ),
      
      // Fixed size groups
      ModeOption(StageConfig.GRPS6, if (isDe) "6er Gruppen" else "Groups of 6", (count / 6 >= 2) && (count % 6 == 0)),
      ModeOption(StageConfig.GRPS7, if (isDe) "7er Gruppen" else "Groups of 7", (count / 7 >= 2) && (count % 7 == 0)),
      ModeOption(StageConfig.GRPS8, if (isDe) "8er Gruppen" else "Groups of 8", (count / 8 >= 2) && (count % 8 == 0))
    )
  }

  /**
   * Calculates the group distribution for a given mode and player count.
   * Returns a list of group sizes.
   */
  def calculateDistribution(stageConfig: StageConfig, count: Int, customGroupCount: Option[Int] = None): Seq[Int] =
    stageConfig match
      case StageConfig.RR => Seq(count)
      
      case StageConfig.GRPS3 => Seq.fill(count / 3)(3)
      
      case StageConfig.GRPS34 =>
        customGroupCount match {
          case Some(gCount) =>
            val no4er = count - (3 * gCount)
            val no3er = gCount - no4er
            if (no4er >= 0 && no3er >= 0) {
              Seq.fill(no4er)(4) ++ Seq.fill(no3er)(3)
            } else {
              val mod = count % 3
              val f4er = mod
              val f3er = (count - (f4er * 4)) / 3
              Seq.fill(f4er)(4) ++ Seq.fill(f3er)(3)
            }
          case None =>
            val mod = count % 3
            val no4er = mod
            val no3er = (count - (no4er * 4)) / 3
            Seq.fill(no4er)(4) ++ Seq.fill(no3er)(3)
        }
        
      case StageConfig.GRPS4 => Seq.fill(count / 4)(4)
      
      case StageConfig.GRPS45 =>
        customGroupCount match {
          case Some(gCount) =>
            val no5er = count - (4 * gCount)
            val no4er = gCount - no5er
            if (no5er >= 0 && no4er >= 0) {
              Seq.fill(no5er)(5) ++ Seq.fill(no4er)(4)
            } else {
              val mod = count % 4
              val f5er = mod
              val f4er = (count - (f5er * 5)) / 4
              Seq.fill(f5er)(5) ++ Seq.fill(f4er)(4)
            }
          case None =>
            val mod = count % 4
            val no5er = mod
            val no4er = (count - (no5er * 5)) / 4
            Seq.fill(no5er)(5) ++ Seq.fill(no4er)(4)
        }
        
      case StageConfig.GRPS5 => Seq.fill(count / 5)(5)
      
      case StageConfig.GRPS56 =>
        customGroupCount match {
          case Some(gCount) =>
            val no6er = count - (5 * gCount)
            val no5er = gCount - no6er
            if (no6er >= 0 && no5er >= 0) {
              Seq.fill(no6er)(6) ++ Seq.fill(no5er)(5)
            } else {
              val mod = count % 5
              val f6er = mod
              val f5er = (count - (f6er * 6)) / 5
              Seq.fill(f6er)(6) ++ Seq.fill(f5er)(5)
            }
          case None =>
            val mod = count % 5
            val no6er = mod
            val no5er = (count - (no6er * 6)) / 5
            Seq.fill(no6er)(6) ++ Seq.fill(no5er)(5)
        }
        
      case StageConfig.GRPS6 => Seq.fill(count / 6)(6)
      case StageConfig.GRPS7 => Seq.fill(count / 7)(7)
      case StageConfig.GRPS8 => Seq.fill(count / 8)(8)
      
      case _ => Nil

