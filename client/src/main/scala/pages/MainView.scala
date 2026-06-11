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

object MainView extends BasePage with JsWrapper with ComWrapper:
  def name = PageNameTyp("MainView") 

  val BtnOption1: HtmlId = genId(name)
  val BtnOption2: HtmlId = genId(name)
  val BtnSearch:  HtmlId = genId(name)
  
  // Models for standard WordPress API
  case class WpContent(rendered: String) derives ReadWriter
  case class WpTitle(rendered: String) derives ReadWriter
  case class WpPage(id: Int, title: WpTitle, content: WpContent, translations: Option[Map[String, Int]] = None) derives ReadWriter

  def render(param: String = ""): Boolean = 
    if (param.isEmpty) {
      setMain(cviews.pages.MainView.html.Base())
    } else {
      // 1. Loading State
      setMain(cviews.pages.MainView.html.Loading(param))

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
          // Fallback: Parameter is not a number, treat as slug
          ajaxGet[Seq[WpPage]](s"/wp-json/wp/v2/pages?slug=$param", List(), host = Global.homeUrl).map {
            case Right(pages) if pages.nonEmpty =>
              val page = pages.head
              val lang = Global.lang
              
              // Check if we need to load a translation
              val translatedIdOpt = page.translations.flatMap(_.get(lang))
              
              translatedIdOpt match {
                case Some(transId) if transId != page.id =>
                  // Fetch the translated page
                  ajaxGet[WpPage](s"/wp-json/wp/v2/pages/$transId", List(), host = Global.homeUrl).map {
                    case Right(transPage) =>
                      setMain(cviews.pages.MainView.html.PageContent(transPage.title.rendered, transPage.content.rendered))
                    case Left(_) =>
                      // Fallback to original page if translation fetch fails
                      setMain(cviews.pages.MainView.html.PageContent(page.title.rendered, page.content.rendered))
                  }
                case _ =>
                  // Current page is already the right language or no translation available
                  setMain(cviews.pages.MainView.html.PageContent(page.title.rendered, page.content.rendered))
              }
              
            case Right(_) =>
              setMain(cviews.pages.MainView.html.PageNotFound(param, "Keine Seite mit diesem Slug gefunden."))
            case Left(err) =>
              setMain(cviews.pages.MainView.html.PageNotFound(param, err.msgCode))
          }
      }
    }
    true

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

