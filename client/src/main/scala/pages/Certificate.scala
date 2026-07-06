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

  def render(param: String = ""): Boolean = 
    comps.ContextHeader.render()
    
    currentParam = param
    val tourneyOpt = Global.currentSelection.tourney

    tourneyOpt match {
      case Some(tourney) if tourney.certTemplate.nonEmpty =>
        if ((templateHtml.isEmpty || loadedTemplateName != tourney.certTemplate) && !isLoading) {
          fetchTemplateContent(tourney.certTemplate, param)
          setMain(cviews.pages.html.Certificate(param, None, true))
        } else {
          // Perform replacements on the templateHtml if defined
          val substitutedHtml = templateHtml.map { rawHtml =>
            val parts = param.split("\\|")
            val playerName = if (parts.length > 0) parts(0) else "Unbekannt"
            val compName   = if (parts.length > 1) parts(1) else "Wettbewerb"
            val rank       = if (parts.length > 2) parts(2) else "X"
            
            replaceKeywords(rawHtml, tourney.name, compName, playerName, rank)
          }
          setMain(cviews.pages.html.Certificate(param, substitutedHtml, false))
        }
      case _ =>
        // Fallback: no template configured
        setMain(cviews.pages.html.Certificate(param, None, false))
    }
    true

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
