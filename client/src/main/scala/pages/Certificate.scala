package pages

import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.model.*
import shared.basic.Pickle.*

object Certificate extends BasePage with JsWrapper with services.ComWrapper:
  def name = PageNameTyp("Certificate")

  case class WpContent(rendered: String) derives ReadWriter
  case class WpTitle(rendered: String) derives ReadWriter
  case class WpPage(id: Int, parent: Int, title: WpTitle, content: WpContent) derives ReadWriter

  var templateHtml: Option[String] = None
  var loadedTemplateName: String = ""
  var isLoading = false
  var currentParam = ""

  // State variables for edited certificate content
  var currentTourneyName = ""
  var currentCompName = ""
  var currentPlayerName = ""
  var currentRank = ""

  def render(param: String = ""): Boolean = 
    comps.ContextHeader.render()
    
    val tourneyOpt = Global.currentSelection.tourney

    if (currentParam != param) {
      currentParam = param
      val parts = param.split("\\|")
      currentPlayerName = if (parts.length > 0) parts(0) else "Unbekannt"
      currentCompName   = if (parts.length > 1) parts(1) else "Wettbewerb"
      currentRank       = if (parts.length > 2) parts(2) else "X"
      currentTourneyName = tourneyOpt.map(_.name).getOrElse("")
    }

    tourneyOpt match {
      case Some(tourney) if tourney.certTemplate.nonEmpty =>
        if ((templateHtml.isEmpty || loadedTemplateName != tourney.certTemplate) && !isLoading) {
          fetchTemplateContent(tourney.certTemplate, param)
          setMain(cviews.pages.html.Certificate(param, currentTourneyName, currentCompName, currentPlayerName, currentRank, None, true))
        } else {
          // Perform replacements on the templateHtml if defined
          val substitutedHtml = templateHtml.map { rawHtml =>
            replaceKeywords(rawHtml, currentTourneyName, currentCompName, currentPlayerName, currentRank)
          }
          setMain(cviews.pages.html.Certificate(param, currentTourneyName, currentCompName, currentPlayerName, currentRank, substitutedHtml, false))
          wireInputEvents()
        }
      case _ =>
        // Fallback: no template configured
        setMain(cviews.pages.html.Certificate(param, currentTourneyName, currentCompName, currentPlayerName, currentRank, None, false))
        wireInputEvents()
    }
    true

  private def wireInputEvents(): Unit =
    wireInput("CertEdit_Tourney", (v) => { currentTourneyName = v; updatePreview() })
    wireInput("CertEdit_Comp", (v) => { currentCompName = v; updatePreview() })
    wireInput("CertEdit_Name", (v) => { currentPlayerName = v; updatePreview() })
    wireInput("CertEdit_Rank", (v) => { currentRank = v; updatePreview() })

  private def wireInput(elementId: String, onChange: String => Unit): Unit =
    val inputElem = dom.document.getElementById(elementId).asInstanceOf[dom.html.Input]
    if (inputElem != null) {
      inputElem.oninput = (e: dom.Event) => {
        onChange(inputElem.value)
      }
    }

  private def updatePreview(): Unit =
    val previewWrapper = dom.document.getElementById("CertificatePreviewWrapper").asInstanceOf[dom.raw.HTMLElement]
    if (previewWrapper != null) {
      val substitutedHtml = templateHtml.map { rawHtml =>
        replaceKeywords(rawHtml, currentTourneyName, currentCompName, currentPlayerName, currentRank)
      }
      setHtml(previewWrapper, cviews.pages.html.CertificatePreview(currentParam, currentPlayerName, currentCompName, currentRank, substitutedHtml, false).toString)
    }

  private def replaceKeywords(rawHtml: String, tourneyName: String, compName: String, playerName: String, rank: String): String =
    val tNameRepl = scala.util.matching.Regex.quoteReplacement(tourneyName)
    val cNameRepl = scala.util.matching.Regex.quoteReplacement(compName)
    val pNameRepl = scala.util.matching.Regex.quoteReplacement(playerName)
    val rNameRepl = scala.util.matching.Regex.quoteReplacement(rank)

    val html1 = "(?i)#(Turnier|Tourney)".r.replaceAllIn(rawHtml, tNameRepl)
    val html2 = "(?i)#(Wettbewerb|Competition)".r.replaceAllIn(html1, cNameRepl)
    val html3 = "(?i)#Name".r.replaceAllIn(html2, pNameRepl)
    "(?i)#(Platz|Place)".r.replaceAllIn(html3, rNameRepl)

  private def fetchTemplateContent(templateName: String, param: String): Unit =
    isLoading = true
    ajaxGet[Seq[WpPage]]("/wp-json/wp/v2/pages?per_page=100", List(), host = Global.homeUrl).map {
      case Right(pages) =>
        val matchOpt = pages.find(_.title.rendered.trim.equalsIgnoreCase(templateName))
        matchOpt match {
          case Some(page) =>
            templateHtml = Some(page.content.rendered)
            loadedTemplateName = templateName
          case None =>
            debug(s"Configured template page '$templateName' not found")
            templateHtml = Some("") // Trigger fallback
            loadedTemplateName = templateName
        }
        isLoading = false
        render(param)
      case Left(err) =>
        debug(s"Failed to fetch templates: ${err.msgCode}")
        templateHtml = Some("") // Trigger fallback
        loadedTemplateName = templateName
        isLoading = false
        render(param)
    }.recover {
      case ex =>
        debug(s"Failed to fetch templates: ${ex.getMessage}")
        templateHtml = Some("") // Trigger fallback
        loadedTemplateName = templateName
        isLoading = false
        render(param)
    }
