package shared.model

import java.time.LocalDate

import upickle.default._
import upickle.default.{ReadWriter as RW, macroRW}

/**
 * Repräsentiert die Basis-Daten eines Turniers.
 *
 * @param name Der Name des Turniers.
 * @param organizer Der Name des Organisators (z.B. ein Verein oder eine Person).
 * @param orgDir Ein vereinheitlichter Name des Organisators, der als Verzeichnisname verwendet wird.
 * @param startDate Das Startdatum des Turniers.
 * @param endDate Das Enddatum des Turniers.
 * @param ident Eine eindeutige Identifikation des Turniers, z.B. eine ID von click-TT.
 * @param typ Die Art des Turniers, z.B. Tischtennis.
 * @param privat ein Flag, das anzeigt, ob das Turnier privat ist. Private Turniere sind nur für registrierte Benutzer sichtbar.
 * @param contact Die Kontaktinformationen für das Turnier.
 * @param address Die Adresse, an der das Turnier stattfindet.
 * @param id Eine eindeutige, automatisch inkrementierte ID für das Turnier.
 */
case class TournBase(
  val name:      String, 
  var organizer: String,             
  val orgDir:    String,             
  val startDate: LocalDate, 
  var endDate:   LocalDate, 
  var ident:     String,               
  val typ:       TourneyTyp,         
  var privat:    Boolean,            
  var contact:   Contact,              
  var address:   Address,            
  val id:        Long = 0L           
)

object TournBase:
  given RW[LocalDate] = readwriter[String].bimap[LocalDate](
    _.toString,
    LocalDate.parse(_)
  )
  given rw: RW[TournBase] = macroRW

/**
 * Eine Aufzählung (Enum) für die verschiedenen Arten von Turnieren.
 *
 * @param id Die numerische ID des Typs.
 * @param label Eine textuelle Beschreibung des Typs.
 */
enum TourneyTyp(val id: Int, val label: String):
  /** Unbekannter Turniertyp. */
  case UNKN    extends TourneyTyp(0,  "UNKN")
  /** Tischtennis-Turnier. */
  case TT      extends TourneyTyp(1,  "TABLETENNIS")

object TourneyTyp:
  given RW[TourneyTyp] = readwriter[Int].bimap[TourneyTyp](
    _.id,
    id => TourneyTyp.fromInt(id)
  )

  def fromInt(id: Int): TourneyTyp = id match
    case 1 => TT
    case _ => UNKN




