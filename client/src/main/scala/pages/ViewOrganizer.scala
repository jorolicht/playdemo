package pages

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import shared.basic.Pickle.*
import services.*

import base.* 
import base.Logging

import org.scalajs.dom
import org.scalajs.dom.{ Event }
import org.scalajs.dom.raw.{ HTMLElement }

import shared.MainIds.WordpressId


object ViewOrganizer extends BasePage with base.JsWrapper with ComWrapper:
  def name = PageNameTyp("ViewOrganizer") 

  val Details: HtmlId = genId(name)
  
  def render(param: String = ""): Boolean = true

  override def renderAsync(param: String = ""): Future[Boolean] = 
    case class Organizer(id: Int, title: String, slug: String, count: Int) derives ReadWriter

    debug(s"ViewOrganizer -> Fetching organizers")
    ajaxGet[Seq[Organizer]]("/wp-json/tourney/v1/organizers", List()).map {
      case Right(orgs) =>
        val organizers = orgs.map(o => (o.id, o.title, o.slug, o.count))
        val html = cviews.pages.html.ViewOrganizer(organizers)
        setHtml(gE(WordpressId), html)
        true
      case Left(err) =>
        error(s"Failed to load organizers: ${err.msgCode}")
        setHtml(gE(WordpressId), s"<div class='alert alert-danger'>Fehler beim Laden der Organisatoren: ${err.msgCode}</div>")
        false
    }


  override def handleEvent(elem: HTMLElement, event: Event) =   
    HtmlId(elem.id) match
      case `Details` => loadTourneysForOrganizer(getData(elem, "slug", ""))
      case _        => error(s"event -> invalid elem/key: ${elem.id}")     


  def loadTourneysForOrganizer(slug: String): Unit =
      import shared.model.*
      
      // Response models for standard WP API
      case class WpTitle(rendered: String) derives ReadWriter
      case class WpPost(id: Int, title: WpTitle, link: String) derives ReadWriter

      val containerId = s"tourneys-$slug"
      val container = dom.document.getElementById(containerId).asInstanceOf[HTMLElement]
      
      if (container.classList.contains("d-none")) {
        container.classList.remove("d-none")
        setHtml(container, "<em>Lade Turniere...</em>")
        
        // Wir brauchen die Parent ID für diesen Slug. 
        ajaxGet[Seq[WpPost]](s"/wp-json/wp/v2/tourney?slug=$slug", List()).map {
          case Right(parents) if parents.nonEmpty =>
            val parentId = parents.head.id
            ajaxGet[Seq[WpPost]](s"/wp-json/wp/v2/tourney?parent=$parentId", List()).map {
              case Right(children) =>
                if (children.isEmpty) {
                  setHtml(container, "<div class='alert alert-info py-1'>Keine Turniere gefunden.</div>")
                } else {
                  val listItems = children.map { c =>
                    val title = c.title.rendered
                    val link = c.link
                    s"<a href='$link' class='list-group-item list-group-item-action py-1'>$title</a>"
                  }.mkString("")
                  setHtml(container, s"<div class='list-group mt-1 shadow-sm'>$listItems</div>")
                }
              case Left(_) => setHtml(container, "<div class='alert alert-danger py-1'>Fehler beim Laden.</div>")
            }
          case _ => setHtml(container, "<div class='alert alert-danger py-1'>Organisator nicht gefunden.</div>")
        }
      } else {
        container.classList.add("d-none")
      }