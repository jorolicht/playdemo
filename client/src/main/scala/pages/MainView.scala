package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import shared.basic.Pickle.*
import services.*
import base.*
import dialogs.*

/**
 * MainView page component for rendering home view and loading static content pages or WP pages.
 */
object MainView extends BasePage with JsWrapper with ComWrapper:
  /** Name identifier for the page. */
  def name = PageNameTyp("MainView") 

  val BtnOption1: HtmlId = genId(name)
  val BtnOption2: HtmlId = genId(name)
  val BtnSearch:  HtmlId = genId(name)
  
  /** Models for standard WordPress REST API response parsing. */
  case class WpContent(rendered: String) derives ReadWriter
  case class WpTitle(rendered: String) derives ReadWriter
  case class WpPage(id: Int, title: WpTitle, content: WpContent, translations: Option[Map[String, Int]] = None) derives ReadWriter

  /**
   * Renders the page view or static content page specified by param.
   *
   * @param param Page slug, ID, or page identifier.
   * @return Boolean true on render.
   */
  def render(param: String = ""): Boolean = 
    if (param.isEmpty) {
      setMain(cviews.pages.MainView.html.Base())
    } else {
      // 1. Loading State
      setMain(cviews.pages.MainView.html.Loading(param))

      val knownStaticPages = Set("pricing", "preise", "funktionen", "functions", "gtc", "agb", "dataprotection", "privacy", "impressum", "imprint", "contact", "kontakt", "excel", "plugin")

      if (knownStaticPages.contains(param.toLowerCase)) {
        loadStaticPageFallback(param)
      } else {
        // 2. Fetch WordPress Page Content by ID or Slug
        param.toIntOption match {
          case Some(pageId) =>
            ajaxGet[WpPage](s"/wp-json/wp/v2/pages/$pageId", List(), host = Global.homeUrl).map {
              case Right(page) =>
                setMain(cviews.pages.MainView.html.PageContent(page.title.rendered, page.content.rendered))
              case Left(err) =>
                setMain(cviews.pages.MainView.html.PageNotFound(pageId.toString, err.msgCode))
            }
          case None =>
            ajaxGet[Seq[WpPage]](s"/wp-json/wp/v2/pages?slug=$param", List(), host = Global.homeUrl).map {
              case Right(pages) if pages.nonEmpty =>
                val page = pages.head
                val lang = Global.lang
                
                val translatedIdOpt = page.translations.flatMap(_.get(lang))
                
                translatedIdOpt match {
                  case Some(transId) if transId != page.id =>
                    ajaxGet[WpPage](s"/wp-json/wp/v2/pages/$transId", List(), host = Global.homeUrl).map {
                      case Right(transPage) =>
                        setMain(cviews.pages.MainView.html.PageContent(transPage.title.rendered, transPage.content.rendered))
                      case Left(_) =>
                        setMain(cviews.pages.MainView.html.PageContent(page.title.rendered, page.content.rendered))
                    }
                  case _ =>
                    setMain(cviews.pages.MainView.html.PageContent(page.title.rendered, page.content.rendered))
                }
                
              case _ =>
                loadStaticPageFallback(param)
            }
        }
      }
    }
    true

  /**
   * Fetches static HTML content files with cache-busting query parameter.
   *
   * @param param The page slug or identifier.
   */
  private def loadStaticPageFallback(param: String): Unit =
    val baseName = param match {
      case "functions" | "funktionen"           => "funktionen"
      case "agb" | "gtc"                        => "gtc"
      case "privacy" | "dataprotection"         => "privacy"
      case "impressum" | "imprint"              => "impressum"
      case "contact" | "kontakt"                => "contact"
      case "pricing" | "preise"                 => "pricing"
      case other                                => other
    }
    val langSuffix = if (Global.lang == "en") "_en" else ""
    val staticName = s"$baseName$langSuffix"

    val baseHome = if (Global.homeUrl != null && Global.homeUrl.nonEmpty) Global.homeUrl.stripSuffix("/") else ""
    val baseData = if (Global.dataUrl != null && Global.dataUrl.nonEmpty && Global.dataUrl.contains("/wp-content/plugins/")) {
      Global.dataUrl.replaceAll("data/?$", "")
    } else ""

    val candidateUrls = List(
      if (baseData.nonEmpty) s"${baseData}pages/$staticName.html" else "",
      s"$baseHome/wp-content/plugins/tourney/pages/$staticName.html",
      s"$baseHome/wp-content/plugins/playdemo/pages/$staticName.html",
      s"/wp-content/plugins/tourney/pages/$staticName.html",
      s"/wp-content/plugins/playdemo/pages/$staticName.html",
      s"./pages/$staticName.html",
      s"/pages/$staticName.html",
      if (baseData.nonEmpty) s"${baseData}pages/$baseName.html" else "",
      s"$baseHome/wp-content/plugins/tourney/pages/$baseName.html",
      s"/wp-content/plugins/tourney/pages/$baseName.html",
      s"./pages/$baseName.html"
    ).filter(_.nonEmpty).distinct

    def tryFetch(urls: List[String]): Unit = urls match {
      case Nil =>
        setMain(cviews.pages.MainView.html.PageNotFound(param, s"Keine Seite mit diesem Slug gefunden (404)."))
      case head :: tail =>
        val cacheBustUrl = if (head.contains("?")) s"$head&_t=${dom.window.performance.now().toLong}" else s"$head?_t=${dom.window.performance.now().toLong}"
        org.scalajs.dom.fetch(cacheBustUrl).`then`[Unit] { resp =>
          if (resp.ok) {
            resp.text().`then`[Unit] { text =>
              val pageTitle = baseName match {
                case "funktionen"                   => if (Global.lang == "en") "Features & Functions" else "Funktionen & Features"
                case "gtc"                          => if (Global.lang == "en") "Terms and Conditions (AGB)" else "Allgemeine Geschäftsbedingungen"
                case "excel"                        => if (Global.lang == "en") "Excel Downloads & Software" else "Excel-Downloads & Software"
                case "plugin"                       => if (Global.lang == "en") "WordPress Plugin (Coming Soon)" else "WordPress Plugin (Demnächst verfügbar)"
                case "privacy" | "dataprotection"   => if (Global.lang == "en") "Privacy Policy" else "Datenschutzerklärung"
                case "impressum" | "imprint"        => if (Global.lang == "en") "Imprint" else "Impressum"
                case "contact" | "kontakt"          => if (Global.lang == "en") "Contact" else "Kontakt"
                case "pricing" | "preise"           => if (Global.lang == "en") "Support & Pricing" else "Support & Kosten"
                case _                              => staticName.capitalize
              }
              setMain(cviews.pages.MainView.html.PageContent(pageTitle, text))
              ()
            }
          } else {
            tryFetch(tail)
          }
        }.`catch` { _ =>
          tryFetch(tail)
        }
    }

    tryFetch(candidateUrls)

  /**
   * Event handler for button clicks and interactions on MainView.
   *
   * @param elem Target HTML element.
   * @param event DOM event trigger.
   */
  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnOption1` => 
        if (Global.user.isDefined) {
          loadPage(TourneyNew.name, "")
        } else {
          services.DemoManager.promptDemoMode("template-full", () => loadPage(TourneyNew.name, ""), () => loadPage(MainView.name, ""))
        }
      case `BtnOption2` => 
        if (Global.user.isDefined) {
          comps.Navbar.doQuickStart()
        } else {
          services.DemoManager.promptDemoMode("template-single", () => comps.Navbar.doQuickStart(), () => loadPage(MainView.name, ""))
        }
      case `BtnSearch` => loadPage(MainSearch.name, "")
      case _ => debug(s"MainView handleEvent: ${elem.id}")
