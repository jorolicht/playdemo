package comps

import org.scalajs.dom
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.MainIds.NavbarId
import shared.DomTypes.*
import shared.PageNameTyp.*
import comps.Sidebar.AsideId

object Navbar extends BaseComp with base.JsWrapper with services.ComWrapper:
  def name = PageNameTyp("Navbar")
  val ToggleSidebarId: HtmlId = genId(name)
  val ConsoleClickId: HtmlId = genId(name)
  val ShowLoginId: HtmlId = genId(name)
  val DoLogoutId: HtmlId = genId(name)
  val ShowQuickCompId: HtmlId = genId(name)
  val StartFullId: HtmlId = genId(name)
  val StartSimpleId: HtmlId = genId(name)
  def render(param: String = "") = 
    setHtml(gE(NavbarId), cviews.comps.html.navbar()) 
    true

  override def handleEvent(elem: org.scalajs.dom.raw.HTMLElement, event: org.scalajs.dom.Event) =
    HtmlId(elem.id) match
      case `ToggleSidebarId` => toggleClass(gE(AsideId), "d-none")
      case `DoLogoutId`      => doLogout()
      case `ShowQuickCompId` => 
        if (base.Global.user.isDefined) doQuickStart() else services.DemoManager.promptDemoMode("template-single", () => doQuickStart(), () => ())
      case `StartFullId`     => 
        if (base.Global.user.isDefined) confirmLeave(() => pages.loadPage(PageNameTyp("TourneyNew"), ""))
        else services.DemoManager.promptDemoMode("template-full", () => confirmLeave(() => pages.loadPage(PageNameTyp("TourneyNew"), "")), () => ())
      case `StartSimpleId`   => 
        if (base.Global.user.isDefined) confirmLeave(() => pages.loadPage(PageNameTyp("CompetitionNew"), ""))
        else services.DemoManager.promptDemoMode("template-single", () => confirmLeave(() => pages.loadPage(PageNameTyp("CompetitionNew"), "")), () => ())
      case _                 => debug(s"event -> unknown event for elem:${elem.id} with event:${event.`type`}")

  private def confirmLeave(action: () => Unit): Unit =
    import shared.BoxButton
    import shared.model.Selection
    
    if (base.Global.currentSelection.tourney.isDefined) {
      dialogs.DlgMsgbox.show(
        "Möchten Sie das aktuelle Turnier / den aktuellen Wettbewerb wirklich verlassen? Nicht gespeicherte Änderungen gehen verloren.",
        "Aktion bestätigen",
        List(BoxButton.Yes, BoxButton.No)
      ).map {
        case BoxButton.Yes => 
          base.Global.currentSelection = Selection()
          comps.ContextHeader.hide()
          action()
        case _ => debug("Leave cancelled")
      }
    } else {
      action()
    }

  def doQuickStart(): Unit =
    import dialogs.*
    import shared.model.*
    import services.*
    import base.Global

    DlgCompetition.show(CompCategory.TT).map {
      case Right(res) =>
        val c = res.competition
        val dateInt = c.startDate.replace("-", "").toIntOption.getOrElse(0)
        
        // 1. Create SIMPLE dummy tournament
        val dummy = Tourney(
          wpId = 0,
          name = c.name,
          organizer = Global.user.map(_.username).getOrElse("System"),
          startDate = dateInt,
          endDate = dateInt,
          ident = "SIMPLE",
          category = c.category
        )
        
        // 2. Initialize TourneyDB with dummy
        TourneyDB.update(dummy)
        
        // 3. Add the competition
        TourneyDB.tourney.addCompetition(c.name, c.typ, c.category, c.startDate) match {
          case Right(newComp) =>
            Global.currentSelection = Selection(Some(TourneyDB.tourney), Some(newComp))
            comps.ContextHeader.render()
            pages.loadPage(PageNameTyp("CompetitionInfo"), "")
          case Left(err) =>
            dom.window.alert(s"Fehler beim Erstellen des Wettbewerbs: ${err.msgCode}")
        }
      case Left(_) => debug("Quick Start cancelled")
    }

  private def doLogout(): Unit =
    import services.*
    import base.Global

    ajaxPost[String, String]("/wp-json/tourney/v1/auth/logout", List(), "", host = Global.homeUrl).map { _ =>
      Global.resetUser
      dom.window.location.href = Global.homeUrl
    }
