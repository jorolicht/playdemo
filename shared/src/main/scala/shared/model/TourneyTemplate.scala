package shared.model

import scala.collection.mutable.ArrayBuffer

/**
 * Turniervorlage aus einem bestehenden Turnier („Turnier kopieren“).
 *
 * Übernommen werden Stammdaten (Veranstalter, Sportart, Kontakt, Ort, Urkundenvorlage) und die Wettbewerbe mit
 * Typ, Spielklasse, Wertungssystem und Anmeldeeinstellungen. Start- und Anmeldezeiten werden um den Abstand zwischen
 * altem und neuem Startdatum verschoben. Spieler, Vereine, Mannschaften, Ergebnisse und Spielphasen entfallen; die
 * Spielphasen bleiben als Phasenplan ([[StagePlan]]) im Wettbewerb erhalten und werden beim Start vorbelegt.
 */
object TourneyTemplate:

  /** Zusätze, die beim Start gekoppelter Phasen an den Namen angehängt werden (StageDraw.drr_first/de_first, de/en). */
  private val LinkSuffixes = Seq(" – Hinrunde", " – Gewinnerrunde", " – first leg", " – winners bracket")

  /**
   * Tage seit 1970-01-01 für ein Datum JJJJMMTT (proleptischer gregorianischer Kalender).
   *
   * @param ymd Datum als Zahl, z. B. 20261123
   * @return Tagesnummer
   */
  def toDays(ymd: Int): Long =
    val y0 = ymd / 10000; val m = (ymd / 100) % 100; val d = ymd % 100
    val y = if m <= 2 then y0 - 1 else y0
    val era = (if y >= 0 then y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (if m > 2 then m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    era.toLong * 146097 + doe - 719468

  /**
   * Datum JJJJMMTT zu einer Tagesnummer ([[toDays]]).
   *
   * @param days Tage seit 1970-01-01
   * @return Datum als Zahl
   */
  def fromDays(days: Long): Int =
    val z = days + 719468
    val era = (if z >= 0 then z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if mp < 10 then mp + 3 else mp - 9
    val y = yoe + era * 400 + (if m <= 2 then 1 else 0)
    (y * 10000 + m * 100 + d).toInt

  /** Gültiges Datum JJJJMMTT? */
  def validYmd(ymd: Int): Boolean = ymd > 19000101 && fromDays(toDays(ymd)) == ymd

  /**
   * Verschiebt ein Datum am Anfang einer Zeichenkette ("JJJJ-MM-TT…", z. B. "2030-04-01 09:00:00") um Tage; der Rest
   * (Uhrzeit) bleibt erhalten. Leere oder andere Werte bleiben unverändert.
   *
   * @param s    Datum mit optionaler Uhrzeit
   * @param days Verschiebung in Tagen
   * @return verschobenes Datum
   */
  def shiftIso(s: String, days: Long): String =
    if s == null || s.length < 10 || days == 0 then s
    else
      val head = s.substring(0, 10)
      head match
        case s"$y-$m-$d" if y.length == 4 && m.length == 2 && d.length == 2 && (y + m + d).forall(_.isDigit) =>
          val n = fromDays(toDays((y + m + d).toInt) + days)
          f"${n / 10000}%04d-${(n / 100) % 100}%02d-${n % 100}%02d" + s.substring(10)
        case _ => s

  /**
   * Name einer Spielphase ohne den Zusatz gekoppelter Phasen („Vorrunde – Hinrunde“ → „Vorrunde“).
   *
   * @param name Name der Spielphase
   * @return Grundname
   */
  def baseName(name: String): String =
    LinkSuffixes.find(sfx => name.endsWith(sfx)).map(sfx => name.dropRight(sfx.length)).getOrElse(name)

  /**
   * Phasenplan eines Wettbewerbs: alle nicht gelöschten Spielphasen in der Reihenfolge ihrer Anlage, ohne die
   * automatisch erzeugten Partner gekoppelter Phasen (Rückrunde, Verliererrunde).
   *
   * @param t Turnier
   * @param c Wettbewerb
   * @return Phasenplan
   */
  def plan(t: Tourney, c: Competition): List[StagePlan] =
    val all = t.stages.toSeq.filter(s => s != null && s.coId == c.id && !s.deleted).sortBy(_.id.value)
    val stages = all.filterNot(_.link.exists(!_.primary))
    // Vorgänger, der ein Partner ist, auf die zugehörige erste Phase abbilden
    def primaryOf(id: StageId): StageId =
      all.find(_.id == id).flatMap(_.link).filter(!_.primary).map(_.partner).getOrElse(id)
    stages.toList.map { s =>
      StagePlan(
        name        = baseName(s.name),
        config      = s.stageConfig.productPrefix,
        winSets     = s.noWinSets,
        encSystem   = s.encSystem,
        encVariant  = s.encVariant,
        finalReset  = s.finalReset,
        certificate = s.certificate,
        pref        = s.prefId.map(primaryOf).map(p => stages.indexWhere(_.id == p)).getOrElse(-1)
      )
    }

  /**
   * Nächster noch nicht angelegter Eintrag des Phasenplans eines Wettbewerbs.
   *
   * @param c        Wettbewerb
   * @param existing vorhandene Spielphasen des Wettbewerbs
   * @return Vorlage oder None
   */
  def nextPlan(c: Competition, existing: Seq[Stage]): Option[StagePlan] =
    c.stagePlan.find(p => !existing.exists(s => !s.deleted && baseName(s.name).equalsIgnoreCase(p.name)))

  /**
   * Überträgt Gewinnsätze, Mannschafts-Spielsystem, Rückspiel und Urkunden aus dem Plan auf eine neue Spielphase.
   *
   * @param s Spielphase (wird verändert)
   * @param p Plan-Eintrag
   */
  def applyPlan(s: Stage, p: StagePlan): Unit =
    if p.winSets > 0 then s.noWinSets = p.winSets
    s.encSystem   = p.encSystem
    s.encVariant  = p.encVariant
    s.finalReset  = p.finalReset
    s.certificate = p.certificate

  /**
   * Erstellt die Vorlage für ein neues Turnier.
   *
   * @param t         Quellturnier
   * @param name      Name des neuen Turniers
   * @param startDate Startdatum des neuen Turniers (JJJJMMTT)
   * @return neues Turnier (wpId 0, ohne Spieler, Ergebnisse und Spielphasen)
   */
  def copy(t: Tourney, name: String, startDate: Int): Tourney =
    val offset  = if validYmd(t.startDate) && validYmd(startDate) then toDays(startDate) - toDays(t.startDate) else 0L
    val endDate = if validYmd(t.endDate) && offset != 0 then fromDays(toDays(t.endDate) + offset)
                  else if validYmd(t.endDate) && t.endDate >= startDate then t.endDate else startDate
    val comps = ArrayBuffer.fill[Competition](64)(null)
    t.competitions.toSeq.filter(c => c != null && !c.deleted).foreach { c =>
      val idx = c.id.value - 1
      if idx >= 0 && idx < 64 then
        comps(idx) = c.copy(
          status       = CompStatus.CFG,
          startStage   = None,
          pants1Stage  = ArrayBuffer(),
          teams        = ArrayBuffer(),
          cttSNO2Ident = scala.collection.mutable.Map.empty,
          deleted      = false,
          version      = 0,
          startDate    = shiftIso(c.startDate, offset),
          regFrom      = shiftIso(c.regFrom, offset),
          regTo        = shiftIso(c.regTo, offset),
          regClosed    = false,
          stagePlan    = plan(t, c)
        )
    }
    Tourney(
      wpId         = 0,
      name         = name,
      organizer    = t.organizer,
      startDate    = startDate,
      endDate      = endDate,
      ident        = if t.ident == "SIMPLE" then "SIMPLE" else "",   // SIMPLE = Einzelwettbewerb, sonst ClickTT-Kennung
      category     = t.category,
      contact      = t.contact,
      address      = t.address,
      version      = 0,
      slug         = "",
      clubs        = ArrayBuffer(),
      players      = ArrayBuffer(),
      competitions = comps,
      stages       = ArrayBuffer.fill[Stage](128)(null),
      certTemplate = t.certTemplate,
      clicktt      = ""
    )
