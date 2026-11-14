package services

import scala.concurrent.{ Future, Promise }
import scala.scalajs.js
import org.scalajs.dom

import base.Logging.*
import shared.basic.Pickle.*

/**
 * Eintrag der Liste "Meine Turniere" (zuletzt verwendete Turniere).
 *
 * @param kind Art: [[RecentTourneys.Local]] (im Browser gespeichert), [[RecentTourneys.File]] (exportierte bzw.
 *             geöffnete Datei) oder [[RecentTourneys.Cloud]]
 * @param key  Schlüssel: "local", Dateiname bzw. Slug des Cloud-Turniers
 * @param name Name des Turniers
 * @param ts   Zeitpunkt der letzten Verwendung (ms seit 1970)
 */
case class RecentTourney(kind: String, key: String, name: String, ts: Double) derives ReadWriter

/**
 * Zuletzt verwendete Turniere für das Menü "Meine Turniere".
 *
 * - Die Liste (höchstens [[MaxEntries]] Einträge) steht im localStorage.
 * - Von Dateien wird beim Export bzw. Öffnen eine Kopie im Browser (IndexedDB) abgelegt, damit der Eintrag ohne
 *   erneute Dateiauswahl geöffnet werden kann. Browser geben den Pfad einer Datei nicht heraus, angezeigt wird daher
 *   nur der Dateiname.
 * - Fehler (z. B. privater Modus ohne Speicher) werden protokolliert; die Liste ist dann leer.
 */
