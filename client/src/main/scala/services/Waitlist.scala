package services

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import shared.basic.Pickle.*
import shared.model.{ Competition, SNO }
import base.{ Global, Logging }

/**
 * Warteliste der Online-Anmeldung: Nachrücken, wenn ein Platz frei wird (Abmeldung, höhere Höchstzahl), siehe
 * [[shared.model.Competition.promoteWaiting]]. Bei Cloud-Turnieren erhalten Nachrücker mit E-Mail-Adresse eine Mail
 * (REST `waitlist-notify`, includes/api/waitlist.php).
 */
object Waitlist extends ComWrapper:

  /** Body für waitlist-notify. */
  case class NotifyRequest(compId: Int, playerIds: List[Int], lang: String) derives ReadWriter

  /**
   * Lässt Spieler der Warteliste nachrücken, speichert den Wettbewerb und benachrichtigt die Nachrücker.
   *
   * @param comp Wettbewerb (wird verändert)
   * @return Anzahl nachgerückter Meldungen
   */
  def promote(comp: Competition): Int =
    val moved = comp.promoteWaiting()
    if moved.nonEmpty then
      TourneyDB.tourney.updateCompetition(comp)
      val ids = moved.filter(_.id.isSingle).flatMap(_.id.toString.stripPrefix("[").stripSuffix("]").toIntOption)
      val cloud = !Global.isLocalMode && !Global.isDemoMode && TourneyDB.tourney.wpId > 0 && TourneyDB.tourney.wpId != 999999
      if cloud && ids.nonEmpty then
        val req = NotifyRequest(comp.id.value, ids, if Global.lang == "en" then "en" else "de")
        // erst nach dem Speichern des Wettbewerbs benachrichtigen (der Server prüft die Meldung)
        val saved = TourneyDB.tourney.competitions.find(c => c != null && c.id == comp.id).getOrElse(comp)
        CompetitionDB.sync(Seq(saved)).flatMap { _ =>
          ajaxPost[NotifyRequest, ujson.Value]("/wp-json/tourney/v1/waitlist-notify",
            List("postId" -> TourneyDB.tourney.wpId.toString), req, host = Global.homeUrl)
        }.foreach {
          case Left(err) => Logging.error(s"Waitlist: notify ${err.msgCode}")
          case Right(_)  => Logging.debug(s"Waitlist: notified ${ids.mkString(",")}")
        }
    moved.size
