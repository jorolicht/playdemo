package comps

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.MainIds.*
import shared.model.*

object ContextHeader extends BaseComp with JsWrapper:
  def name = PageNameTyp("ContextHeader")

  val AdminExportId: HtmlId = genId(name)
  val AdminImportId: HtmlId = genId(name)

  def render(param: String = ""): Boolean = 
    val comps = services.CompetitionDB.competitions.toSeq.filter(c => c != null && !c.deleted)
    val stages = Global.currentSelection.competition.map { c =>
      services.StageDB.stages.toSeq.filter(s => s != null && s.coId == c.id && !s.deleted)
    }.getOrElse(Seq.empty)
    
    setHtml(gE(ContextHeaderId), cviews.comps.html.ContextHeader(Global.currentSelection, comps, stages))

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
                pages.loadPage(PageNameTyp("TourneyInfo"), "")
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

  def hide(): Unit =
    setHtml(gE(ContextHeaderId), "")

  override def handleEvent(elem: HTMLElement, event: dom.Event): Unit = 
    HtmlId(elem.id) match
      case `AdminExportId` => services.AdminManager.exportCurrentTourney()
      case `AdminImportId` =>
        val fileInput = dom.document.getElementById("adminImportFile").asInstanceOf[dom.html.Input]
        if (fileInput != null) {
          fileInput.value = ""
          fileInput.click()
        }
      case _ =>
        debug(s"ContextHeader handleEvent: ${elem.id}")
