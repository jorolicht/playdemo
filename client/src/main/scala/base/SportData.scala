package base

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom

import Logging.*
import shared.basic.Pickle.*
import shared.model.{ CompId, Stage }
import shared.sport.Sports

/**
 * Anbindung der versionierten Wertungssysteme im Client.
 *
 * - Kontext: Eine Stage rechnet im Wertungssystem ihres Wettbewerbs (`Stage.scoringOf`), alles andere im
 *   Wertungssystem des ausgewählten Wettbewerbs (`Sports.fallbackKey`).
 * - Laden zur Laufzeit: Die Indexdatei `data/sport_index.json` listet alle Versionen des Plugins. Versionen, die
 *   nicht schon beim Übersetzen eingebettet wurden (z. B. ein nachträglich hinterlegtes `sport_tt_002.json`), werden
 *   geladen und ergänzt. Eingebettete Versionen bleiben unverändert.
 */
object SportData:

  /**
   * Wertungssystem eines Wettbewerbs des geladenen Turniers.
   *
   * @param coId Wettbewerbs-Id
   * @return Schlüssel (leer = Wettbewerb ohne Eintrag bzw. unbekannt)
   */
  private def scoringOf(coId: CompId): String =
    services.TourneyDB.tourney.competitions.find(c => c != null && c.id == coId).map(_.scoring).getOrElse("")

  /**
   * Setzt den Kontext und lädt die nicht eingebetteten Versionen; scheitert nie (Fehler werden protokolliert).
   *
   * @param dataUrl Basis-URL des Plugin-Verzeichnisses `data/` (mit abschließendem "/")
   * @return Future, die nach dem Laden erfüllt ist
   */
  def init(dataUrl: String): Future[Unit] =
    Stage.scoringOf   = scoringOf
    Sports.fallbackKey = () => Global.currentSelection.competition.map(c => scoringOf(c.id)).getOrElse("")
    fetchText(s"${dataUrl}${Sports.IndexFileName}").flatMap {
      case None => Future.successful(())
      case Some(idx) =>
        val files = try read[Seq[String]](idx) catch { case e: Throwable => error(s"SportData: index: ${e.getMessage}"); Seq() }
        val extra = files.filterNot(f => Sports.embedded.contains(f.stripSuffix(".json")))
        Future.sequence(extra.map { f =>
          fetchText(s"${dataUrl}${Sports.dataFileName(f)}").map {
            case Some(json) => Sports.load(f, json) match
              case Right(key) => info(s"SportData: ${key} ergänzt")
              case Left(err)  => error(s"SportData: ${f} ungültig: ${err}")
            case None => ()
          }
        }).map(_ => ())
    }

  /**
   * Liest eine Textdatei.
   *
   * @param url URL
   * @return Inhalt, None bei Fehler (protokolliert)
   */
  private def fetchText(url: String): Future[Option[String]] =
    val opts = new dom.RequestInit {
      method = dom.HttpMethod.GET
      // neu hinterlegte Dateien sofort sehen: beim Server nachfragen (unverändert -> Antwort aus dem Cache)
      cache  = dom.RequestCache.`no-cache`
    }
    dom.fetch(url, opts).toFuture
      .flatMap { res =>
        if res.ok then res.text().toFuture.map(Some(_))
        else { warn(s"SportData: ${url}: HTTP ${res.status}"); Future.successful(None) }
      }
      .recover { case e: Throwable => warn(s"SportData: ${url}: ${e.getMessage}"); None }
