package pages

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import base.*
import base.Messages.gM
import shared.model.*

/**
 * „Mein Turnier“ – persönliche Ansicht für Spieler (öffentlich, ohne Konto).
 *
 * Der Spieler wählt seinen Namen; die Auswahl wird je Turnier im Browser gemerkt (localStorage). Angezeigt werden
 * seine nächsten Spiele (mit Gegner und, wenn aufgerufen, dem Tisch), seine Ergebnisse sowie seine Wettbewerbe mit
 * Tabellenplatz bzw. Stand im K.-o.-Baum. Berücksichtigt werden Einzel und Doppel; Mannschaftswettbewerbe erscheinen
 * nur in der Liste der Wettbewerbe. Solange die Seite offen ist, werden die Daten alle 30 Sekunden neu geladen.
 */
object MyTourney extends BasePage with JsWrapper:
  def name = PageNameTyp("MyTourney")

  val SelectId:     HtmlId = genId(name)
  val BtnSelectId:  HtmlId = genId(name)
  val BtnChangeId:  HtmlId = genId(name)
  val BtnRefreshId: HtmlId = genId(name)
  val BtnBackId:    HtmlId = genId(name)

  /** Abstand des automatischen Neuladens in Millisekunden. */
  val RefreshMs = 30000

  private var timer: Option[Int] = None
  private var lastUpdate: String = ""

  /**
   * Nächstes (noch nicht beendetes) Spiel.
   *
   * @param comp     Wettbewerb
   * @param stage    Spielphase
   * @param m        Spiel
   * @param opponent Name des Gegners bzw. Hinweis, dass er noch nicht feststeht
   */
  case class NextGame(comp: Competition, stage: Stage, m: MEntry, opponent: String):
    def called: Boolean = m.status == MEntry.MS_RUN && m.playfield.trim.nonEmpty
    def ready: Boolean  = m.status == MEntry.MS_READY

  /**
   * Beendetes Spiel aus Sicht des Spielers.
   *
   * @param comp     Wettbewerb
   * @param stage    Spielphase
   * @param m        Spiel
   * @param opponent Name des Gegners
   * @param sets     Sätze bzw. Punkte aus Sicht des Spielers
   * @param won      Some(true) gewonnen, Some(false) verloren, None unentschieden
   * @param bye      Spiel gegen Freilos
   */
  case class GameResult(comp: Competition, stage: Stage, m: MEntry, opponent: String, sets: (Int, Int), won: Option[Boolean], bye: Boolean)

  /**
   * Wettbewerb des Spielers.
   *
   * @param comp    Wettbewerb
   * @param partner Doppelpartner bzw. Mannschaft
   * @param stages  Spielphasen mit dem Stand des Spielers (Tabellenplatz bzw. Stand im K.-o.-Baum)
   */
  case class CompInfo(comp: Competition, partner: Option[String], stages: Seq[(Stage, String)])

  /**
   * Inhalt der persönlichen Ansicht.
   *
   * @param player  gewählter Spieler
   * @param club    Verein des Spielers
   * @param comps   seine Wettbewerbe
   * @param next    nächste Spiele (aufgerufene zuerst)
   * @param results Ergebnisse (zuletzt gespielte zuerst)
   */
  case class View(player: Player, club: String, comps: Seq[CompInfo], next: Seq[NextGame], results: Seq[GameResult])

  // ---------------------------------------------------------------------------------------------------------------
  // Auswahl im Browser merken
  // ---------------------------------------------------------------------------------------------------------------

  private def storageKey(t: Tourney): String = s"tourney_my_player_${t.wpId}"

  /** Im Browser gemerkter Spieler dieses Turniers. */
  def storedPlayerId(t: Tourney): Option[PlayerId] =
    try Option(dom.window.localStorage.getItem(storageKey(t))).flatMap(_.toIntOption).filter(_ > 0).map(PlayerId(_))
    catch case _: Throwable => None

  /** Merkt (Some) bzw. vergisst (None) den gewählten Spieler dieses Turniers. */
  def storePlayerId(t: Tourney, id: Option[PlayerId]): Unit =
    try id match
      case Some(p) => dom.window.localStorage.setItem(storageKey(t), p.value.toString)
      case None    => dom.window.localStorage.removeItem(storageKey(t))
    catch case _: Throwable => ()

  // ---------------------------------------------------------------------------------------------------------------
  // Auswertung
  // ---------------------------------------------------------------------------------------------------------------

  private def tourney: Tourney = Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)

  private def competitions: Seq[Competition] =
    services.CompetitionDB.competitions.toSeq.filter(c => c != null && !c.deleted)

  private def stagesOf(c: Competition): Seq[Stage] =
    services.TourneyDB.tourney.stages.toSeq.filter(s => s != null && s.coId == c.id && !s.deleted)

  /** Startnummern des Spielers in einem Wettbewerb (Einzel bzw. Doppel). */
  def snosOf(pid: PlayerId, c: Competition): Seq[SNO] =
    c.pants1Stage.toSeq.filter(_.active).map(_.id).filter { s =>
      s.singleIdOpt.contains(pid) || s.doubleIdsOpt.exists((a, b) => a == pid || b == pid)
    }

  /** Mannschaft des Spielers in einem Mannschaftswettbewerb. */
  private def teamOf(pid: PlayerId, c: Competition): Option[Team] =
    if (!shared.format.TeamEncounter.isTeam(c.typ)) None else c.teams.find(_.players.contains(pid))

  /** Spieler, die an mindestens einem Wettbewerb teilnehmen, sortiert nach Name. */
  def participants: Seq[Player] =
    val comps = competitions
    tourney.players.toSeq.filter(p => p != null && p.id.value > 0)
      .filter(p => comps.exists(c => snosOf(p.id, c).nonEmpty || teamOf(p.id, c).isDefined))
      .sortBy(_.displayName.toLowerCase)

  private def clubName(clubId: Int): String =
    tourney.clubs.find(c => c != null && c.id.toInt == clubId).map(_.name).getOrElse("")

  /** Name eines Teilnehmers (Startnummer) im Wettbewerb. */
  private def pantName(c: Competition, sno: SNO): String =
    if (sno.isBye) gM("myTourney.bye")
    else if (sno.isNN) gM("myTourney.tbd")
    else c.pants1Stage.find(_.id == sno).map(_.name).filter(_.nonEmpty).getOrElse(gM("myTourney.tbd"))

  /** Bezeichnung der Runde eines Spiels (K.-o.: z. B. „Halbfinale“, Gruppen: Name der Gruppe, sonst „Runde n“). */
  def roundLabel(stage: Stage, m: MEntry): String =
    def koRound(r: Int): String =
      val key = s"StageInput.round.$r"
      val l = gM(key)
      if (l == key) gM("StageInput.round.default", r.toString) else l
    (stage.data, m) match
      case (StageData.KnockoutStage(_), _) => koRound(m.round)
      case (StageData.GroupsStage(groups), g: MEntryGr) =>
        groups.find(_.grId == g.grId).map(_.name).getOrElse(gM("StageInput.round.default", m.round.toString))
      case _ => gM("StageInput.round.default", m.round.toString)

  /** Stand des Spielers in einer Spielphase: Tabellenplatz bzw. Stand im K.-o.-Baum. */
  def standing(c: Competition, stage: Stage, sno: SNO): String =
    def groupText(g: shared.format.Group, label: Option[String]): Option[String] =
      val idx = g.pants.indexWhere(p => p != null && p.id == sno)
      if (idx < 0) None
      else
        val played = stage.matches.exists(m => m.finished && (m.stNoA == sno || m.stNoB == sno))
        val place  = g.pants(idx).place._1
        val pts    = g.points.lift(idx).map((a, b) => s"$a:$b").getOrElse("")
        val prefix = label.map(_ + ": ").getOrElse("")
        Some(if (played && place > 0) prefix + gM("myTourney.place", place.toString, pts)
             else prefix + gM("myTourney.noResults"))

    stage.data match
      case StageData.GroupsStage(groups) =>
        groups.iterator.flatMap(g => groupText(g, Some(g.name))).nextOption().getOrElse("")
      case StageData.RoundRobinStage(rr) =>
        groupText(rr, None).getOrElse("")
      case StageData.SwissStage(_) =>
        val rounds = stage.matches.count(m => m.finished && (m.stNoA == sno || m.stNoB == sno))
        if (rounds == 0) gM("myTourney.noResults")
        else ResultList.getRankedPantsForStage(c, Some(stage)).find(_.id == sno)
          .map(p => gM("myTourney.swissPlace", p.place._1.toString, rounds.toString)).getOrElse("")
      case StageData.KnockoutStage(_) =>
        val mine    = stage.matches.toSeq.filter(m => m.stNoA == sno || m.stNoB == sno)
        val pending = mine.filter(m => !m.finished)
        val lost    = mine.find(m => m.finished && m.status != MEntry.MS_DRAW && m.getWinner() != sno && !m.getWinner().isNN)
        val won     = mine.filter(m => m.finished && m.getWinner() == sno)
        val toLoserBracket = stage.link.exists(l => l.kind == LinkKind.DE && l.primary)
        if (mine.isEmpty) gM("myTourney.koNotPlaced")
        else lost match
          case Some(m) if toLoserBracket => gM("myTourney.koLoserBracket", roundLabel(stage, m))
          case Some(m) if m.round == 1   => gM("myTourney.koSecond")
          case Some(m)                   => gM("myTourney.koOut", roundLabel(stage, m))
          case None if pending.nonEmpty  => gM("myTourney.koIn", roundLabel(stage, pending.maxBy(_.round)))
          case None if won.exists(_.round == 1) => gM("myTourney.koWinner")
          case None if won.exists(_.round == 0) => gM("myTourney.koThird")
          case None                      => ""

  /** Baut die persönliche Ansicht für einen Spieler auf. */
  def buildView(player: Player): View =
    val pid   = player.id
    val comps = competitions.filter(c => snosOf(pid, c).nonEmpty || teamOf(pid, c).isDefined)
      .sortBy(c => (c.startDate, c.id.value))

    val infos = comps.map { c =>
      teamOf(pid, c) match
        case Some(team) => CompInfo(c, Some(team.name), Seq.empty)
        case None =>
          val snos    = snosOf(pid, c)
          val partner = snos.flatMap(_.doubleIdsOpt).headOption.map((a, b) => if (a == pid) b else a)
            .flatMap(p => tourney.players.find(pl => pl != null && pl.id == p)).map(_.displayName)
          val stages  = stagesOf(c).sortBy(_.id.value).flatMap(s => snos.headOption.map(sno => s -> standing(c, s, sno)))
            .filter(_._2.nonEmpty)
          CompInfo(c, partner, stages)
    }

    val games = for
      c     <- comps if !shared.format.TeamEncounter.isTeam(c.typ)
      sno   <- snosOf(pid, c)
      stage <- stagesOf(c)
      m     <- stage.matches.toSeq if m.stNoA == sno || m.stNoB == sno
    yield (c, stage, m, sno)

    val next = games.filter((_, _, m, _) => !m.finished && m.status != MEntry.MS_SKIP)
      .map { (c, s, m, sno) => NextGame(c, s, m, pantName(c, if (m.stNoA == sno) m.stNoB else m.stNoA)) }
      .sortBy(g => (if (g.called) 0 else if (g.ready) 1 else 2, g.stage.id.value, -g.m.round, g.m.gameNo))

    val results = games.filter((_, _, m, _) => m.finished)
      .map { (c, s, m, sno) =>
        val isA  = m.stNoA == sno
        val opp  = if (isA) m.stNoB else m.stNoA
        val sets = if (isA) m.sets else m.sets.swap
        val won  = if (m.status == MEntry.MS_DRAW) None else Some(m.getWinner() == sno)
        GameResult(c, s, m, pantName(c, opp), sets, won, opp.isBye)
      }
      .sortBy(r => (-r.stage.id.value, r.m.round, -r.m.gameNo))

    View(player, clubName(player.clubId), infos, next, results)

  /** Startzeit eines Wettbewerbs für die Anzeige, z. B. „21.11. 10:00“. */
  def startDisplay(c: Competition): String =
    val d = Option(c.startDate).getOrElse("").replaceAll("[^0-9]", "")
    if (d.length >= 12) s"${d.substring(6, 8)}.${d.substring(4, 6)}. ${d.substring(8, 10)}:${d.substring(10, 12)}"
    else if (d.length >= 8) s"${d.substring(6, 8)}.${d.substring(4, 6)}."
    else ""

  // ---------------------------------------------------------------------------------------------------------------
  // Seite
  // ---------------------------------------------------------------------------------------------------------------

  def render(param: String = ""): Boolean =
    Global.currentSelection.tourney match
      case None =>
        loadPage(TourneyWelcome.name, "")
        false
      case Some(t) =>
        val players = participants
        val view    = storedPlayerId(t).flatMap(id => players.find(_.id == id)).map(buildView)
        setMain(cviews.pages.html.MyTourney(t, players, view, lastUpdate))
        if (view.isDefined) startTimer() else stopTimer()
        true

  /** Startet das automatische Neuladen (einmalig), solange die Seite angezeigt wird. */
  private def startTimer(): Unit =
    if (timer.isEmpty) timer = Some(dom.window.setInterval(() => tick(), RefreshMs))

  private def stopTimer(): Unit =
    timer.foreach(dom.window.clearInterval)
    timer = None

  private def tick(): Unit =
    if (Global.activePageName != name.value) stopTimer()
    else refresh()

  /**
   * Lädt Wettbewerbe und Spielphasen neu und zeigt die Seite erneut an. Im lokalen bzw. Demo-Modus liegen die Daten
   * im Browser, dort wird nur neu angezeigt.
   */
  private def refresh(): Unit =
    val now = new js.Date()
    lastUpdate = f"${now.getHours().toInt}%02d:${now.getMinutes().toInt}%02d"
    if (Global.isDemoMode || Global.isLocalMode || tourney.wpId <= 0 || tourney.wpId == 999999) render()
    else
      // Bearbeiter des Turniers: eigene, noch nicht gespeicherte Wettbewerbsänderungen nicht überschreiben
      val comps = if (Global.hasTourneyAccess(tourney)) Future.successful(()) else services.CompetitionDB.load().map(_ => ())
      for
        _ <- comps
        _ <- services.StageDB.refresh()
      do if (Global.activePageName == name.value) render()

  override def handleEvent(elem: HTMLElement, event: Event): Unit =
    HtmlId(elem.id) match
      case `BtnSelectId` =>
        val sel = gE(SelectId).asInstanceOf[dom.html.Select]
        sel.value.toIntOption.filter(_ > 0).foreach { id =>
          storePlayerId(tourney, Some(PlayerId(id)))
          refresh()
        }
      case `BtnChangeId` =>
        storePlayerId(tourney, None)
        render()
      case `BtnRefreshId` =>
        refresh()
      case `BtnBackId` =>
        stopTimer()
        val t = tourney
        val single = t.ident == "SIMPLE"
        loadPage(if (single) CompetitionWelcome.name else TourneyWelcome.name, "")
      case _ => debug(s"MyTourney handleEvent: ${elem.id}")
