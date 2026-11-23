package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import shared.basic.Pickle.*
import shared.basic.AppError
import shared.model.{ Selection, Tourney, TourneyTemplate }
import base.{ Global, Logging }
import base.Messages.gM

/**
 * „Turnier kopieren“ (Turniervorlage aus dem Vorjahr): legt aus dem geöffneten Turnier ein neues an, siehe
 * [[shared.model.TourneyTemplate]].
 *
 * - Cloud-Turnier → neues Cloud-Turnier (reserviert ein Turnier vom Guthaben bzw. Lizenz), dazu die Homepage-Texte,
 *   die Anmeldeeinstellungen und das Logo.
 * - Lokales Turnier (auch Demo) → neues lokales Turnier; vorher fragt der bekannte Dialog, ob das bisherige lokale
 *   Turnier in eine Datei gespeichert werden soll.
 */
object TourneyCopy extends ComWrapper:

  /** Ist das Turnier ein Cloud-Turnier (nicht lokal, nicht Demo)? */
  private def isCloud(t: Tourney): Boolean =
    !Global.isLocalMode && !Global.isDemoMode && t.wpId > 0 && t.wpId != 999999

  /**
   * Fragt Name und Startdatum ab und legt die Kopie an.
   *
   * @param src geöffnetes Turnier
   */
  def run(src: Tourney): Unit =
    dialogs.DlgCopyTourney.show(src).foreach {
      case Some((name, date)) =>
        val copy = TourneyTemplate.copy(src, name, date)
        if isCloud(src) then copyToCloud(src, copy) else copyToLocal(copy)
      case None => Logging.debug("Copy tourney cancelled")
    }

  /** Neues Cloud-Turnier mit Homepage-Einstellungen und Logo des Quellturniers. */
  private def copyToCloud(src: Tourney, copy: Tourney): Unit =
    val srcAci = Global.currentAci
    // offene Änderungen des Quellturniers zuerst speichern
    TourneyDB.sync().flatMap(_ => AdminManager.importTourney(write(copy))).foreach {
      case Right(slug) =>
        val newId = TourneyDB.tourney.wpId
        val aci = srcAci.copy(logoUrl = "", archived = false)
        val logo: Future[Unit] =
          if srcAci.logoUrl.isEmpty then Future.successful(())
          else ajaxPost[Map[String, Int], ujson.Value]("/wp-json/tourney/v1/logo-copy",
                 List("postId" -> newId.toString), Map("from" -> src.wpId), host = Global.homeUrl).map {
                   case Left(err) => Logging.error(s"TourneyCopy: logo-copy ${err.msgCode}")
                   case _         => ()
                 }
        for
          _ <- TourneyDB.saveAci(newId, aci)
          _ <- logo
          _ <- TourneyDB.loadAci(newId)
        do
          Global.isLocalMode = false
          Global.isDemoMode = false
          Global.tourneySlug = slug
          Global.currentSelection = Selection(Some(TourneyDB.tourney))
          comps.Navbar.openTourneyView(TourneyDB.tourney)
      case Left(err) =>
        dialogs.DlgMsgbox.showError(err, gM("dlg.copyTourney.error"))
    }

  /** Neues lokales Turnier anstelle des bisherigen lokalen Turniers. */
  private def copyToLocal(copy: Tourney): Unit =
    comps.Navbar.confirmOverwriteLocal { () =>
      Global.isLocalMode = true
      Global.isDemoMode = false
      copy.wpId = 999999
      TourneyDB.tourney = copy
      TourneyDB.version = 1
      TourneyDB.sync()
      ClubDB.sync(Seq.empty)
      PlayerDB.sync(Seq.empty)
      CompetitionDB.sync(copy.competitions.filter(_ != null).toSeq)
      StageDB.sync(Seq.empty)
      Global.currentSelection = Selection(Some(copy))
      comps.Navbar.openTourneyView(copy)
    }
