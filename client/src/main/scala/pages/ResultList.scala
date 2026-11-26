package pages

import org.scalajs.dom
import base.*
import base.Messages.gM
import shared.model.*
import scala.scalajs.js

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
   * Medaillenspiegel für die Anzeige.
   *
   * @param rows    Zeilen in Rangfolge
   * @param counted Anzahl gewerteter (abgeschlossener) Wettbewerbe
   * @param total   Anzahl aller Wettbewerbe
   */
  case class MedalView(rows: Seq[shared.utils.MedalTable.Row], counted: Int, total: Int)

  /**
   * Zeile der Spielerstatistik für die Anzeige.
   *
   * @param name    Spielername
   * @param club    Verein
   * @param row     berechnete Werte
   * @param longest längstes Spiel als Text (Gegner, Ergebnis, Bälle, Wettbewerb)
   */
  case class StatLine(name: String, club: String, row: shared.utils.PlayerStats.Row, longest: String):
    /** TTR-Veränderung mit Vorzeichen bzw. „–“ ohne Wert. */
    def ttrText: String = row.ttrDelta.map(d => if d > 0 then s"+$d" else d.toString).getOrElse("–")
    /** Farbe der TTR-Veränderung. */
    def ttrClass: String = row.ttrDelta match
      case Some(d) if d > 0 => "text-success"
      case Some(d) if d < 0 => "text-danger"
      case _                => "text-muted"

  /**
   * Spielerstatistik für die Anzeige.
   *
   * @param lines   Zeilen (nach Siegen, dann Spielen)
   * @param longest längstes Spiel des Turniers als Text
   */
  case class StatsView(lines: Seq[StatLine], longest: Option[String])

  /**
   * Toggles collapse state for all competition cards on the page.
   *
   * @param show If true, expands all cards; if false, collapses all cards.
   */
  def toggleAllCards(show: Boolean): Unit =
    try {
      val elements = dom.document.querySelectorAll(".comp-card-collapse")
      val bootstrap = js.Dynamic.global.bootstrap

      for (i <- 0 until elements.length) {
        val el = elements.item(i).asInstanceOf[dom.html.Div]
        val icon = dom.document.getElementById(s"icon-${el.id}")
        if (icon != null) {
          if (show) {
            icon.classList.remove("bi-chevron-down")
            icon.classList.add("bi-chevron-up")
          } else {
            icon.classList.remove("bi-chevron-up")
            icon.classList.add("bi-chevron-down")
          }
        }
        if (!js.isUndefined(bootstrap) && bootstrap != null && !js.isUndefined(bootstrap.Collapse)) {
          val instance = bootstrap.Collapse.getOrCreateInstance(el, js.Dynamic.literal("toggle" -> false))
          if (show) instance.show() else instance.hide()
        } else {
          if (show) el.classList.add("show") else el.classList.remove("show")
        }
      }
    } catch {
      case e: Exception => error(s"toggleAllCards error: ${e.getMessage}")
    }

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

          case StageData.KnockoutStage(_) if stage.stageConfig == StageConfig.DEL =>
            // Verliererrunde eines Doppelten K.-o.: Platzierung aus Finale und Verliererrunde
            shared.format.DoubleElimination.placements(stage).flatMap { case (sno, place) =>
              comp.pants1Stage.find(_.id == sno).map(_.copy(place = (place, 0)))
            }

          case StageData.KnockoutStage(_) =>
            getKoRanking(comp, stage)
        }

      case None =>
        comp.pants1Stage.filter(_.place._1 > 0).sortBy(_.place._1).toSeq
    }

  /**
   * Computes the placements of a knockout stage from its finished matches.
   * Rounds count down (first round = highest number, final = 1, match for 3rd place = 0):
   * final winner 1, final loser 2, match for 3rd place 3/4 (otherwise both semi-final losers 3),
   * loser of round r >= 2 shares place 2^(r-1)+1 (3, 5, 9, ...). Participants still in the
   * draw are not ranked yet.
   *
   * @param comp The competition containing the participants.
   * @param stage The knockout stage.
   * @return Ranked participants sorted by place.
   */
  private def getKoRanking(comp: Competition, stage: Stage): Seq[Pant] =
    val finished = stage.matches.filter(_.finished)
    val thirdPlayed = finished.exists(_.round == 0)
    val places = finished.flatMap { m =>
      val (winner, loser) = if (m.sets._1 > m.sets._2) (m.stNoA, m.stNoB) else (m.stNoB, m.stNoA)
      m.round match {
        case 0                => Seq(winner -> 3, loser -> 4)
        case 1                => Seq(winner -> 1, loser -> 2)
        case 2 if thirdPlayed => Seq.empty
        case r                => Seq(loser -> ((1 << (r - 1)) + 1))
      }
    }.filter { case (sno, _) => !sno.isBye && !sno.isNN }

    places.flatMap { case (sno, place) =>
      comp.pants1Stage.find(_.id == sno).map(_.copy(place = (place, 0)))
    }.sortBy(_.place._1).toSeq

  /**
   * Vereine eines Teilnehmers für den Medaillenspiegel: Einzel und Mannschaft je ein Verein, Doppel bis zu zwei.
   *
   * @param comp Wettbewerb (für die Mannschaften)
   * @param p    Teilnehmer
   * @return Vereine als (ID, Name); ohne Zuordnung der im Teilnehmer gespeicherte Verein
   */
  private def clubsOf(comp: Competition, p: Pant): Seq[(Int, String)] =
    val t = services.TourneyDB.tourney
    def club(id: Int) = (id, t.clubs.find(c => c != null && c.id.toInt == id).map(_.name).getOrElse(""))
    def player(id: PlayerId) = t.players.find(pl => pl != null && pl.id == id)
    val fallback = Seq((p.clubId, p.club))
    if p.id.isTeam then comp.teams.find(_.id == p.id.teamId).map(tm => Seq(club(tm.clubId))).getOrElse(fallback)
    else if p.id.isDouble then
      val (a, b) = p.id.doubleId
      val cs = Seq(a, b).flatMap(player).map(pl => club(pl.clubId))
      if cs.nonEmpty then cs else fallback
    else if p.id.isSingle then player(p.id.singleId).map(pl => Seq(club(pl.clubId))).getOrElse(fallback)
    else Seq()

  /**
   * Gewertete Einzel- und Doppelspiele aller Wettbewerbe (Mannschaftswettbewerbe nicht; ohne Freilose).
   *
   * @param comps  Wettbewerbe
   * @param stages Abschnitte
   * @return Spiele; Kennung = Name des Wettbewerbs
   */
  private def statGames(comps: Seq[Competition], stages: Seq[Stage]): Seq[shared.utils.PlayerStats.Game] =
    def side(sno: SNO): Seq[PlayerId] =
      if sno.isSingle then Seq(sno.singleId) else if sno.isDouble then { val (a, b) = sno.doubleId; Seq(a, b) } else Seq()
    for
      comp <- comps if comp.typ == CompTyp.SINGLE || comp.typ == CompTyp.DOUBLE || comp.typ == CompTyp.MIXED
      st   <- stages if st.coId == comp.id
      m    <- st.matches.toSeq if m.countable && !m.stNoA.isBye && !m.stNoB.isBye
      a     = side(m.stNoA)
      b     = side(m.stNoB) if a.nonEmpty && b.nonEmpty
    yield
      val balls = try m.getBalls.toSeq.filter(x => x._1 >= 0 && x._2 >= 0) catch case _: Throwable => Seq()
      shared.utils.PlayerStats.Game(comp.name, a, b, m.sets, balls)

  /**
   * Spielerstatistik über alle Einzel- und Doppelwettbewerbe.
   *
   * @param comps  Wettbewerbe
   * @param stages Abschnitte
   * @return Anzeige mit Zeilen und längstem Spiel des Turniers
   */
  private def statsView(comps: Seq[Competition], stages: Seq[Stage]): StatsView =
    import shared.utils.PlayerStats
    val t = services.TourneyDB.tourney
    def player(id: PlayerId) = t.players.find(pl => pl != null && pl.id == id)
    def names(ids: Seq[PlayerId]) = ids.map(id => player(id).map(_.displayName).getOrElse(s"#$id")).mkString(" / ")
    def club(id: PlayerId) = player(id).flatMap(pl => t.clubs.find(c => c != null && c.id.toInt == pl.clubId)).map(_.name).getOrElse("")
    def ballText(g: PlayerStats.Game) = if g.ballCount > 0 then s", ${gM("results.statBalls", g.ballCount.toString)}" else ""
    val games = statGames(comps, stages)
    val lines = PlayerStats.compute(games, id => player(id).flatMap(_.meta.ttr)).map { r =>
      val longest = r.longest.map { g =>
        val (opp, sets) = if g.sideA.contains(r.player) then (g.sideB, g.sets) else (g.sideA, g.sets.swap)
        s"${sets._1}:${sets._2} ${gM("results.statVs")} ${names(opp)}${ballText(g)} (${g.ref})"
      }.getOrElse("")
      StatLine(player(r.player).map(_.displayName).getOrElse(s"#${r.player}"), club(r.player), r, longest)
    }
    val longest = PlayerStats.longest(games).map(g => s"${names(g.sideA)} – ${names(g.sideB)} ${g.sets._1}:${g.sets._2}${ballText(g)} (${g.ref})")
    StatsView(lines, longest)

  /**
   * Prüft, ob ein Wettbewerb im Medaillenspiegel zählt: abgeschlossen oder Urkundenabschnitt beendet.
   *
   * @param card Wettbewerb mit Urkundenabschnitt
   * @return true, wenn die Platzierungen endgültig sind
   */
  private def medalsFinal(card: CompResultCard): Boolean =
    card.comp.status == CompStatus.FIN || card.certStage.exists(_.status == StageStatus.FIN)

  // ---------------------------------------------------------------------------------------------------------------
  // Export (Excel/CSV) und Druckansicht (PDF über den Druckdialog des Browsers)
  // ---------------------------------------------------------------------------------------------------------------

  /** Zuletzt angezeigte Daten (Gesamtansicht) für den Export. */
  private var lastCards: Seq[CompResultCard] = Seq()
  private var lastMedals: Option[MedalView]  = None
  private var lastStats: Option[StatsView]   = None
  private var lastMatches: Seq[Seq[String]]  = Seq()

  /**
   * Bezeichnung der Runde eines Spiels (Gruppe und Runde, K.-o.-Runde bzw. Runde n).
   *
   * @param st Abschnitt
   * @param m  Spiel
   * @return Text für die Spalte „Runde“
   */
  private def roundText(st: Stage, m: MEntry): String = m match
    case g: MEntryGr =>
      s"${gM("results.exGroup")} ${('A' + math.max(g.grId, 1) - 1).toChar}, ${gM("StageResult.round_n", g.round.toString)}"
    case k: MEntryKo if st.stageConfig != StageConfig.DEL =>
      k.round match
        case 0 => gM("StageResult.round_3rd")
        case 1 => gM("StageResult.round_final")
        case 2 => gM("StageResult.round_semi")
        case 3 => gM("StageResult.round_quarter")
        case 4 => gM("StageResult.round_last16")
        case 5 => gM("StageResult.round_last32")
        case 6 => gM("StageResult.round_last64")
        case 7 => gM("StageResult.round_last128")
        case r => gM("StageResult.round_n", r.toString)
    case _ => gM("StageResult.round_n", m.round.toString)

  /**
   * Zeilen aller beendeten Spiele (ohne Freilose) für den Export.
   *
   * @param comps  Wettbewerbe
   * @param stages Abschnitte
   * @return Wettbewerb, Abschnitt, Runde, Teilnehmer und Verein A/B, Sätze, Bälle
   */
  private def matchRows(comps: Seq[Competition], stages: Seq[Stage]): Seq[Seq[String]] =
    for
      comp <- comps
      st   <- stages if st.coId == comp.id
      m    <- st.matches.toSeq.sortBy(_.gameNo) if m.finished && m.status != MEntry.MS_FIX
    yield
      def pant(sno: SNO) = comp.pants1Stage.find(_.id == sno).map(p => (p.name, p.club)).getOrElse((sno.toString, ""))
      val (nameA, clubA) = pant(m.stNoA)
      val (nameB, clubB) = pant(m.stNoB)
      val balls = try m.getBalls.toSeq.filter(b => b._1 >= 0 && b._2 >= 0).map(b => s"${b._1}:${b._2}").mkString(" ")
                  catch case _: Throwable => m.result
      Seq(comp.name, st.name, roundText(st, m), nameA, clubA, nameB, clubB, s"${m.sets._1}:${m.sets._2}", balls)

  /**
   * Lädt eine Auswertung als CSV-Datei für Excel herunter.
   *
   * @param kind "places", "matches", "medals" oder "stats"
   */
  def exportCsv(kind: String): Unit =
    import shared.utils.Csv
    def h(keys: String*): Seq[String] = keys.map(k => gM(k))
    val content = kind match
      case "places" =>
        Csv.document(h("results.thComp", "results.thPlace", "results.thName", "results.thClub"),
          lastCards.flatMap(c => c.pants.map(p => Seq(c.comp.name, p.place._1.toString, p.name, p.club))))
      case "matches" =>
        Csv.document(h("results.thComp", "results.exStage", "results.exRound", "results.exPantA", "results.exClubA",
                       "results.exPantB", "results.exClubB", "results.statSets", "results.exBalls"), lastMatches)
      case "medals" =>
        Csv.document(h("results.medalRank", "results.thClub", "results.medalGold", "results.medalSilver", "results.medalBronze", "results.medalTotal"),
          lastMedals.toSeq.flatMap(_.rows).map(r => Seq(r.rank.toString, r.club, r.gold.toString, r.silver.toString, r.bronze.toString, r.total.toString)))
      case _ =>
        Csv.document(h("results.thName", "results.thClub", "results.statGames", "results.exWins", "results.exLosses",
                       "results.exSetsWon", "results.exSetsLost", "results.exBallsWon", "results.exBallsLost", "results.statLongest", "results.statTtr"),
          lastStats.toSeq.flatMap(_.lines).map(l => Seq(l.name, l.club, l.row.games.toString, l.row.wins.toString, l.row.losses.toString,
            l.row.setsWon.toString, l.row.setsLost.toString, l.row.ballsWon.toString, l.row.ballsLost.toString, l.longest,
            l.row.ttrDelta.map(_.toString).getOrElse(""))))
    val base = services.TourneyDB.tourney.name.trim.replaceAll("[^A-Za-z0-9ÄÖÜäöüß]+", "-").stripPrefix("-").stripSuffix("-")
    val name = s"${if base.isEmpty then "turnier" else base}_${gM(s"results.exFile.$kind")}.csv"
    val blob = new dom.Blob(js.Array(content), dom.BlobPropertyBag("text/csv;charset=utf-8"))
    val url  = dom.URL.createObjectURL(blob)
    val a    = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
    a.href = url
    a.setAttribute("download", name)
    dom.document.body.appendChild(a)
    a.click()
    dom.document.body.removeChild(a)
    dom.window.setTimeout(() => dom.URL.revokeObjectURL(url), 1000)

  /**
   * Druckansicht: alle Reiter mit aufgeklappten Wettbewerben untereinander, ohne Navigation; über den Druckdialog
   * auch als PDF speicherbar.
   */
  def printResults(): Unit =
    val body = dom.document.body
    body.classList.add("tourney-print-results")
    dom.window.addEventListener("afterprint", (_: dom.Event) => body.classList.remove("tourney-print-results"),
                                js.Dynamic.literal(once = true).asInstanceOf[dom.EventListenerOptions])
    dom.window.print()

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

    // Register toggleAllCards on window object so onclick attributes work natively
    dom.window.asInstanceOf[scala.scalajs.js.Dynamic].resultsExport = (kind: String) => exportCsv(kind)
    dom.window.asInstanceOf[scala.scalajs.js.Dynamic].resultsPrint = () => printResults()
    dom.window.asInstanceOf[scala.scalajs.js.Dynamic].toggleAllCards = 
      (show: Boolean) => toggleAllCards(show)

    // Register event listeners to update icon-collapse-comp-? when cards expand/collapse
    dom.document.addEventListener("show.bs.collapse", (e: dom.Event) => {
      val target = e.target.asInstanceOf[dom.html.Div]
      if (target != null && target.id != null && target.id.startsWith("collapse-comp-")) {
        val icon = dom.document.getElementById(s"icon-${target.id}")
        if (icon != null) {
          icon.classList.remove("bi-chevron-down")
          icon.classList.add("bi-chevron-up")
        }
      }
    })

    dom.document.addEventListener("hide.bs.collapse", (e: dom.Event) => {
      val target = e.target.asInstanceOf[dom.html.Div]
      if (target != null && target.id != null && target.id.startsWith("collapse-comp-")) {
        val icon = dom.document.getElementById(s"icon-${target.id}")
        if (icon != null) {
          icon.classList.remove("bi-chevron-up")
          icon.classList.add("bi-chevron-down")
        }
      }
    })

    val allStages = services.TourneyDB.tourney.stages.toSeq.filter(s => s != null && !s.deleted)

    /** Wettbewerb mit Urkundenabschnitt (bzw. letztem Abschnitt) und Platzierungen. */
    def card(comp: Competition): CompResultCard =
      val compStages = allStages.filter(_.coId == comp.id)
      val certStageOpt = compStages.find(_.certificate).orElse(compStages.lastOption)
      CompResultCard(comp, certStageOpt, getRankedPantsForStage(comp, certStageOpt))

    val targetComps = selectedCompId match {
      case Some(id) => allComps.filter(_.id.value == id)
      case None     => allComps
    }
    
    // Render Cards for ALL target competitions
    val cards = targetComps.map(card)

    // Medaillenspiegel über alle Wettbewerbe (nur in der Gesamtansicht)
    val medals = if selectedCompId.isDefined then None else
      val counted = allComps.map(card).filter(medalsFinal)
      Some(MedalView(
        shared.utils.MedalTable.compute(counted.flatMap(c => c.pants.map(p => shared.utils.MedalTable.Placing(p.place._1, clubsOf(c.comp, p))))),
        counted.length, allComps.length))

    // Spielerstatistik über alle Einzel- und Doppelwettbewerbe (nur in der Gesamtansicht)
    val stats = if selectedCompId.isDefined then None else Some(statsView(allComps, allStages))

    if selectedCompId.isEmpty then
      lastCards = cards
      lastMedals = medals
      lastStats = stats
      lastMatches = matchRows(allComps, allStages)

    setMain(cviews.pages.html.ResultList(cards, medals, stats))
    true
