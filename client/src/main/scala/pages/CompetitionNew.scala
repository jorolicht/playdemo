package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.{ MouseEvent, Event }
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import base.*
import shared.MainIds.*
import shared.model.*

object CompetitionNew extends BasePage with JsWrapper with services.ComWrapper:
  def name = PageNameTyp("CompetitionNew")
  
  val CompNameId:     HtmlId = genId(name)
  val CompTypId:      HtmlId = genId(name)
  val CompCategoryId: HtmlId = genId(name)
  val StartDateId:    HtmlId = genId(name)
  val TtrFromId:      HtmlId = genId(name)
  val TtrToId:        HtmlId = genId(name)
  val BtnSave:        HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    setMain(cviews.pages.html.CompetitionNew())
    comps.ContextHeader.render()
    
    // Initial load: Set current time
    val today = new js.Date()
    val datePart = today.toISOString().split("T")(0)
    val timePart = today.toTimeString().split(" ")(0).take(5) // HH:mm
    
    // datetime-local expects yyyy-MM-ddTHH:mm
    setInput(gE(StartDateId), s"${datePart}T$timePart")
    
    true

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnSave` => 
        doSave()
      case _ => debug(s"CompetitionNew handleEvent: ${elem.id}")

  private def doSave(): Unit =
    val nameStr = getInput(gE(CompNameId))
    
    if (nameStr.length < 3) {
      dom.window.alert("Bitte geben Sie einen gültigen Namen ein.")
    } else {
      val typ = CompTyp.fromString(getInput(gE(CompTypId)))
      val cat = CompCategory.valueOf(getInput(gE(CompCategoryId)))
      
      // Convert yyyy-MM-ddTHH:mm to yyyy-MM-dd HH:mm:ss
      val startRaw = getInput(gE(StartDateId))
      val startFormatted = startRaw.replace("T", " ") + ":00"
      
      val ttrFrom = try Some(getInput(gE(TtrFromId)).toInt) catch { case _:Exception => None }
      val ttrTo   = try Some(getInput(gE(TtrToId)).toInt)   catch { case _:Exception => None }
      
      // LOGIC: Create SIMPLE dummy tournament
      val dateInt = startFormatted.take(10).replace("-", "").toIntOption.getOrElse(0)
      
      val dummy = Tourney(
        id = 0,
        name = nameStr,
        organizer = Global.user.map(_.org).getOrElse("System"),
        startDate = dateInt,
        endDate = dateInt,
        ident = "SIMPLE",
        category = cat
      )
      
      // 1. Create dummy tournament on server
      services.TourneyDB.apiCreate(dummy).map {
        case Right(slug) =>
          // 2. Add the competition
          services.TourneyDB.tourney.addCompetition(nameStr, typ, cat, startFormatted) match {
            case Right(newComp) =>
              Global.currentSelection = Selection(Some(services.TourneyDB.tourney), Some(newComp))
              comps.ContextHeader.render()
              loadPage(PageNameTyp("CompetitionInfo"), "")
            case Left(err) =>
              dom.window.alert(s"Fehler beim Erstellen des Wettbewerbs: ${err.msgCode}")
          }
        case Left(err) =>
          dom.window.alert(s"Fehler beim Erstellen des Turniers auf dem Server: ${err.msgCode}")
      }
    }
