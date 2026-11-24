package shared.model

import shared.basic.Pickle.*
import shared.basic.AppError
import scala.collection.mutable. { ArrayBuffer, Map, Stack }
import ujson.*



enum CompCategory:
  case UNKNOWN
  case TT  // Table Tennis

  def msgCode: String = s"CompCategory.${this.toString}"

object CompCategory:
  import shared.basic.Pickle.*

  given rw: ReadWriter[CompCategory] =
    readwriter[String].bimap[CompCategory](
      _.toString,
      s => try CompCategory.valueOf(s) catch { case _: Exception => CompCategory.UNKNOWN }
    )

enum CompTyp(val id: Int):
  case UNKN   extends CompTyp(0)
  case SINGLE extends CompTyp(1)
  case DOUBLE extends CompTyp(2)
  case MIXED  extends CompTyp(3)
  case TEAM2  extends CompTyp(4)
  case TEAM3  extends CompTyp(5)
  case TEAM4  extends CompTyp(6)
  case TEAM6  extends CompTyp(7)
  case Typ    extends CompTyp(99)

  def msgCode: String = s"CompTyp.${this.toString}"

  /** Kennung des Formats im Wertungssystem der Sportart (`formats` in `shared/sports`). */
  def formatId: String = this.toString.toLowerCase

  def equalsTo(compareWith: CompTyp*): Boolean =
    compareWith.contains(this)

  def name(
      mfun: (String, Seq[String]) => String,
      insert: String = ""
  ): String =
    mfun(msgCode, Seq(insert))

object CompTyp:
  import shared.basic.Pickle.*

  given rw: ReadWriter[CompTyp] =
    readwriter[String].bimap[CompTyp](
      _.toString,
      s => try CompTyp.valueOf(s) catch { case _: Exception => CompTyp.UNKN }
    )

  def fromString(value: String): CompTyp =
    value.toLowerCase match
      case "einzel" | "single" => CompTyp.SINGLE
      case "doppel" | "double" => CompTyp.DOUBLE
      case "mixed"             => CompTyp.MIXED
      case "team" | "team2"    => CompTyp.TEAM2
      case "team3"             => CompTyp.TEAM3
      case "team4"             => CompTyp.TEAM4
      case "team6"             => CompTyp.TEAM6
      case _                   => CompTyp.Typ

  def fromId(id: Int): CompTyp =
    values.find(_.id == id).getOrElse(CompTyp.UNKN)

  def apply(id: Int): CompTyp =
    fromId(id)


enum CompStatus(val id: Int):
  case UNKN   extends CompStatus(97)
  case Status extends CompStatus(98)
  case CFG    extends CompStatus(99)
  case RUN    extends CompStatus(100)
  case FIN    extends CompStatus(103)

  case READY  extends CompStatus(0)
  case VRAUS  extends CompStatus(1)
  case VREIN  extends CompStatus(2)
  case VRFIN  extends CompStatus(3)
  case ZRAUS  extends CompStatus(4)
  case ZREIN  extends CompStatus(5)
  case ZRFIN  extends CompStatus(6)
  case ERAUS  extends CompStatus(7)
  case EREIN  extends CompStatus(8)
  case ERFIN  extends CompStatus(9)

  def msgCode: String =
    s"CompStatus.${this.toString}"

  def equalsTo(compareWith: CompStatus*): Boolean =
    compareWith.contains(this)

object CompStatus:

  given rw: ReadWriter[CompStatus] =
    readwriter[String].bimap[CompStatus](
      _.toString,
      s => try CompStatus.valueOf(s) catch { case _: Exception => CompStatus.UNKN }
    )


case class CompCTT(
  ageGroup:             String = "",
  ratingRemark:         String = "",
  ratingLowLevel:       Int = 0,
  ratingUpperLevel:     Int = 0,
  sex:                  Int = 0,
  maxPersons:           Int = 0,
  entryFee:             String = "",
  ageFrom:              String = "",
  ageTo:                String = "",
  preliminaryRoundMode: String = "",
  finalRoundMode:       String = "",
  manualFinalRankings:  Boolean = false
)

