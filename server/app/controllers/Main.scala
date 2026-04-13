package controllers

import javax.inject._
import shared.basic.Pickle.*
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.quoted.*
import play.api.mvc._
import play.api.Logging
import play.api.i18n.{ I18nSupport, Messages, Langs, Lang }

import repositories.UserRepository
import shared._
import shared.model._

@Singleton
class Main @Inject()(cc: ControllerComponents, userRepo: UserRepository)(implicit ec: ExecutionContext) 
  extends AbstractController(cc) with I18nSupport with Logging  {

  def home(name: String, param: String=""): Action[AnyContent] = Action { implicit request =>
    val debug   = request.getQueryString("debug").getOrElse("")
    val tourney = request.getQueryString("tourney").getOrElse("")
    logger.trace(s"home -> name=${name} param=${param} debug=${debug} tourney=${tourney}")
    Ok(views.html.main(name, param, debug, tourney) )
  }

}