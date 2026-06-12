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
  def getAvailableModes(count: Int): Seq[ModeOption] =
    Seq(
      ModeOption(StageConfig.RR, "Round Robin / Jeder-gegen-Jeden", count >= 3),
      ModeOption(StageConfig.SW, "Schweizer-System", count > 6 && count % 2 == 0),
      ModeOption(StageConfig.KO, "KO-System", count >= 4),
      
      // 3er Gruppen
      ModeOption(StageConfig.GRPS3, "3er Gruppen", (count / 3 >= 2) && (count % 3 == 0)),
      
      // 3er und 4er Gruppen
      ModeOption(StageConfig.GRPS34, "3er und 4er Gruppen", 
        ((count / 3 == 2) && (count % 3 == 1)) || 
        ((count / 3 > 2) && (count % 3 == 1 || count % 3 == 2))
      ),
      
      // 4er Gruppen
      ModeOption(StageConfig.GRPS4, "4er Gruppen", (count / 4 >= 2) && (count % 4 == 0)),
      
      // 4er und 5er Gruppen
      ModeOption(StageConfig.GRPS45, "4er und 5er Gruppen", 
        ((count / 4 == 2) && (count % 4 == 1)) ||
        ((count / 4 == 3) && (count % 4 == 1 || count % 4 == 2)) ||
        ((count / 4 > 3) && (count % 4 >= 1 && count % 4 <= 3))
      ),
      
      // 5er Gruppen
      ModeOption(StageConfig.GRPS5, "5er Gruppen", (count / 5 >= 2) && (count % 5 == 0)),
      
      // 5er und 6er Gruppen
      ModeOption(StageConfig.GRPS56, "5er und 6er Gruppen",
        ((count / 5 == 2) && (count % 5 == 1)) ||
        ((count / 5 == 3) && (count % 5 == 1 || count % 5 == 2)) ||
        ((count / 5 == 4) && (count % 5 >= 1 && count % 5 <= 3)) ||
        ((count / 5 > 4) && (count % 5 >= 1 && count % 5 <= 4))
      ),
      
      // Fixed size groups
      ModeOption(StageConfig.GRPS6, "6er Gruppen", (count / 6 >= 2) && (count % 6 == 0)),
      ModeOption(StageConfig.GRPS7, "7er Gruppen", (count / 7 >= 2) && (count % 7 == 0)),
      ModeOption(StageConfig.GRPS8, "8er Gruppen", (count / 8 >= 2) && (count % 8 == 0))
    )

  /**
   * Calculates the group distribution for a given mode and player count.
   * Returns a list of group sizes.
   */
  def calculateDistribution(stageConfig: StageConfig, count: Int): Seq[Int] =
    stageConfig match
      case StageConfig.RR => Seq(count)
      
      case StageConfig.GRPS3 => Seq.fill(count / 3)(3)
      
      case StageConfig.GRPS34 =>
        val mod = count % 3
        val no4er = mod
        val no3er = (count - (no4er * 4)) / 3
        Seq.fill(no4er)(4) ++ Seq.fill(no3er)(3)
        
      case StageConfig.GRPS4 => Seq.fill(count / 4)(4)
      
      case StageConfig.GRPS45 =>
        val mod = count % 4
        val no5er = mod
        val no4er = (count - (no5er * 5)) / 4
        Seq.fill(no5er)(5) ++ Seq.fill(no4er)(4)
        
      case StageConfig.GRPS5 => Seq.fill(count / 5)(5)
      
      case StageConfig.GRPS56 =>
        val mod = count % 5
        val no6er = mod
        val no5er = (count - (no6er * 6)) / 5
        Seq.fill(no6er)(6) ++ Seq.fill(no5er)(5)
        
      case StageConfig.GRPS6 => Seq.fill(count / 6)(6)
      case StageConfig.GRPS7 => Seq.fill(count / 7)(7)
      case StageConfig.GRPS8 => Seq.fill(count / 8)(8)
      
      case _ => Nil
