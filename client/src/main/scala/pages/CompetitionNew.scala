package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.{ MouseEvent, Event }
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import base.*
import base.Messages.gM
import shared.MainIds.*
import shared.model.*

/**
 * Page component representing the quick-start competition creation wizard (Schnellstart).
 *
 * Allows users to quickly create a standalone competition without manually configuring a full tournament.
 * A SIMPLE wrapper tournament is automatically created and initialized in the background.
 */
object CompetitionNew extends BasePage with JsWrapper with services.ComWrapper:
  def name = PageNameTyp("CompetitionNew")
  
  val CompNameId:     HtmlId = genId(name)
  val CompTypId:      HtmlId = genId(name)
  val CompCategoryId: HtmlId = genId(name)
  val StartDateId:    HtmlId = genId(name)
  val TtrFromId:      HtmlId = genId(name)
  val TtrToId:        HtmlId = genId(name)
  val BtnSave:        HtmlId = genId(name)

  /**
   * Renders the CompetitionNew page view and initializes default values.
   *
   * @param param Optional routing parameter.
   * @return True upon successful render.
   */
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

  /**
   * Dispatches DOM events to appropriate action handlers.
   *
   * @param elem HTML element triggering the event.
   * @param event Triggering DOM event.
   */
  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnSave` => 
        doSave()
      case _ => debug(s"CompetitionNew handleEvent: ${elem.id}")

  /**
   * Validates form input, creates the SIMPLE dummy tournament, adds the new competition,
   * synchronizes state with browser storage or server, and navigates to the CompetitionInfo view.
   */
  private def doSave(): Unit =
    val nameStr = getInput(gE(CompNameId)).trim
    
    if (nameStr.length < 3) {
      dom.window.alert("Bitte geben Sie einen gültigen Namen ein.")
    } else {
      val typ = CompTyp.fromString(getInput(gE(CompTypId)))
      val cat = CompCategory.valueOf(getInput(gE(CompCategoryId)))
      
      // Convert yyyy-MM-ddTHH:mm to yyyy-MM-dd HH:mm:ss
      val startRaw = getInput(gE(StartDateId))
      val startFormatted = if (startRaw.contains("T")) startRaw.replace("T", " ") + ":00" else startRaw
      
      val ttrFrom = try Some(getInput(gE(TtrFromId)).toInt) catch { case _:Exception => None }
      val ttrTo   = try Some(getInput(gE(TtrToId)).toInt)   catch { case _:Exception => None }
      
      // LOGIC: Create SIMPLE dummy tournament
      val dateInt = startFormatted.take(10).replace("-", "").toIntOption.getOrElse(20260101)
      
      val dummy = Tourney(
        wpId = 0,
        name = nameStr,
        organizer = Global.user.map(u => if (u.org.nonEmpty) u.org else u.username).getOrElse("System"),
        startDate = dateInt,
        endDate = dateInt,
        ident = "SIMPLE",
        category = cat
      )
      
      // 1. Create dummy tournament on server or local storage
      services.TourneyDB.apiCreate(dummy).flatMap {
        case Right(slug) =>
          services.TourneyDB.update(dummy, doSync = false)
          // 2. Add the competition
          services.TourneyDB.tourney.addCompetition(
            name = nameStr, 
            typ = typ, 
            category = cat, 
            startDate = startFormatted, 
            lowLevel = ttrFrom, 
            upperLevel = ttrTo
          ) match {
            case Right(newComp) =>
              Global.currentSelection = Selection(Some(services.TourneyDB.tourney), Some(newComp))
              comps.ContextHeader.render()
              services.TourneyDB.sync().map { _ =>
                loadPage(PageNameTyp("CompetitionInfo"), "")
              }
            case Left(err) =>
              dom.window.alert(s"Fehler beim Erstellen des Wettbewerbs: ${err.msgCode}")
              Future.successful(())
          }
        case Left(err) =>
          val errMsg = if (err.is("tourney_already_exists")) {
            val inputEl = gE(CompNameId).asInstanceOf[dom.html.Input]
            inputEl.classList.add("is-invalid")
            inputEl.oninput = { (_: dom.Event) =>
              inputEl.classList.remove("is-invalid")
            }
            gM("error.competition_already_exists")
          } else {
            s"Fehler beim Erstellen des Wettbewerbs: ${err.msgCode}"
          }
          dom.window.alert(errMsg)
          Future.successful(())
      }
    }
