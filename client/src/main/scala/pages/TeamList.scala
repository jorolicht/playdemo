package pages

import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.raw.HTMLElement
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.model.*
import shared.BoxButton
import shared.format.TeamEncounter
import shared.sport.EncounterCfg

/**
 * Seite "Mannschaften": Mannschaften der Mannschaftswettbewerbe anlegen, bearbeiten und löschen sowie Spieler
 * zuordnen. Eine angelegte Mannschaft ist im Wettbewerb gemeldet (Teilnehmer `[T<id>]`).
 */
object TeamList extends BasePage with JsWrapper:
  def name = PageNameTyp("TeamList")

  /** Button "Mannschaft hinzufügen", Suffix = Wettbewerbs-Id. */
  val BtnAddTeam:    HtmlId = genId(name)
  /** Button "Spieler anlegen". */
  val BtnAddPlayer:  HtmlId = genId(name)
  /** Button "Bearbeiten", Suffix = Wettbewerbs-Id_Mannschafts-Id. */
  val BtnEditTeam:   HtmlId = genId(name)
  /** Button "Löschen", Suffix = Wettbewerbs-Id_Mannschafts-Id. */
  val BtnDeleteTeam: HtmlId = genId(name)

  /**
   * Zeile der Mannschaftstabelle.
   *
   * @param team     Mannschaft
   * @param clubName Verein
   * @param players  Spielernamen, Stammspieler zuerst
   * @param rating   durchschnittliche Wertung der Stammspieler
   */
  case class TeamRow(team: Team, clubName: String, players: Seq[String], rating: Int)

  /**
   * Abschnitt je Mannschaftswettbewerb.
   *
   * @param comp   Wettbewerb
   * @param cfg    Spielsystem der Begegnung
   * @param rows   Mannschaften
   * @param locked Meldung gesperrt (Wettbewerb läuft bereits)
   */
  case class CompSection(comp: Competition, cfg: EncounterCfg, rows: Seq[TeamRow], locked: Boolean)

  /** Mannschaftswettbewerbe des Turniers mit ihrem Spielsystem. */
  private def teamComps: Seq[(Competition, EncounterCfg)] =
    services.TourneyDB.tourney.competitions.toSeq
      .filter(c => c != null && !c.deleted)
      .flatMap(c => TeamEncounter.config(c.typ).map(cfg => (c, cfg)))

  /**
   * Rendert die Seite.
   *
   * @param param nicht verwendet
   * @return true
   */
  def render(param: String = ""): Boolean =
    comps.ContextHeader.render()
    val tourney  = services.TourneyDB.tourney
    val clubName = (id: Int) => tourney.clubs.find(_.id.toInt == id).map(_.name).getOrElse("")
    val sections = teamComps.map { case (c, cfg) =>
      val rows = c.teams.toSeq.sortBy(_.id).map { t =>
        val names = t.players.map(id => tourney.players.find(_.id == id).map(_.displayName).getOrElse(s"[${id.value}]"))
        TeamRow(t, clubName(t.clubId), names, t.rating(cfg.teamSize, tourney.players.toSeq))
      }
      CompSection(c, cfg, rows, tourney.isRegLocked(c) || !Global.hasTourneyAccess(tourney))
    }
    setMain(cviews.pages.html.TeamList(sections))
    true

  /**
   * Verarbeitet die Klicks der Seite.
   *
   * @param elem  auslösendes Element
   * @param event Ereignis
   */
  override def handleEvent(elem: HTMLElement, event: Event): Unit =
    val id = elem.id
    def suffix(prefix: HtmlId): String = id.substring(prefix.id.length + 1)
    def compTeam(prefix: HtmlId): Option[(Competition, EncounterCfg, Team)] =
      suffix(prefix).split('_') match
        case Array(c, t) =>
          teamComps.find(_._1.id.value == c.toInt).flatMap { case (comp, cfg) =>
            comp.teams.find(_.id == t.toInt).map(team => (comp, cfg, team))
          }
        case _ => None

    if id.startsWith(BtnAddTeam.id + "-") then
      teamComps.find(_._1.id.value == suffix(BtnAddTeam).toInt).foreach { case (c, cfg) => editTeam(c, cfg, None) }
    else if id.startsWith(BtnEditTeam.id + "-") then
      compTeam(BtnEditTeam).foreach { case (c, cfg, t) => editTeam(c, cfg, Some(t)) }
    else if id.startsWith(BtnDeleteTeam.id + "-") then
      compTeam(BtnDeleteTeam).foreach { case (c, _, t) => deleteTeam(c, t) }
    else if id == BtnAddPlayer.id then
      addPlayer()

  /**
   * Öffnet den Dialog "Mannschaft hinzufügen" für einen Mannschaftswettbewerb (z. B. aus der Anmeldung).
   *
   * @param comp   Wettbewerb
   * @param onDone wird nach erfolgreichem Speichern aufgerufen
   */
  def addTeam(comp: Competition, onDone: () => Unit): Unit =
    TeamEncounter.config(comp.typ).foreach(cfg => editTeam(comp, cfg, None, onDone))

  /**
   * Legt eine Mannschaft an oder ändert sie und aktualisiert den zugehörigen Teilnehmer des Wettbewerbs.
   *
   * @param comp   Wettbewerb
   * @param cfg    Spielsystem (Stammspieler, Kadergröße)
   * @param team   bestehende Mannschaft, None = neu
   * @param onDone wird nach erfolgreichem Speichern aufgerufen, Default: Seite neu rendern
   */
  private def editTeam(comp: Competition, cfg: EncounterCfg, team: Option[Team], onDone: () => Unit = () => { render(); () }): Unit =
    val tourney  = services.TourneyDB.tourney
    val title    = if team.isEmpty then gM("TeamList.add_team") else gM("TeamList.edit_team")
    val clubName = team.flatMap(t => tourney.clubs.find(_.id.toInt == t.clubId)).map(_.name).getOrElse("")
    // Spieler anderer Mannschaften dieses Wettbewerbs sind nicht wählbar
    val usedElsewhere = comp.teams.filterNot(t => team.exists(_.id == t.id)).flatMap(_.players).toSet
    val players  = tourney.players.toSeq.filter(p => p.active && !usedElsewhere.contains(p.id)).sortBy(_.displayName.toLowerCase)
    dialogs.DlgTeam.show(title, team, clubName, players, tourney.clubs.toSeq, cfg.teamSize, cfg.maxPlayers,
                         res => PlayerRegistration.findOrAddPlayer(res).map(pl => (pl, tourney.clubs.toSeq))).map {
      case Right(res) =>
        if res.players.exists(usedElsewhere.contains) then
          dom.window.alert(gM("TeamList.err_player_in_other_team"))
        else
          val clubId =
            if res.clubName.isEmpty then 0
            else tourney.clubs.find(_.name.equalsIgnoreCase(res.clubName)).map(_.id.toInt)
                   .orElse(tourney.addClub(res.clubName, checkSimilarity = false).toOption.map(_.id.toInt)).getOrElse(0)
          val t = team.getOrElse {
            val nt = Team(comp.teams.map(_.id).maxOption.getOrElse(0) + 1, res.name)
            comp.teams += nt
            nt
          }
          t.name    = res.name
          t.clubId  = clubId
          t.players = res.players
          val pant = Pant(
            id     = t.sno,
            name   = t.name,
            club   = res.clubName,
            rating = t.rating(cfg.teamSize, tourney.players.toSeq),
            active = true,
            status = PantStatus.PLAY
          )
          val idx = comp.pants1Stage.indexWhere(_.id == t.sno)
          if idx >= 0 then comp.pants1Stage(idx) = pant.copy(status = comp.pants1Stage(idx).status, active = comp.pants1Stage(idx).active)
          else comp.pants1Stage += pant
          tourney.updateCompetition(comp) match
            case Left(err) => dialogs.DlgMsgbox.showError(err, gM("TeamList.err_save"))
            case Right(_)  => onDone()
      case Left(_) => ()
    }

  /**
   * Löscht eine Mannschaft nach Rückfrage samt Meldung im Wettbewerb.
   *
   * @param comp Wettbewerb
   * @param team Mannschaft
   */
  private def deleteTeam(comp: Competition, team: Team): Unit =
    dialogs.DlgMsgbox.show(gM("TeamList.confirm_delete", team.name), gM("TeamList.delete_title"), List(BoxButton.Yes, BoxButton.No)).map {
      case BoxButton.Yes =>
        comp.teams.filterInPlace(_.id != team.id)
        comp.pants1Stage.filterInPlace(_.id != team.sno)
        services.TourneyDB.tourney.updateCompetition(comp) match
          case Left(err) => dialogs.DlgMsgbox.showError(err, gM("TeamList.err_save"))
          case Right(_)  => render()
      case _ => ()
    }

  /**
   * Legt einen Spieler im Turnier an, ohne ihn in einem Wettbewerb zu melden.
   */
  private def addPlayer(): Unit =
    val tourney = services.TourneyDB.tourney
    dialogs.DlgAddSingle.show(tourney.clubs.toSeq).map {
      case Right(res) =>
        val club = tourney.clubs.find(_.name.equalsIgnoreCase(res.clubName)).orElse(tourney.addClub(res.clubName, checkSimilarity = false).toOption)
        val clubId = club.map(_.id.toInt).getOrElse(0)
        tourney.addPlayer(res.firstName, res.lastName, clubId, res.year, res.email, res.whatsApp) match
          case Right(p) =>
            tourney.updatePlayer(p.copy(meta = p.meta.copy(ttr = res.ttr)))
            render()
          case Left(err) => dialogs.DlgMsgbox.showError(err, gM("TeamList.err_save"))
      case Left(_) => ()
    }
