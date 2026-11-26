package shared.utils

/**
 * Medaillenspiegel (Vereinswertung) über alle Wettbewerbe eines Turniers.
 *
 * Platz 1 zählt als Gold, Platz 2 als Silber, Platz 3 als Bronze. Teilen sich mehrere Teilnehmer einen Platz (z. B. beide
 * Verlierer im Halbfinale ohne Spiel um Platz 3), erhält jeder die Medaille; Platz 4 zählt nie. Ein Doppel aus zwei
 * Vereinen bringt jedem der beiden Vereine eine Medaille. Teilnehmer ohne Verein werden nicht gewertet.
 *
 * Reihenfolge: Gold, dann Silber, dann Bronze (jeweils absteigend); bei Gleichstand gleicher Rang (1, 1, 3), sortiert
 * nach Vereinsname.
 */
object MedalTable:

  /**
   * Platzierung eines Teilnehmers in einem Wettbewerb.
   *
   * @param place Platz (1, 2, 3, …; 0 = noch nicht platziert)
   * @param clubs Vereine des Teilnehmers als (Vereins-ID, Name); bei Doppel bis zu zwei, ID 0 = unbekannt
   */
  case class Placing(place: Int, clubs: Seq[(Int, String)])

  /**
   * Zeile des Medaillenspiegels.
   *
   * @param rank   Rang (gleiche Medaillen = gleicher Rang)
   * @param clubId Vereins-ID (0, wenn nur der Name bekannt ist)
   * @param club   Vereinsname
   * @param gold   Anzahl Gold
   * @param silver Anzahl Silber
   * @param bronze Anzahl Bronze
   */
  case class Row(rank: Int, clubId: Int, club: String, gold: Int, silver: Int, bronze: Int):
    /** Gesamtzahl der Medaillen. */
    def total: Int = gold + silver + bronze

  /**
   * Berechnet den Medaillenspiegel.
   *
   * @param placings Platzierungen aller gewerteten Wettbewerbe
   * @return Zeilen in Rangfolge (nur Vereine mit mindestens einer Medaille)
   */
  def compute(placings: Seq[Placing]): Seq[Row] =
    val medals = for
      p              <- placings if p.place >= 1 && p.place <= 3
      (id, name)     <- p.clubs.filter(_._2.trim.nonEmpty).distinctBy(c => key(c._1, c._2))
    yield (key(id, name), id, name.trim, p.place)

    val rows = medals.groupBy(_._1).values.map { ms =>
      val (_, id, name, _) = ms.head
      Row(0, id, name, ms.count(_._4 == 1), ms.count(_._4 == 2), ms.count(_._4 == 3))
    }.toSeq.sortBy(r => (-r.gold, -r.silver, -r.bronze, r.club.toLowerCase))

    rows.zipWithIndex.map { case (r, i) =>
      val first = rows.indexWhere(o => (o.gold, o.silver, o.bronze) == (r.gold, r.silver, r.bronze))
      r.copy(rank = if first >= 0 && first < i then first + 1 else i + 1)
    }

  /** Schlüssel eines Vereins: die ID, falls bekannt, sonst der Name. */
  private def key(id: Int, name: String): String = if id > 0 then s"#$id" else name.trim.toLowerCase