object CompCTT:
  import shared.basic.Pickle.*
  given ReadWriter[CompCTT] = macroRW


opaque type CompId = Int

object CompId:
  def apply(value: Int): CompId = value
  def fromInt(value: Int): CompId = value
  
  extension (id: CompId)
    def value: Int = id

  given Ordering[CompId] = Ordering.Int

  given rw: ReadWriter[CompId] = 
    ReadWriter.join(IntReader, IntWriter).bimap(
      id => id, 
      value => CompId(value)
    )


/**
 * Wettbewerb eines Turniers.
 *
 * Online-Anmeldung (öffentliche Turnierseite, nur Einzel): `regFrom`/`regTo` begrenzen den Zeitraum (Datum
 * "JJJJ-MM-TT", jeweils einschließlich, leer = keine Grenze), `regClosed` beendet die Anmeldung vorzeitig.
 * Siehe [[Competition.registrationOpen]]; serverseitig `tourney_public_register_is_open` (includes/api/player.php).
 *
 * Warteliste: `maxPants` begrenzt die Teilnehmerzahl (0 = unbegrenzt). Ist der Wettbewerb voll, kommen weitere
 * Online-Anmeldungen mit Status [[PantStatus.WAIT]] auf die Warteliste (Reihenfolge = Anmeldereihenfolge) und rücken
 * mit [[Competition.promoteWaiting]] nach, sobald ein Platz frei wird.
 */
case class Competition(
  id:                   CompId,
  var name:             String,
  var typ:              CompTyp,
  var category:         CompCategory,
  var startDate:        String, // Format: yyyy-MM-dd HH:mm:ss
  var status:           CompStatus,
  var startStage:       Option[StageId] = None,
  var activ:            Boolean = true,
  var webRegister:      Boolean = false,
  var lowLevel:         Option[Int] = None,
  var upperLevel:       Option[Int] = None,
  var cttInfo:          Option[CompCTT] = None,
  val pants1Stage:      ArrayBuffer[Pant] = ArrayBuffer(),
  var deleted:          Boolean = false,
  var version:          Int = 0,
  var cttSNO2Ident:     Map[SNO, String] = Map.empty,
  val teams:            ArrayBuffer[Team] = ArrayBuffer(),
  var scoring:          String = "",
  var regFrom:          String = "",
  var regTo:            String = "",
  var regClosed:        Boolean = false,
  var stagePlan:        List[StagePlan] = List(),
  var maxPants:         Int = 0
):
  var pant2idx: Map[SNO, Int] = Map.empty         // Pant id -> index in pant array

  def hash: Int = s"$name${typ.id}$startDate${getFromTTR}${getToTTR}".hashCode
  def equal(co: Competition): Boolean = hash == co.hash
  def setTyp(value: String): Competition = copy(typ = CompTyp.fromString(value))
  def getAgeGroup: String = cttInfo.map(_.ageGroup).getOrElse("")
  def getRatingLowLevel: Int = lowLevel.getOrElse(0)
  def getRatingUpperLevel: Int = upperLevel.getOrElse(0)
  def getFromTTR: String =
    if getRatingLowLevel > 0 then f"${getRatingLowLevel}%04d"
    else "0000"

  def getToTTR: String =
    if getRatingUpperLevel > 0 then f"${getRatingUpperLevel}%04d"
    else "XXXX"

  /** Ist die Online-Anmeldung für diese Art Wettbewerb möglich? Nur Einzel (Doppel/Mannschaften brauchen Partner). */
  def registrationSupported: Boolean = typ == CompTyp.SINGLE

  /**
   * Ist die Online-Anmeldung nach den Einstellungen des Wettbewerbs an einem Tag offen?
   * (Ohne turnierweiten Schalter und Wettbewerbsbeginn, die prüft der Aufrufer.)
   *
   * @param today Datum "JJJJ-MM-TT"
   * @return true, wenn Einzel, nicht beendet, nicht abgeschlossen und `today` im Zeitraum `regFrom`–`regTo` liegt
   */
  def registrationOpen(today: String): Boolean =
    registrationSupported && !regClosed && status != CompStatus.FIN &&
      (regFrom.isEmpty || today >= regFrom) && (regTo.isEmpty || today <= regTo)

  /** Belegt die Meldung einen Platz? Alle außer Warteliste und abgelehnt. */
  def occupiesPlace(p: Pant): Boolean = p.status != PantStatus.WAIT && p.status != PantStatus.RJEC

  /** Anzahl belegter Plätze. */
  def placesTaken: Int = pants1Stage.count(occupiesPlace)

  /** Ist die Höchstzahl erreicht? (Ohne Höchstzahl nie.) */
  def isFull: Boolean = maxPants > 0 && placesTaken >= maxPants

  /** Meldungen auf der Warteliste in Nachrückreihenfolge. */
  def waitingList: Seq[Pant] = pants1Stage.filter(_.status == PantStatus.WAIT).toSeq

  /**
   * Position einer Meldung auf der Warteliste.
   *
   * @param sno Startnummer
   * @return 1, 2, … oder 0, wenn die Meldung nicht auf der Warteliste steht
   */
  def waitPosition(sno: SNO): Int = waitingList.indexWhere(_.id == sno) + 1

  /**
   * Lässt Meldungen der Warteliste nachrücken, solange Plätze frei sind (Status REGI, aktiv – wie eine
   * Online-Anmeldung). Ohne Höchstzahl rücken alle nach.
   *
   * @return die nachgerückten Meldungen
   */
  def promoteWaiting(): List[Pant] =
    val free = if maxPants > 0 then math.max(0, maxPants - placesTaken) else Int.MaxValue
    val moved = waitingList.take(free).toList
    moved.foreach { p => p.status = PantStatus.REGI; p.active = true }
    moved


