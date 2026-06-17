package services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import org.scalajs.dom
import shared.basic.Pickle.*
import shared.basic.AppError
import base.{Global, Logging}
import shared.model.*

object AdminManager extends ComWrapper {

  case class ExportData(
    tourney: Tourney,
    clubs: Seq[Club],
    players: Seq[Player],
    competitions: Seq[Competition],
    stages: Seq[Stage],
    extraMeta: Map[String, String]
  ) derives ReadWriter

  def exportCurrentTourney(): Unit = {
    val t = TourneyDB.tourney
    if (t.wpId == 0) {
      dom.window.alert("Es ist kein Turnier geladen, das exportiert werden könnte.")
      return
    }

    val exportData = ExportData(
      tourney = t,
      clubs = t.clubs.toSeq,
      players = t.players.toSeq,
      competitions = t.competitions.filter(_ != null).toSeq,
      stages = t.stages.filter(_ != null).toSeq,
      extraMeta = Map(
        "startDate" -> t.startDate.toString,
        "endDate" -> t.endDate.toString,
        "ident" -> t.ident,
        "category" -> t.category.toString,
        "organizer" -> t.organizer
      )
    )

    val jsonString = write(exportData, indent = 2)
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

  def importTourney(jsonString: String): Future[Either[AppError, String]] = {
    try {
      val importedData = read[ExportData](jsonString)
      val newTourney = importedData.tourney
      
      // Reset IDs to create a new tournament
      newTourney.wpId = 0
      newTourney.version = 1
      
      Logging.info(s"Starte Import für Turnier: ${newTourney.name}")
      
      TourneyDB.apiCreate(newTourney).flatMap {
        case Right(slug) =>
          // Update the DB references
          TourneyDB.tourney = newTourney
          TourneyDB.version = 1
          
          TourneyDB.tourney.clubs.clear()
          TourneyDB.tourney.clubs ++= importedData.clubs
          
          TourneyDB.tourney.players.clear()
          TourneyDB.tourney.players ++= importedData.players
          
          TourneyDB.tourney.competitions.clear()
          for (i <- 0 until 64) TourneyDB.tourney.competitions += null
          importedData.competitions.foreach { c =>
            val i = c.id.value - 1
            if (i >= 0 && i < 64) TourneyDB.tourney.competitions(i) = c
          }
          
          TourneyDB.tourney.stages.clear()
          for (i <- 0 until 128) TourneyDB.tourney.stages += null
          importedData.stages.foreach { r =>
            val i = r.id.value - 1
            if (i >= 0 && i < 128) TourneyDB.tourney.stages(i) = r
          }
          
          // Meta-Data Update
          val extraMetaPayload = Map(
              "startDate" -> importedData.extraMeta.getOrElse("startDate", newTourney.startDate.toString),
              "endDate" -> importedData.extraMeta.getOrElse("endDate", newTourney.endDate.toString),
              "ident" -> importedData.extraMeta.getOrElse("ident", newTourney.ident),
              "category" -> importedData.extraMeta.getOrElse("category", newTourney.category.toString),
              "organizer" -> importedData.extraMeta.getOrElse("organizer", newTourney.organizer)
          )

          // Trigger Syncs
          for {
            _ <- TourneyDB.sync()
            _ <- ClubDB.sync(TourneyDB.tourney.clubs.toSeq)
            _ <- PlayerDB.sync(TourneyDB.tourney.players.toSeq)
            _ <- CompetitionDB.sync(importedData.competitions)
            _ <- StageDB.sync(importedData.stages)
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
