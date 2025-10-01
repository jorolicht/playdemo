package controllers

import javax.inject._
import play.api.mvc._
import shared.model.TournBase
import upickle.default._

import scala.concurrent.ExecutionContext

import scala.concurrent.Future

@Singleton
class TourneyController @Inject()(val controllerComponents: ControllerComponents)(implicit ec: ExecutionContext) extends BaseController {

  def postTourney = Action.async(parse.text) { request =>
    val body: String = request.body
    Future {
      try {
        val tourney = read[TournBase](body)
        // For now, just log the tourney
        println(s"Received tourney: $tourney")
        Ok("Tourney received")
      } catch {
        case e: Exception =>
          BadRequest("Invalid TournBase format: " + e.getMessage)
      }
    }
  }
}
