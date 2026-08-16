package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.model.*
import services.ComWrapper

object Management extends BasePage with JsWrapper with ComWrapper:
  def name = PageNameTyp("Management")

  def render(param: String = ""): Boolean =
    if (!Global.user.exists(_.roles.contains("administrator"))) {
      pages.loadPage(PageNameTyp("PgError"), "Zugriff verweigert: Die Management-Seite steht nur Administratoren zur Verfügung.")
      false
    } else {
      setMain(cviews.pages.html.Management(Global.user))
      true
    }

  override def handleEvent(elem: HTMLElement, event: dom.Event): Unit = ()
