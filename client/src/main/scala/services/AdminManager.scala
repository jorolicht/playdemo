package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import org.scalajs.dom
import shared.basic.Pickle.*
import shared.basic.AppError
import base.{Global, Logging}
import shared.model.*

/**
 * Service manager for administrative tasks in the tournament management application.
 * Provides the import of tournament data (export to file: [[TourneyDB.triggerExportDownload]]).
 */
object AdminManager extends ComWrapper {

  /**
   * Imports a tournament from a JSON string.
   * Reconstructs the fixed-size array buffers for competitions and stages
   * by restoring non-null elements to their original indices, padded with nulls.
   *
   * @param jsonString The JSON serialization of the Tourney case class.
   * @param overwrite True to overwrite an existing cloud tournament with the same slug.
   * @return A Future containing either an AppError or the slug of the newly created tournament.
   */
  def importTourney(jsonString: String, overwrite: Boolean = false): Future[Either[AppError, String]] = {
    try {
      val newTourney = try {
        read[services.DemoManager.DemoPayload](jsonString).tourney.tourney
      } catch {
        case _: Exception => read[Tourney](jsonString)
      }
      
      // Reset IDs to create a new tournament
      newTourney.wpId = 0
      newTourney.version = 1
      
      Logging.info(s"Starte Import für Turnier: ${newTourney.name}")
      
      // Save flat list of competitions and stages
      val importedComps = newTourney.competitions.toSeq.filter(_ != null)
      val importedStages = newTourney.stages.toSeq.filter(_ != null)
      
      // Re-initialize newTourney's competitions and stages arrays to the fixed sizes with nulls
      newTourney.competitions.clear()
      for (_ <- 0 until 64) newTourney.competitions += null
      importedComps.foreach { c =>
        val idx = c.id.value - 1
        if (idx >= 0 && idx < 64) newTourney.competitions(idx) = c
      }
      
      newTourney.stages.clear()
      for (_ <- 0 until 128) newTourney.stages += null
      importedStages.foreach { r =>
        val idx = r.id.value - 1
        if (idx >= 0 && idx < 128) newTourney.stages(idx) = r
      }

      TourneyDB.apiCreate(newTourney, overwrite).flatMap {
        case Right(slug) =>
          // Update the DB references and register sync handlers
          TourneyDB.update(newTourney, doSync = false)
          TourneyDB.version = 1
          ClubDB.version = 0
          PlayerDB.version = 0
          
          // Meta-Data Update
          val extraMetaPayload = Map(
              "startDate" -> newTourney.startDate.toString,
              "endDate" -> newTourney.endDate.toString,
              "ident" -> newTourney.ident,
              "category" -> newTourney.category.toString,
              "organizer" -> newTourney.organizer
          )

          // Trigger Syncs
          for {
            _ <- TourneyDB.sync()
            _ <- ClubDB.sync(TourneyDB.tourney.clubs.toSeq)
            _ <- PlayerDB.sync(TourneyDB.tourney.players.toSeq)
            _ <- CompetitionDB.sync(importedComps)
            _ <- StageDB.sync(importedStages)
            _ <- ajaxPost[Map[String,String], String]("/wp-json/tourney/v1/meta-data", List("postId" -> newTourney.wpId.toString), extraMetaPayload, host = Global.homeUrl).recover { case _ => Left(AppError("meta.skip", "")) }
          } yield Right(slug)
        case Left(err) => Future.successful(Left(err))
      }
    } catch {
      case e: Exception => 
        Logging.error(s"Import fehlgeschlagen: ${e.getMessage}")
        Future.successful(Left(AppError("err.import", "Invalid JSON format", e.getMessage, "AdminManager.importTourney")))
    }
  }
}
