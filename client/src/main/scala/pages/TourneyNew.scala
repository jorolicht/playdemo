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

/**
 * Page for creating a new tournament or editing an existing tournament.
 */
object TourneyNew extends BasePage with JsWrapper:
  def name = PageNameTyp("TourneyNew")

  val BtnSave: HtmlId    = genId(name)
  val BtnLoadCtt: HtmlId = genId(name)

  private var isEditMode: Boolean = false

  def render(param: String = ""): Boolean = 
    val currentT = Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)
    isEditMode = param == "edit" && currentT.wpId > 0

    setMain(cviews.pages.html.TourneyNew(isEditMode))
    
    // Set default dates or fill existing values
    val today = new js.Date()
    val todayStr = today.toISOString().split("T")(0)

    def intDateToStr(dateInt: Int): String =
      if (dateInt <= 0) todayStr
      else {
        val y = dateInt / 10000
        val m = (dateInt / 100) % 100
        val d = dateInt % 100
        f"$y%04d-$m%02d-$d%02d"
      }
    
    val startInput = dom.document.getElementById("tn-start").asInstanceOf[dom.html.Input]
    val endInput = dom.document.getElementById("tn-end").asInstanceOf[dom.html.Input]
    val nameInput = dom.document.getElementById("tn-name").asInstanceOf[dom.html.Input]
    val identInput = dom.document.getElementById("tn-ident").asInstanceOf[dom.html.Input]
    val orgInput = dom.document.getElementById("tn-organizer").asInstanceOf[dom.html.Input]
    val typSelect = dom.document.getElementById("tn-typ").asInstanceOf[dom.html.Select]

    val contactFname = dom.document.getElementById("tn-contact-fname").asInstanceOf[dom.html.Input]
    val contactLname = dom.document.getElementById("tn-contact-lname").asInstanceOf[dom.html.Input]
    val contactPhone = dom.document.getElementById("tn-contact-phone").asInstanceOf[dom.html.Input]
    val contactEmail = dom.document.getElementById("tn-contact-email").asInstanceOf[dom.html.Input]

    val addrDesc = dom.document.getElementById("tn-addr-desc").asInstanceOf[dom.html.Input]
    val addrStreet = dom.document.getElementById("tn-addr-street").asInstanceOf[dom.html.Input]
    val addrZip = dom.document.getElementById("tn-addr-zip").asInstanceOf[dom.html.Input]
    val addrCity = dom.document.getElementById("tn-addr-city").asInstanceOf[dom.html.Input]
    val addrCountry = dom.document.getElementById("tn-addr-country").asInstanceOf[dom.html.Input]

    if (isEditMode) {
      if (nameInput != null) nameInput.value = currentT.name
      if (identInput != null) identInput.value = currentT.ident
      if (orgInput != null) orgInput.value = currentT.organizer
      if (typSelect != null) typSelect.value = if (currentT.category == CompCategory.TT) "TT" else "UNKNOWN"
      if (startInput != null) startInput.value = intDateToStr(currentT.startDate)
      if (endInput != null) endInput.value = intDateToStr(currentT.endDate)

      currentT.contact.foreach { c =>
        if (contactFname != null) contactFname.value = c.firstname
        if (contactLname != null) contactLname.value = c.lastname
        if (contactPhone != null) contactPhone.value = c.phone
        if (contactEmail != null) contactEmail.value = c.email
      }

      currentT.address.foreach { a =>
        if (addrDesc != null) addrDesc.value = a.description
        if (addrStreet != null) addrStreet.value = a.street
        if (addrZip != null) addrZip.value = a.zip
        if (addrCity != null) addrCity.value = a.city
        if (addrCountry != null) addrCountry.value = a.country
      }
    } else {
      if (startInput != null) startInput.value = todayStr
      if (endInput != null) endInput.value = todayStr
    }
    
    true

  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnSave` => 
        saveTourney()
      case `BtnLoadCtt` =>
        dialogs.DlgClickTT.show().map {
          case Right(t) => 
            Global.currentSelection = Selection(Some(t))
            comps.ContextHeader.render()
            loadPage(pages.TourneyInfo.name, "")
          case Left(err) => 
            debug(s"ClickTT Import cancelled or failed: $err")
        }
      case _ => 
        debug(s"Unhandled event in TourneyNew: ${elem.id}")

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

    val todayInt = new js.Date().toISOString().split("T")(0).replace("-", "").toInt
    
    if (tName.length < 3) {
      showError("Der Turniername muss mindestens 3 Zeichen lang sein.")
    } else if (!isEditMode && tStart < todayInt) {
      showError("Das Startdatum darf nicht in der Vergangenheit liegen.")
    } else if (tEnd < tStart) {
      showError("Das Enddatum muss gleich oder nach dem Startdatum liegen.")
    } else {
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

      if (isEditMode) {
        val existing = Global.currentSelection.tourney.getOrElse(services.TourneyDB.tourney)
        existing.name = tName
        existing.organizer = tOrganizer
        existing.startDate = tStart
        existing.endDate = tEnd
        existing.ident = tIdent
        existing.category = if (tTyp == "TT") CompCategory.TT else CompCategory.UNKNOWN
        existing.contact = Some(contact)
        existing.address = Some(address)

        Logging.info(s"Aktualisiere Turnier: ${existing.name}")
        feedback.innerHTML = s"""<div class='alert alert-info mt-3'>Turnieränderungen werden gespeichert...</div>"""

        services.TourneyDB.update(existing)
        services.TourneyDB.sync().map { _ =>
          Global.currentSelection = Selection(Some(existing))
          comps.ContextHeader.render()
          feedback.innerHTML = s"""<div class='alert alert-success mt-3'>Turnier '${existing.name}' wurde erfolgreich aktualisiert.</div>"""
          dom.window.setTimeout(() => {
            pages.loadPage(pages.TourneyInfo.name, "")
          }, 800)
        }
      } else {
        val tourney = Tourney(
          wpId = 0,         // New entry, DB will assign ID
          name = tName,
          organizer = tOrganizer,
          startDate = tStart,
          endDate = tEnd,
          ident = tIdent,
          category = if (tTyp == "TT") CompCategory.TT else CompCategory.UNKNOWN,
          contact = Some(contact),
          address = Some(address),
          version = 0
        )

        Logging.info(s"Speichere neues Turnier: ${tourney}")
        feedback.innerHTML = s"""<div class='alert alert-info mt-3'>Turnier wird auf dem Server erstellt...</div>"""

        services.TourneyDB.apiCreate(tourney).map {
          case Right(slug) =>
            Global.pageId = tourney.wpId
            services.TourneyDB.update(tourney, doSync = false)
            Global.currentSelection = Selection(Some(tourney))
            comps.ContextHeader.render()

            feedback.innerHTML = s"""<div class='alert alert-success mt-3'>Turnier '${tourney.name}' wurde erfolgreich erstellt.</div>"""
            dom.window.setTimeout(() => {
              pages.loadPage(pages.TourneyInfo.name, "")
            }, 1000)
            
          case Left(err) =>
            val errMsg = if (err.is("tourney_already_exists")) {
              val nameInput = dom.document.getElementById("tn-name").asInstanceOf[dom.html.Input]
              if (nameInput != null) {
                nameInput.classList.add("is-invalid")
                nameInput.oninput = { (_: dom.Event) =>
                  nameInput.classList.remove("is-invalid")
                }
              }
              base.Messages.gM("error.tourney_already_exists")
            } else {
              s"Fehler beim Erstellen des Turniers: ${err.msgCode}"
            }
            showError(errMsg)
        }
      }
    }

  private def showError(msg: String): Unit =
    val feedback = dom.document.getElementById("validationFeedback").asInstanceOf[HTMLElement]
    if (feedback != null) {
      feedback.innerHTML = s"<div class='alert alert-danger mt-3'>$msg</div>"
      dom.window.scrollTo(0, feedback.offsetTop.toInt)
    }