object RecentTourneys:

  val Local = "local"
  val File  = "file"
  val Cloud = "cloud"

  /** Höchstzahl der angezeigten Turniere. */
  val MaxEntries = 3

  private def storageKey = s"${base.Global.localStoragePrefix}recent_tourneys"

  /** Gespeicherte Einträge, neueste zuerst. */
  private def stored: Seq[RecentTourney] =
    try
      val raw = dom.window.localStorage.getItem(storageKey)
      if raw == null || raw.isEmpty then Seq() else read[Seq[RecentTourney]](raw).sortBy(-_.ts)
    catch
      case e: Throwable =>
        warn(s"RecentTourneys: ${e.getMessage}")
        Seq()

  /**
   * Name des im Browser gespeicherten lokalen Turniers.
   *
   * @return Name, None wenn kein lokales Turnier gespeichert ist
   */
  def localTourneyName: Option[String] =
    try
      val raw = dom.window.localStorage.getItem("App.demo_tourney")
      if raw == null || raw.trim.isEmpty then None
      else
        val d = js.JSON.parse(raw)
        def str(v: js.Dynamic): Option[String] = if js.isUndefined(v) || v == null then None else Some(v.toString).filter(_.nonEmpty)
        val t = d.selectDynamic("tourney")
        val name =
          if js.isUndefined(t) || t == null then str(d.selectDynamic("name"))
          else str(t.selectDynamic("name")).orElse {
            val tt = t.selectDynamic("tourney")
            if js.isUndefined(tt) || tt == null then None else str(tt.selectDynamic("name"))
          }
        Some(name.getOrElse("?"))
    catch case _: Throwable => None

  /**
   * Slug und Name des zuletzt geöffneten Cloud-Turniers aus dem Browser-Speicher (wie bisher die Statuszeile der
   * Startseite).
   *
   * @return (Slug, Name), None ohne gespeichertes Cloud-Turnier
   */
  private def lastCloud: Option[(String, String)] =
    try
      def get(k: String): String =
        Seq(dom.window.sessionStorage.getItem(k), dom.window.localStorage.getItem(k)).find(v => v != null && v.nonEmpty).getOrElse("")
      val slug = get("tourney_slug")
      if slug.isEmpty || slug == "local" then None else Some(slug -> Some(get("tourney_name")).filter(_.nonEmpty).getOrElse(slug))
    catch case _: Throwable => None

  /**
   * Einträge für das Menü, neueste zuerst, höchstens [[MaxEntries]].
   *
   * - Das lokale Turnier erscheint nur, solange es eines gibt (im Browser gespeichert oder gerade geöffnet), mit seinem
   *   aktuellen Namen.
   * - Ein lokales bzw. Cloud-Turnier, das schon vor Einführung der Liste gespeichert wurde, wird ergänzt.
   */
  def list: Seq[RecentTourney] =
    val local     = localTourneyName
    val hasLocal  = local.isDefined || base.Global.isLocalMode
    val st        = stored.flatMap(e => if e.kind != Local then Some(e) else if hasLocal then Some(e.copy(name = local.getOrElse(e.name))) else None)
    val oldLocal  = local.filter(_ => !st.exists(_.kind == Local)).map(n => RecentTourney(Local, "local", n, 0))
    val oldCloud  = lastCloud.filter((slug, _) => !st.exists(e => e.kind == Cloud && e.key == slug)).map((slug, n) => RecentTourney(Cloud, slug, n, 0))
    (st ++ oldLocal ++ oldCloud).take(MaxEntries)

  /**
   * Vermerkt die Verwendung eines Turniers; ältere Einträge über [[MaxEntries]] entfallen (samt Dateikopie).
   *
   * @param kind Art des Eintrags
   * @param key  Schlüssel
   * @param name Name des Turniers
   */
  def touch(kind: String, key: String, name: String): Unit =
    try
      val entry          = RecentTourney(kind, key, name, js.Date.now())
      val others         = stored.filterNot(e => e.kind == kind && e.key == key)
      val (keep, drop)   = (entry +: others).splitAt(MaxEntries)
      dom.window.localStorage.setItem(storageKey, write(keep))
      drop.filter(_.kind == File).foreach(e => FileCache.delete(e.key))
    catch case e: Throwable => warn(s"RecentTourneys.touch: ${e.getMessage}")

  /**
   * Vermerkt eine exportierte bzw. geöffnete Datei und legt ihre Kopie im Browser ab.
   *
   * @param fileName Dateiname
   * @param name     Name des Turniers
   * @param json     Inhalt der Datei
   */
  def touchFile(fileName: String, name: String, json: String): Unit =
    FileCache.put(fileName, json)
    touch(File, fileName, name)

  /**
   * Kopien der Turnierdateien im Browser (IndexedDB, Datenbank "tourney_files", Store "files", Schlüssel Dateiname).
   */
  object FileCache:
    private val DbName = "tourney_files"
    private val Store  = "files"

    /** Öffnet die Datenbank (legt den Store beim ersten Mal an). */
    private def open(): Future[js.Dynamic] =
      val p = Promise[js.Dynamic]()
      try
        val req = dom.window.asInstanceOf[js.Dynamic].indexedDB.open(DbName, 1)
        req.onupgradeneeded = (_: js.Any) => req.result.createObjectStore(Store)
        req.onsuccess       = (_: js.Any) => p.success(req.result)
        req.onerror         = (_: js.Any) => p.failure(new Exception("indexedDB open failed"))
      catch case e: Throwable => p.failure(e)
      p.future

    /** Führt eine Anfrage im Store aus. */
    private def request[T](mode: String)(op: js.Dynamic => js.Dynamic)(res: js.Dynamic => T): Future[T] =
      import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
      open().flatMap { db =>
        val p   = Promise[T]()
        val req = op(db.transaction(Store, mode).objectStore(Store))
        req.onsuccess = (_: js.Any) => p.success(res(req.result))
        req.onerror   = (_: js.Any) => p.failure(new Exception("indexedDB request failed"))
        p.future
      }

    /**
     * Legt die Kopie einer Datei ab (Fehler werden protokolliert).
     *
     * @param fileName Dateiname
     * @param json     Inhalt
     */
    def put(fileName: String, json: String): Unit =
      import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
      request("readwrite")(_.put(json, fileName))(_ => ()).failed.foreach(e => warn(s"FileCache.put: ${e.getMessage}"))

    /**
     * Liest die Kopie einer Datei.
     *
     * @param fileName Dateiname
     * @return Inhalt, None wenn keine Kopie vorhanden ist
     */
    def get(fileName: String): Future[Option[String]] =
      import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
      request("readonly")(_.get(fileName))(r => if js.isUndefined(r) || r == null then None else Some(r.toString))
        .recover { case e => warn(s"FileCache.get: ${e.getMessage}"); None }

    /**
     * Löscht die Kopie einer Datei (Fehler werden protokolliert).
     *
     * @param fileName Dateiname
     */
    def delete(fileName: String): Unit =
      import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
      request("readwrite")(_.delete(fileName))(_ => ()).failed.foreach(e => warn(s"FileCache.delete: ${e.getMessage}"))
