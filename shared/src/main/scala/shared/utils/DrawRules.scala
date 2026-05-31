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
    cfg: RoundCfg, 
    label: String, 
    isEnabled: Boolean,
    info: String = ""
  )

  /**
   * Returns all available mode options for a given number of players.
   */
  def getAvailableModes(count: Int): Seq[ModeOption] =
    Seq(
      ModeOption(RoundCfg.RR, "Round Robin / Jeder-gegen-Jeden", count >= 3),
      ModeOption(RoundCfg.SW, "Schweizer-System", count > 6 && count % 2 == 0),
      ModeOption(RoundCfg.KO, "KO-System", count >= 4),
      
      // 3er Gruppen
      ModeOption(RoundCfg.GRPS3, "3er Gruppen", (count / 3 >= 2) && (count % 3 == 0)),
      
      // 3er und 4er Gruppen
      ModeOption(RoundCfg.GRPS34, "3er und 4er Gruppen", 
        ((count / 3 == 2) && (count % 3 == 1)) || 
        ((count / 3 > 2) && (count % 3 == 1 || count % 3 == 2))
      ),
      
      // 4er Gruppen
      ModeOption(RoundCfg.GRPS4, "4er Gruppen", (count / 4 >= 2) && (count % 4 == 0)),
      
      // 4er und 5er Gruppen
      ModeOption(RoundCfg.GRPS45, "4er und 5er Gruppen", 
        ((count / 4 == 2) && (count % 4 == 1)) ||
        ((count / 4 == 3) && (count % 4 == 1 || count % 4 == 2)) ||
        ((count / 4 > 3) && (count % 4 >= 1 && count % 4 <= 3))
      ),
      
      // 5er Gruppen
      ModeOption(RoundCfg.GRPS5, "5er Gruppen", (count / 5 >= 2) && (count % 5 == 0)),
      
      // 5er und 6er Gruppen
      ModeOption(RoundCfg.GRPS56, "5er und 6er Gruppen",
        ((count / 5 == 2) && (count % 5 == 1)) ||
        ((count / 5 == 3) && (count % 5 == 1 || count % 5 == 2)) ||
        ((count / 5 == 4) && (count % 5 >= 1 && count % 5 <= 3)) ||
        ((count / 5 > 4) && (count % 5 >= 1 && count % 5 <= 4))
      ),
      
      // Fixed size groups
      ModeOption(RoundCfg.GRPS6, "6er Gruppen", (count / 6 >= 2) && (count % 6 == 0)),
      ModeOption(RoundCfg.GRPS7, "7er Gruppen", (count / 7 >= 2) && (count % 7 == 0)),
      ModeOption(RoundCfg.GRPS8, "8er Gruppen", (count / 8 >= 2) && (count % 8 == 0))
    )

  /**
   * Calculates the group distribution for a given mode and player count.
   * Returns a list of group sizes.
   */
  def calculateDistribution(cfg: RoundCfg, count: Int): Seq[Int] =
    cfg match
      case RoundCfg.RR => Seq(count)
      
      case RoundCfg.GRPS3 => Seq.fill(count / 3)(3)
      
      case RoundCfg.GRPS34 =>
        val mod = count % 3
        val no4er = mod
        val no3er = (count - (no4er * 4)) / 3
        Seq.fill(no4er)(4) ++ Seq.fill(no3er)(3)
        
      case RoundCfg.GRPS4 => Seq.fill(count / 4)(4)
      
      case RoundCfg.GRPS45 =>
        val mod = count % 4
        val no5er = mod
        val no4er = (count - (no5er * 5)) / 4
        Seq.fill(no5er)(5) ++ Seq.fill(no4er)(4)
        
      case RoundCfg.GRPS5 => Seq.fill(count / 5)(5)
      
      case RoundCfg.GRPS56 =>
        val mod = count % 5
        val no6er = mod
        val no5er = (count - (no6er * 6)) / 5
        Seq.fill(no6er)(6) ++ Seq.fill(no5er)(5)
        
      case RoundCfg.GRPS6 => Seq.fill(count / 6)(6)
      case RoundCfg.GRPS7 => Seq.fill(count / 7)(7)
      case RoundCfg.GRPS8 => Seq.fill(count / 8)(8)
      
      case _ => Nil
