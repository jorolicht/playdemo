package shared.model

import scala.collection.mutable.{ ArrayBuffer, ListBuffer, HashMap, HashSet, Map }
import upickle.default.{ReadWriter, macroRW}

/** CompRound describes a round of a competition like first round
 *  intermediate round or final round. Every competition has at least
 *  a final round. Currently only two typs are supported ("group" or "ko") 
 *  number of players always less or equal size
 * 
 *  baseIdvv - Round is based on previous Round (eg. preliminary round)
 *  quali    - take as pre selection either winner-1, looser-2 or all-0 from previous round
 * 
 */

sealed abstract class CompRound(val name: String, val id: Int, val coId: Long, var coPhCfg: CompRoundCfg, 
                     var status: CompRoundStatus, var demo: Boolean, var size: Int, var noPlayers: Int, 
                     var noWinSets: Int=0, var based: Option[Int]=None, var quali: QualifyTyp = QualifyTyp.ALL)  {
  var candidates   = ArrayBuffer[(Participant, Boolean)]() 
  var candInfo     = ""
  var matches      = ArrayBuffer[Match]()
  var mFinished    = 0
  var mFix         = 0
  var mTotal       = 0
}


enum QualifyTyp(val id: Int, val label: String):
  case ALL extends QualifyTyp(0, "All")   // pre selection all
  case WIN extends QualifyTyp(1, "WIN")   // winner of previous round
  case LOO extends QualifyTyp(2, "LOO")   // loser of previous round

  def msgCode: String = s"QualifyTyp.${this.toString}"


enum CompRoundCfg(val id: Int, val label: String):
  case UNKN   extends CompRoundCfg(-1,  "UNKN")       // UNBEKANNT
  case CFG    extends CompRoundCfg(0,   "CFG")        // Auswahl des Spielsystems
  case VRGR   extends CompRoundCfg(1,   "VRGR")       // Gruppen Vorrunde
  case ZRGR   extends CompRoundCfg(2,   "ZRGR")       // Gruppen Zwischenrunde
  case ERGR   extends CompRoundCfg(3,   "ERGR")       // Gruppen Endrunde
  case TRGR   extends CompRoundCfg(4,   "TRGR")       // Trost-Gruppen Runde
  case VRKO   extends CompRoundCfg(6,   "VRKO")       // KO Vorrunde
  case ERKO   extends CompRoundCfg(8,   "ERKO")       // KO Endrunde
  case TRKO   extends CompRoundCfg(9,   "TRKO")       // KO Trostrunde
  case GR3to9 extends CompRoundCfg(100, "GR3to9")     // Spielsystem mit 3er-9er Gruppen
  case GRPS3  extends CompRoundCfg(101, "GRPS3")      // Spielsystem mit 3er-Gruppen
  case GRPS34 extends CompRoundCfg(102, "GRPS34")     // Spielsystem mit 3er- und 4er-Gruppen
  case GRPS4  extends CompRoundCfg(103, "GRPS4")      // Spielsystem mit 4er-Gruppen
  case GRPS45 extends CompRoundCfg(104, "GRPS45")     // Spielsystem mit 4er- und 5er-Gruppen
  case GRPS5  extends CompRoundCfg(105, "GRPS5")      // Spielsystem mit 5er-Gruppen
  case GRPS56 extends CompRoundCfg(106, "GRPS56")     // Spielsystem mit 5er- und 6er-Gruppen
  case GRPS6  extends CompRoundCfg(107, "GRPS6")      // Spielsystem mit 6er-Gruppen
  case RR     extends CompRoundCfg(108, "RR")         // Spielsystem jeder-gegen-jeden
  case KO     extends CompRoundCfg(109, "KO")         // KO-System
  case SW     extends CompRoundCfg(110, "SW")         // Schweizer System

  def msgCode: String = s"CompRoundCfg.${this.toString}"
  def infoCode: String = s"CompRoundCfgInfo.${this.toString}"
  def equalsTo(compareWith: CompRoundCfg*): Boolean = compareWith.contains(this)


enum CompRoundStatus(val id: Int, val label: String):
  case CFG  extends CompRoundStatus(0,  "CFG")   // Configuration Status
  case AUS  extends CompRoundStatus(1,  "AUS")   // Auslosung der Vorrunde, Zwischenrunde, Endrunde, Trostrunde 
  case EIN  extends CompRoundStatus(2,  "EIN")   // Auslosung erfolgt, Eingabe der Ergebnisse
  case FIN  extends CompRoundStatus(3,  "FIN")   // Runde/Phase beendet, Auslosung ZR oder ER kann erfolgen 
  case UNKN extends CompRoundStatus(-1, "UNKN")

  def msgCode: String = s"CompRoundStatus.${this.toString}"


enum CompRoundTyp(val id: Int, val label: String):
  case UNKN extends CompRoundTyp(-1, "UNKN")  // Unbekannt
  case GR   extends CompRoundTyp(1,  "GR")    // Gruppen-System
  case KO   extends CompRoundTyp(2,  "KO")    // KO-System
  case SW   extends CompRoundTyp(3,  "SW")    // Swiss-System
  case RR   extends CompRoundTyp(4,  "RR")    // Round Robin

  def msgCode: String = s"CompRoundTyp.${this.toString}"
