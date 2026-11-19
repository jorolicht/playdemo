package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.*

import shared.basic.Pickle.*
import shared.basic.AppError
import scala.util.control.NonFatal
import scala.collection.mutable.ArrayBuffer
import shared.model.{Stage, StageId, CompId, StageConfig, StageStatus}
import base.{Global, Logging}
import base.Messages.gM

/**
 * StageDB provides methods to manage stages and synchronize them with the WordPress server.
 * It supports up to 128 stages.
 * Stages are stored in custom post type meta fields stage001 to stage128.
 * Optimistic locking is implemented using a version counter.
 *
 * Multiuser (siehe [[MultiUser]]): Zu jeder Stage wird der zuletzt geladene bzw. gespeicherte Stand (Basis)
 * gemerkt und beim Speichern dessen Version mitgeschickt. Hat ein anderer Bearbeiter die Stage inzwischen
 * gespeichert, lehnt WordPress mit `version_mismatch` ab; dann werden die eigenen Änderungen je Spiel in den
 * aktuellen Stand übernommen („letzte Eingabe gewinnt“, Hinweis bei überschriebenen Spielen) und erneut gespeichert.
 */
object StageDB extends ComWrapper with Debouncer:

  /** Zum Speichern vorgemerkte Stages (Stage-ID → Stand), gesammelt über alle Änderungen bis zum Speichern. */
  private val pending = scala.collection.mutable.LinkedHashMap[Int, Stage]()

  /** Stages, deren Speicherung gerade läuft. */
  private val inFlight = scala.collection.mutable.Set[Int]()

  /** Multiuser: zuletzt geladener bzw. gespeicherter Stand je Stage-ID (Version, JSON). */
  private val baseline = scala.collection.mutable.Map[Int, (Int, String)]()

  /** Turnier (wpId), zu dem die Basisstände gehören. */
  private var baseWpId: Int = 0

  /**
   * Merkt geänderte Stages vor und speichert sie gesammelt nach 800 ms ohne weitere Änderung.
   *
   * @param dirty geänderte Stages
   */
  def triggerSync(dirty: Seq[Stage]): Unit =
    dirty.foreach(s => pending(s.id.value) = s)
    debounce(delay = 800) {
      val all = pending.values.toSeq
      pending.clear()
      Logging.debug(s"Synchronisiere ${all.length} Stages mit dem Server...")
      sync(all)
    }
  // Helper to access the current tourney's stages
  def stages: ArrayBuffer[Stage] = TourneyDB.tourney.stages

  private val route = "/wp-json/tourney/v1/stages-sync"

  /**
   * Eintrag für das Änderungsprotokoll.
   *
   * @param id   Stage-ID
   * @param text Beschreibung, z. B. "Herren A – Gruppen: Spiel 12 Müller – Meier 3:1"
   */
  case class ChangeLogItem(id: Int, text: String) derives ReadWriter

  /**
   * @param events geänderte Stages
   * @param bases  Multiuser: Version, auf der die Änderung beruht (Stage-ID → Version)
   * @param log    Multiuser: Texte für das Änderungsprotokoll
   */
  case class StageSyncRequest(events: Seq[Stage], bases: Map[String, Int] = Map(), log: Seq[ChangeLogItem] = Seq()) derives ReadWriter
  case class StageSyncResponse(success: Boolean) derives ReadWriter
  case class StagesResponse(stages: Seq[Stage]) derives ReadWriter

  /**
   * Initializes the sync handler for the current tournament.
   */
  def initHandler(): Unit =
    TourneyDB.tourney.setStageSyncHandler { dirty => triggerSync(dirty) }

  /**
   * Synchronizes pending stage events with the WordPress server.
   */
  def sync(dirty: Seq[Stage]): Future[Either[AppError, Unit]] = sync(dirty, retries = 2)

  /**
   * Speichert Stages; bei einem Konflikt (Multiuser) werden die Änderungen zusammengeführt und erneut gespeichert.
   *
   * @param dirty   zu speichernde Stages
   * @param retries verbleibende Versuche nach einem Konflikt
   */
  private def sync(dirty: Seq[Stage], retries: Int): Future[Either[AppError, Unit]] = {
    if (base.Global.isDemoMode || base.Global.isLocalMode) {
      val req = StageSyncRequest(TourneyDB.tourney.stages.filter(_ != null).toSeq)
      org.scalajs.dom.window.localStorage.setItem("App.demo_stages", write(req))
      Future.successful(Right(()))
    } else if (dirty.isEmpty || TourneyDB.tourney.wpId == 0) {
      Future.successful(Right(()))
    } else {
      val multi = MultiUser.enabled
      val prev: Map[Int, (Int, String)] =
        if (multi && baseWpId == TourneyDB.tourney.wpId) dirty.flatMap(s => baseline.get(s.id.value).map(s.id.value -> _)).toMap
        else Map()
      val log = if (multi) changeLog(dirty, prev) else Seq()
      val req = StageSyncRequest(dirty, prev.map { case (id, (ver, _)) => id.toString -> ver }, log)
      val params = List("postId" -> TourneyDB.tourney.wpId.toString)
      // der gesendete Stand wird (vorläufig) zur neuen Basis, damit eine direkt folgende Speicherung darauf aufbaut
      if (multi) setBases(dirty)
      val ids = dirty.map(_.id.value)
      inFlight ++= ids

      ajaxPost[StageSyncRequest, StageSyncResponse](
        route,
        params,
        req,
        host = Global.homeUrl
      ).flatMap {
        case Right(res) =>
          inFlight --= ids
          if (multi) MultiUser.notifyChange("stage", noteText(dirty, log))
          Future.successful(Right(()))
        case Left(err) if err.is("version_mismatch") && multi && retries > 0 =>
          inFlight --= ids
          restoreBases(ids, prev)
          Logging.info(s"StageDB: Stage inzwischen von anderem Bearbeiter gespeichert, führe Änderungen zusammen... ${err.msg}")
          merge(dirty, prev).flatMap {
            case Right(merged) => sync(merged, retries - 1)
            case Left(e)       => Future.successful(Left(e))
          }
        case Left(err) if err.is("version_mismatch") =>
          inFlight --= ids
          Logging.error(s"Sync fehlgeschlagen: Version-Mismatch bei Stages. Lade neu... ${err.msg}")
          load().map(_ => Left(err))
        case Left(err) =>
          inFlight --= ids
          if (multi) restoreBases(ids, prev)
          Future.successful(Left(err))
      }
    }
  }

  /**
   * Merkt den aktuellen Stand von Stages als Basis für das Zusammenführen (Multiuser).
   *
   * @param list Stages
   */
  private def setBases(list: Seq[Stage]): Unit =
    if (baseWpId != TourneyDB.tourney.wpId) { baseline.clear(); baseWpId = TourneyDB.tourney.wpId }
    list.foreach(s => baseline(s.id.value) = (s.version, write(s)))

  /**
   * Stellt nach einer gescheiterten Speicherung die vorherigen Basisstände wieder her.
   *
   * @param ids  Stage-IDs der Speicherung
   * @param prev vorherige Basisstände
   */
  private def restoreBases(ids: Seq[Int], prev: Map[Int, (Int, String)]): Unit =
    ids.foreach(id => prev.get(id) match
      case Some(b) => baseline(id) = b
      case None    => baseline.remove(id)
    )

  /**
   * Legt eine Stage in der Turnier-Datenhaltung ab und aktualisiert ggf. die aktuelle Auswahl.
   *
   * @param s Stage
   */
  private def store(s: Stage): Unit =
    val idx = s.id.value - 1
    if (idx >= 0 && idx < 128) {
      val t = TourneyDB.tourney
      while (t.stages.length < 128) t.stages += null
      t.stages(idx) = s
      if (Global.currentSelection.stage.exists(_.id == s.id))
        Global.currentSelection = Global.currentSelection.copy(stage = Some(s))
    }

  /** JSON eines Spiels für Vergleiche. */
  private def js(m: shared.model.MEntry): String = write(m)

  /** JSON einer Begegnung (Mannschaften) für Vergleiche. */
  private def jsEnc(s: Stage, gameNo: Int): String = s.encounters.find(_.gameNo == gameNo).map(e => write(e)).getOrElse("")

  /**
   * Bezeichnung einer Stage für Hinweise und Protokoll: "Wettbewerb – Stage".
   *
   * @param s Stage
   */
  private def label(s: Stage): String =
    val comp = TourneyDB.tourney.competitions.find(c => c != null && c.id == s.coId).map(_.name).getOrElse("")
    if (comp.isEmpty) s.name else s"$comp – ${s.name}"

  /**
   * Aufbau einer Stage ohne Spiele/Ergebnisse. Unterscheidet er sich zwischen Basis und eigenem Stand, wurde die
   * Stage umgebaut (z. B. ausgelost, gestartet, neue Runde); dann gilt der eigene Stand als Ganzes.
   * Der Wechsel zwischen „Spiele laufen“ und „Beendet“ ergibt sich aus den Ergebnissen und zählt nicht dazu.
   */
  private def structure(s: Stage): Any =
    val status = if (s.status == StageStatus.FIN) StageStatus.EIN else s.status
    (s.name, s.stageConfig, status, s.size, s.noPlayers, s.noWinSets, s.prefId, s.nextIds, s.quali, s.deleted,
     s.certificate, s.encVariant, s.encSystem, s.link, s.finalReset, s.matches.length)

  /**
   * Führt nach einem Konflikt die eigenen Änderungen mit dem aktuellen Stand in WordPress zusammen.
   * Stages anderer Bearbeiter, die man selbst nicht geändert hat, werden dabei ebenfalls aktualisiert.
   *
   * @param dirty eigene, nicht gespeicherte Stände
   * @param prev  Basisstände, auf denen die eigenen Änderungen beruhen
   * @return zusammengeführte Stages zum erneuten Speichern
   */
  private def merge(dirty: Seq[Stage], prev: Map[Int, (Int, String)]): Future[Either[AppError, Seq[Stage]]] =
    val params = List("postId" -> TourneyDB.tourney.wpId.toString)
    ajaxGet[StagesResponse]("/wp-json/tourney/v1/stages", params, host = Global.homeUrl).map {
      case Left(err) => Left(err)
      case Right(res) =>
        val remote    = res.stages.filter(_.id.value > 0).map(s => s.id.value -> s).toMap
        val dirtyIds  = dirty.map(_.id.value).toSet
        val conflicts = ArrayBuffer[String]()
        // der Stand in WordPress ist die neue Basis (vor dem Zusammenführen merken, das ihn verändert)
        // (Stages mit weiteren, noch nicht gesendeten eigenen Änderungen behalten ihre Basis)
        setBases(remote.values.filter(r => dirtyIds.contains(r.id.value) || !pending.contains(r.id.value)).toSeq)
        val merged = dirty.map { own =>
          val id = own.id.value
          (remote.get(id), prev.get(id).map { case (_, json) => read[Stage](json) }) match
            case (Some(rem), Some(b)) if structure(own) == structure(b) =>
              mergeMatches(own, b, rem, conflicts)
            case (Some(rem), b) =>
              if (b.forall(_.version != rem.version)) conflicts += label(own)
              own.version = rem.version + 1
              own
            case (None, _) => own
        }
        // fremde Änderungen an den übrigen Stages übernehmen
        remote.values.filter(r => !dirtyIds.contains(r.id.value) && !pending.contains(r.id.value)).foreach(store)
        merged.foreach(store)
        if (conflicts.nonEmpty) MultiUser.showToast(gM("multiuser.conflict", conflicts.distinct.mkString(", ")))
        MultiUser.updateSelection()
        MultiUser.refreshView()
        Right(merged)
    }

  /**
   * Übernimmt die selbst geänderten Spiele (und Begegnungen) in den aktuellen Stand aus WordPress.
   * Hat auch der andere Bearbeiter dasselbe Spiel anders eingetragen, gilt die eigene (letzte) Eingabe.
   *
   * @param own       eigener Stand
   * @param b         Basis, auf der der eigene Stand beruht
   * @param rem       aktueller Stand in WordPress (wird verändert und zurückgegeben)
   * @param conflicts Sammlung der überschriebenen Spiele für den Hinweis
   * @return zusammengeführte Stage mit Version rem.version + 1
   */
  private def mergeMatches(own: Stage, b: Stage, rem: Stage, conflicts: ArrayBuffer[String]): Stage =
    def find(s: Stage, g: Int) = s.matches.find(_.gameNo == g)
    val changed = own.matches.map(_.gameNo).filter { g =>
      find(b, g).map(js) != find(own, g).map(js) || jsEnc(b, g) != jsEnc(own, g)
    }
    changed.foreach { g =>
      val remChanged = find(rem, g).map(js) != find(b, g).map(js) || jsEnc(rem, g) != jsEnc(b, g)
      val differs    = find(rem, g).map(js) != find(own, g).map(js) || jsEnc(rem, g) != jsEnc(own, g)
      if (remChanged && differs) conflicts += s"${label(own)}: ${gM("multiuser.game", g.toString)}"
      own.encounters.find(_.gameNo == g).foreach { e =>
        val i = rem.encounters.indexWhere(_.gameNo == g)
        if (i >= 0) rem.encounters(i) = e else rem.encounters += e
      }
      find(own, g).foreach(m => rem.setModel(m))
    }
    shared.sport.Sports.using(rem.scoring) { rem.updateStatus() }
    rem.version = rem.version + 1
    rem

  /**
   * Erzeugt die Texte für das Änderungsprotokoll: neue, geänderte und gelöschte Ergebnisse sowie Statuswechsel.
   *
   * @param dirty zu speichernde Stages
   * @param prev  Basisstände
   */
  private def changeLog(dirty: Seq[Stage], prev: Map[Int, (Int, String)]): Seq[ChangeLogItem] =
    import shared.model.MEntry
    def done(m: MEntry) = m.status == MEntry.MS_FIN || m.status == MEntry.MS_DRAW
    dirty.flatMap { own =>
      prev.get(own.id.value).toSeq.flatMap { case (_, json) =>
        val b    = read[Stage](json)
        val lbl  = label(own)
        val comp = TourneyDB.tourney.competitions.find(c => c != null && c.id == own.coId)
        val pants = comp.map(_.pants1Stage.toSeq).getOrElse(Seq.empty)
        def names(m: MEntry) =
          s"${pages.Stage.StageInput.formatSnoName(m.stNoA, pants)} – ${pages.Stage.StageInput.formatSnoName(m.stNoB, pants)}"
        val status =
          if (own.status != b.status) Seq(ChangeLogItem(own.id.value, s"$lbl: ${gM(s"StageStatus.${own.status}")}"))
          else Seq()
        val results = own.matches.toSeq.flatMap { m =>
          b.matches.find(_.gameNo == m.gameNo).filter(bm => js(bm) != js(m)).flatMap { bm =>
            val game = gM("multiuser.game", m.gameNo.toString)
            if (done(m) && (!done(bm) || bm.sets != m.sets || bm.result != m.result)) {
              val balls = if (m.result.trim.nonEmpty) s" (${m.result})" else ""
              Some(ChangeLogItem(own.id.value, s"$lbl: $game ${names(m)} ${m.sets._1}:${m.sets._2}$balls"))
            } else if (!done(m) && done(bm)) {
              Some(ChangeLogItem(own.id.value, s"$lbl: $game ${names(bm)} – ${gM("multiuser.resultDeleted")}"))
            } else None
          }
        }
        status ++ results
      }
    }

  /**
   * Kurztext der Änderungsmeldung an andere Bearbeiter.
   *
   * @param dirty gespeicherte Stages
   * @param log   Protokolltexte
   */
  private def noteText(dirty: Seq[Stage], log: Seq[ChangeLogItem]): String =
    log match
      case Seq()      => s"${dirty.map(label).distinct.mkString(", ")} ${gM("multiuser.changed")}"
      case Seq(one)   => one.text
      case first +: _ => s"${first.text} (+${log.length - 1})"

  /**
   * Multiuser: lädt die Stages nach einer Änderungsmeldung eines anderen Bearbeiters neu. Stages mit eigenen,
   * noch nicht gespeicherten Änderungen bleiben unverändert; sie werden beim Speichern zusammengeführt.
   *
   * @return erledigt, wenn die Stages geladen sind
   */
  def refresh(): Future[Unit] =
    val params = List("postId" -> TourneyDB.tourney.wpId.toString)
    ajaxGet[StagesResponse]("/wp-json/tourney/v1/stages", params, host = Global.homeUrl).map {
      case Right(res) =>
        res.stages.filter(_.id.value > 0).foreach { r =>
          val id = r.id.value
          val local = TourneyDB.tourney.stages.lift(id - 1).filter(_ != null)
          if (!pending.contains(id) && !inFlight.contains(id) && !local.exists(_.version == r.version)) {
            store(r)
            setBases(Seq(r))
          }
        }
      case Left(err) => Logging.error(s"StageDB.refresh: ${err.msgCode}")
    }

  /**
   * Loads stages from the WordPress server.
   */
  def load(): Future[Either[AppError, Long]] = {
    if (base.Global.isDemoMode || base.Global.isLocalMode) {
      val t = TourneyDB.tourney
      while (t.stages.length < 128) t.stages += null
      val jsStr = org.scalajs.dom.window.localStorage.getItem("App.demo_stages")
      if (jsStr != null && jsStr.nonEmpty) {
        val req = read[StageSyncRequest](jsStr)
        t.stages.clear()
        for (i <- 0 until 128) t.stages += null
        req.events.foreach { r =>
           val targetIdx = if (r.id.value > 0 && r.id.value <= 128) r.id.value - 1 else t.stages.indexOf(null)
           if (targetIdx >= 0 && targetIdx < 128) then
             val stageToStore = if (r.id.value > 0) r else r.copy(id = StageId(targetIdx + 1))
             t.stages(targetIdx) = stageToStore
        }
        t.dirtyStage.clear()
        Logging.debug(s"StageDB.load(demo): loaded ${req.events.length} stages")
      }
      initHandler()
      return Future.successful(Right(0L))
    } else if (TourneyDB.tourney.wpId == 0) {
      Logging.debug("StageDB.load: tourney.id is 0, skipping load")
      return Future.successful(Right(0L))
    }

    val params = List("postId" -> TourneyDB.tourney.wpId.toString)
    ajaxGet[StagesResponse]("/wp-json/tourney/v1/stages", params, host = Global.homeUrl).map {
      case Right(res) =>
        if (TourneyDB.tourney.wpId != 0) {
          val t = TourneyDB.tourney
          t.stages.clear()
          for (i <- 0 until 128) t.stages += null
          res.stages.foreach { r =>
             val targetIdx = if (r.id.value > 0 && r.id.value <= 128) r.id.value - 1 else t.stages.indexOf(null)
             if (targetIdx >= 0 && targetIdx < 128) then
               val stageToStore = if (r.id.value > 0) r else r.copy(id = StageId(targetIdx + 1))
               t.stages(targetIdx) = stageToStore
          }
          t.dirtyStage.clear()
          initHandler() // Ensure handler is set
          baseline.clear()
          setBases(t.stages.filter(_ != null).toSeq)
        }
        Logging.debug(s"StageDB.load: loaded ${res.stages.length} stages")
        Right(0L)
      case Left(err) => Left(err)
    }
  }
