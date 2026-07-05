package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.MainIds.*
import shared.model.*
import dialogs.*
import shared.basic.Pickle.*

object TourneyAdmin extends BasePage with JsWrapper with services.ComWrapper:
  def name = PageNameTyp("TourneyAdmin")

  val BtnExportId:      HtmlId = genId(name)
  val BtnImportId:      HtmlId = genId(name)
  val BtnClickTTId:     HtmlId = genId(name)
  val RadioCertId:      HtmlId = genId(name)

  case class WpContent(rendered: String) derives ReadWriter
  case class WpTitle(rendered: String) derives ReadWriter
  case class WpPage(id: Int, parent: Int, title: WpTitle, content: WpContent) derives ReadWriter

  var templates: Seq[WpPage] = Seq.empty
  var isLoadingTemplates = false
  var templatesLoaded = false

  private var activeTab = "IMPEXP" // Tabs: "IMPEXP" (Import/Export), "CTT" (ClickTT), "CERT" (Urkunden Konfiguration)

  def render(param: String = ""): Boolean =
    Global.currentSelection.tourney match
      case Some(tourney) =>
        if (param.nonEmpty && List("IMPEXP", "CTT", "CERT").contains(param.toUpperCase)) {
          activeTab = param.toUpperCase
        }

        // Fetch templates if we select "CERT" and haven't loaded them yet
        if (activeTab == "CERT" && !templatesLoaded && !isLoadingTemplates) {
          fetchTemplates()
        }

        comps.ContextHeader.render()
        setMain(cviews.pages.html.TourneyAdmin(tourney, activeTab, templates, isLoadingTemplates))

        // Wire change listener for adminImportFile
        val fileInput = dom.document.getElementById("adminImportFile").asInstanceOf[dom.html.Input]
        if (fileInput != null) {
          fileInput.addEventListener("change", (e: dom.Event) => {
            if (fileInput.files.length > 0) {
              val file = fileInput.files(0)
              val reader = new dom.FileReader()
              reader.onload = (e: dom.Event) => {
                val jsonString = reader.result.asInstanceOf[String]
                services.AdminManager.importTourney(jsonString).map {
                  case Right(slug) =>
                    dom.window.alert("Import erfolgreich! Das Turnier wird nun geladen.")
                    fileInput.value = "" // reset
                    loadPage(TourneyInfo.name, "")
                  case Left(err) =>
                    dom.window.alert(s"Fehler beim Import: ${err.msgCode}")
                    fileInput.value = "" // reset
                }
              }
              reader.readAsText(file)
            }
          })
        }
        true
      case None =>
        debug("TourneyAdmin: No tournament selected, redirecting to Main Search")
        loadPage(MainSearch.name, "")
        false

  private def fetchTemplates(): Unit =
    isLoadingTemplates = true
    ajaxGet[Seq[WpPage]]("/wp-json/wp/v2/pages?per_page=100", List(), host = Global.homeUrl).map {
      case Right(pages) =>
        val parentOpt = pages.find(_.title.rendered.trim.equalsIgnoreCase("templates"))
        parentOpt match {
          case Some(parentPage) =>
            templates = pages.filter(p => p.parent == parentPage.id && p.title.rendered.trim.toUpperCase.startsWith("TT"))
          case None =>
            debug("Parent page 'Templates' not found in pages list")
            // Fallback: search for any page starting with "TT" if the Templates folder wasn't found
            templates = pages.filter(_.title.rendered.trim.toUpperCase.startsWith("TT"))
        }
        isLoadingTemplates = false
        templatesLoaded = true
        render()
      case Left(err) =>
        debug(s"Failed to fetch pages: ${err.msgCode}")
        isLoadingTemplates = false
        templatesLoaded = true
        render()
    }.recover {
      case ex =>
        debug(s"Failed to fetch pages: ${ex.getMessage}")
        isLoadingTemplates = false
        templatesLoaded = true
        render()
    }

  private def selectTemplate(templateName: String): Unit =
    Global.currentSelection.tourney.foreach { t =>
      t.certTemplate = templateName
      services.TourneyDB.update(t)
      dom.window.alert(s"Template '$templateName' erfolgreich gespeichert!")
      render()
    }

  override def handleEvent(elem: HTMLElement, event: Event): Unit =
    HtmlId(elem.id) match
      case `BtnExportId` =>
        services.AdminManager.exportCurrentTourney()

      case `BtnImportId` =>
        val fileInput = dom.document.getElementById("adminImportFile").asInstanceOf[dom.html.Input]
        if (fileInput != null) {
          fileInput.value = ""
          fileInput.click()
        }

      case `BtnClickTTId` =>
        dialogs.DlgClickTT.show().map {
          case Right(t) =>
            Global.currentSelection = Selection(Some(t))
            comps.ContextHeader.render()
            loadPage(TourneyInfo.name, "")
          case Left(err) =>
            debug(s"ClickTT Import cancelled or failed: ${err.msgCode}")
        }

      case id if id.id.startsWith(RadioCertId.id) =>
        val suffix = elem.id.substring(RadioCertId.id.length + 1)
        if (suffix.startsWith("SETTMPL-")) {
          val templateName = suffix.substring("SETTMPL-".length)
          selectTemplate(templateName)
        } else {
          val stageId = StageId(suffix.toInt)
          val stages = services.TourneyDB.tourney.stages
          val targetStage = stages(stageId.value - 1)
          if (targetStage != null) {
            val isAlreadyChecked = targetStage.certificate
            val newCertVal = !isAlreadyChecked
            handleCertificateChange(stageId, newCertVal)
          }
        }

      case _ =>
        debug(s"TourneyAdmin handleEvent: ${elem.id}")

  private def handleCertificateChange(stageId: StageId, value: Boolean): Unit =
    val stages = services.TourneyDB.tourney.stages
    val targetStage = stages(stageId.value - 1)
    if (targetStage != null) {
      stages.zipWithIndex.foreach { case (s, idx) =>
        if (s != null && s.coId == targetStage.coId && !s.deleted) {
          val newVal = if (s.id == stageId) value else false
          if (s.certificate != newVal) {
            s.certificate = newVal
            services.TourneyDB.tourney.updateStage(s) match {
              case Right(updatedStage) =>
                if (Global.currentSelection.stage.exists(_.id == s.id)) {
                  Global.currentSelection = Global.currentSelection.copy(stage = Some(updatedStage))
                }
              case Left(err) =>
                error(s"Failed to update stage certificate: ${err.msgCode}")
            }
          }
        }
      }
      render()
    }
