package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.scalajs.js
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.MainIds.*
import shared.model.*
import shared.basic.*

object NewTourney extends BasePage with JsWrapper:
  def name = PageNameTyp("NewTourney")

  val BtnSave: HtmlId    = genId(name)
  val BtnLoadCtt: HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    setMain(cviews.pages.html.NewTourney())
    
    // Set default dates (today)
    val today = new js.Date()
    val todayStr = today.toISOString().split("T")(0)
    
    val startInput = dom.document.getElementById("tn-start").asInstanceOf[dom.html.Input]
    val endInput = dom.document.getElementById("tn-end").asInstanceOf[dom.html.Input]
    
    if (startInput != null) startInput.value = todayStr
    if (endInput != null) endInput.value = todayStr
    
    true

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnSave` => 
        saveTourney()
      case `BtnLoadCtt` =>
        dialogs.DlgClickTT.show().map {
          case Right(t) => 
            // Data is already in DB services (mapped by ClickTTMapper)
            Global.currentSelection = Selection(Some(t))
            comps.ContextHeader.render()
            loadPage(pages.InfoTourney.name, "")
          case Left(err) => 
            debug(s"ClickTT Import cancelled or failed: $err")
        }
      case _ => 
        debug(s"Unhandled event in NewTourney: ${elem.id}")

  private def saveTourney(): Unit =
    val feedback = dom.document.getElementById("validationFeedback")
    
    def getValue(id: String) = dom.document.getElementById(id).asInstanceOf[dom.html.Input].value
    def getIntDate(id: String): Int = getValue(id).replace("-", "").toInt

    val tName = getValue("tn-name")
    val tOrganizer = getValue("tn-organizer")
    val tStart = getIntDate("tn-start")
    val tEnd = getIntDate("tn-end")
    val tIdent = getValue("tn-ident")
    val tTyp = dom.document.getElementById("tn-typ").asInstanceOf[dom.html.Select].value

    // Basic Validation
    val todayInt = new js.Date().toISOString().split("T")(0).replace("-", "").toInt
    
    if (tName.length < 3) {
      showError("Der Turniername muss mindestens 3 Zeichen lang sein.")
    } else if (tStart < todayInt) {
      showError("Das Startdatum darf nicht in der Vergangenheit liegen.")
    } else if (tEnd < tStart) {
      showError("Das Enddatum muss gleich oder nach dem Startdatum liegen.")
    } else {
      // Create objects
      val contact = Contact(
        lastname = getValue("tn-contact-lname"),
        firstname = getValue("tn-contact-fname"),
        phone = getValue("tn-contact-phone"),
        email = getValue("tn-contact-email")
      )

      val address = Address(
        description = getValue("tn-addr-desc"),
        country = getValue("tn-addr-country"),
        zip = getValue("tn-addr-zip"),
        city = getValue("tn-addr-city"),
        street = getValue("tn-addr-street")
      )

      val tourney = Tourney(
        name = tName,
        organizer = tOrganizer,
        startDate = tStart,
        endDate = tEnd,
        ident = tIdent,
        typ = if (tTyp == "TableTennis") TourneyTyp.TableTennis else TourneyTyp.Unknown,
        contact = Some(contact),
        address = Some(address),
        version = 0
      )

      Logging.info(s"Speichere neues Turnier: ${tourney}")
      
      // Update DB and Selection
      services.TourneyDB.update(tourney)
      Global.currentSelection = Selection(Some(tourney))
      comps.ContextHeader.render()

      feedback.innerHTML = s"""<div class='alert alert-success mt-3'>Turnier '${tourney.name}' wurde erstellt.</div>"""
      
      dom.window.setTimeout(() => {
        pages.loadPage(pages.InfoTourney.name, "")
      }, 1000)
    }

  private def showError(msg: String): Unit =
    val feedback = dom.document.getElementById("validationFeedback").asInstanceOf[HTMLElement]
    feedback.innerHTML = s"<div class='alert alert-danger mt-3'>$msg</div>"
    dom.window.scrollTo(0, feedback.offsetTop.toInt)
