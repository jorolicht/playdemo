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
 * Provides functions for exporting and importing tournament data.
 */
object AdminManager extends ComWrapper {

  /**
   * Exports the currently loaded tournament as a JSON file.
   * Filters out null entries from the competitions and stages lists to optimize size.
   */
  def exportCurrentTourney(): Unit = {
    val t = TourneyDB.tourney
    if (t.wpId == 0) {
      dom.window.alert("Es ist kein Turnier geladen, das exportiert werden könnte.")
      return
    }

    // Clone and clean arrays to exclude null items from serialized JSON
    val cleanTourney = t.copy(
      clubs = t.clubs.clone(),
      players = t.players.clone(),
      competitions = t.competitions.filter(_ != null),
      stages = t.stages.filter(_ != null)
    )

    val jsonString = write(cleanTourney, indent = 2)
    val blob = new dom.Blob(js.Array(jsonString), dom.BlobPropertyBag(`type` = "application/json"))
    val url = dom.URL.createObjectURL(blob)
    val a = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
    a.href = url
    a.download = s"tourney_export_${t.wpId}_${t.startDate}.json"
    dom.document.body.appendChild(a)
    a.click()
    dom.document.body.removeChild(a)
    dom.URL.revokeObjectURL(url)
  }

  /**
   * Imports a tournament from a JSON string.
   * Reconstructs the fixed-size array buffers for competitions and stages
   * by restoring non-null elements to their original indices, padded with nulls.
   *
   * @param jsonString The JSON serialization of the Tourney case class.
   * @return A Future containing either an AppError or the slug of the newly created tournament.
   */
  def importTourney(jsonString: String): Future[Either[AppError, String]] = {
    try {
      val newTourney = read[Tourney](jsonString)
      
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

      TourneyDB.apiCreate(newTourney).flatMap {
        case Right(slug) =>
          // Update the DB references and register sync handlers
          TourneyDB.update(newTourney, doSync = false)
          TourneyDB.version = 1
          
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
            _ <- ajaxPost[Map[String,String], String]("/wp-json/tourney/v1/meta-data", List("postId" -> newTourney.wpId.toString), extraMetaPayload, host = Global.homeUrl)
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