/**
 * Vorlage einer Spielphase aus einem kopierten Turnier ([[TourneyTemplate]]). Beim Start einer Spielphase werden Name,
 * Spielsystem und Gewinnsätze daraus vorbelegt; Spieler und Auslosung entstehen wie gewohnt neu.
 *
 * @param name        Name der Spielphase (bei gekoppelten Phasen ohne Zusatz „– Hinrunde“ bzw. „– Gewinnerrunde“)
 * @param config      Spielsystem ([[StageConfig]] als Name, z. B. "KO", "GRPS34", "DRR")
 * @param winSets     Gewinnsätze (0 = Standard)
 * @param encSystem   Spielsystem der Begegnungen bei Mannschaften
 * @param encVariant  Spielweise der Begegnungen bei Mannschaften
 * @param finalReset  Doppeltes K.-o.: Finale mit Rückspiel
 * @param certificate Spielphase mit Urkunden
 * @param pref        Index der Vorgänger-Phase in der Liste, -1 = erste Phase
 */
case class StagePlan(
  name:        String,
  config:      String,
  winSets:     Int = 0,
  encSystem:   String = "",
  encVariant:  String = "",
  finalReset:  Boolean = false,
  certificate: Boolean = false,
  pref:        Int = -1
)

object StagePlan:
  given rw: ReadWriter[StagePlan] = macroRW

object Competition:
  given rw: ReadWriter[Competition] =
    ReadWriter.join(
      macroRW[Competition].map { comp =>
        if (comp.cttSNO2Ident == null) {
          comp.cttSNO2Ident = Map.empty
        }
        // Wertungssystem (Schlüssel, z. B. "tt_001"); leer = vor Einführung der Versionen angelegt (Sports.LegacyKey)
        if (comp.scoring == null) comp.scoring = ""
        if (comp.regFrom == null) comp.regFrom = ""
        if (comp.regTo == null) comp.regTo = ""
        if (comp.stagePlan == null) comp.stagePlan = List()
        if (comp.teams == null) comp.copy(teams = ArrayBuffer()) else comp
      },
      macroRW[Competition]
    )

  def dummy: Competition = Competition(CompId(0), "", CompTyp.UNKN, CompCategory.UNKNOWN, "", CompStatus.UNKN)


object CompDB:
  val MaxStage = 32
  val comps: Array[Competition] = Array.fill(MaxStage)(null)
  var compIdx: Map[CompId, Int] = Map.empty

  private val free = Stack.from(0 until MaxStage)
