package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import shared.MainIds.*
import shared.model.*
import base.*
import dialogs.*

object MainMulti extends BasePage with JsWrapper:
  def name = PageNameTyp("MainMulti") 

  val BtnOption1: HtmlId = genId(name)
  val BtnOption2: HtmlId = genId(name)
  val BtnSearch:  HtmlId = genId(name)
  
  def render(param: String = ""): Boolean = 
    setMain(cviews.pages.html.MainMulti())
    true

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnOption1` => loadPage(NewTourney.name, "")
      case `BtnOption2` => 
        DlgCompetition.show(isOption2 = true).map {
          case Right(res) => 
            // 1. Create and save Dummy Tourney
            val dummyTourney = Tourney(
              id = 0,
              name = s"Quick: ${res.competition.name}",
              organizer = Global.user.map(_.org).getOrElse("System"),
              startDate = res.competition.startDate.replace("-", "").toInt,
              endDate = res.competition.startDate.replace("-", "").toInt,
              ident = "COMPETITION",
              typ = res.tourneyTyp.getOrElse(TourneyTyp.Unknown),
              version = 1
            )
            services.TourneyDB.update(dummyTourney)
            
            // 2. Add Competition to DB
            services.CompetitionDB.add(
              res.competition.name, 
              res.competition.typ, 
              res.competition.startDate.replace("-", "")
            ) match {
              case Right(c) => 
                Global.currentSelection = Selection(Some(dummyTourney), Some(c))
                comps.ContextHeader.render()
                loadPage(PageNameTyp("InfoCompetition"), "")
              case Left(err) => 
                error(s"Failed to create quick competition: ${err.msgCode}")
            }
          case Left(_) => debug("Option 2 cancelled")
        }
      case `BtnSearch` => loadPage(PageNameTyp("ViewOrganizer"), "")
      case _ => debug(s"MainMulti handleEvent: ${elem.id}")
