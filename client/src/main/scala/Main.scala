import shared.basic.Pickle.*
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.*

import services.{ ComWrapper, ClubDB, PlayerDB, CompetitionDB, TourneyDB }
import base.{ Global, JsWrapper, _ }
import comps.{ BaseComp, Navbar, Sidebar, ContextHeader, Footer }

import shared.basic.*
import shared.MainIds.*
import comps.Wordpress
import addon.Console

object Main extends BaseComp with ComWrapper with JsWrapper with Mgmt:
  def name = PageNameTyp("Main")

  /**
   * Startet die Tourney-Anwendung (über WordPress-Shortcode [tourney] sowie Direktansicht des CPT 'tourney').
   * Plugin-Variante (fws), Homepage und Schreibrechte werden von WordPress als data-Attribute übergeben.
   *
   * @param version   Versionskennung der Applikation (SemVer MAJOR.MINOR.PATCH)
   * @param logLevel  Log-Level ("debug", "info", "warn", "error")
   * @param tourney   Optionaler Turnier-Identifier / Slug
   */
  @JSExportTopLevel("startApp")
  def startApp(version: String, logLevel: String, tourney: String = ""): Unit = 
    val effectiveVersion = if (version != null && version.nonEmpty && version != "001DE1970-01") version else shared.AppVersion.version

    Global.dataUrl  = getData(gE(ParamId), "dataurl", "./data/")
    Global.imgUrl   = getData(gE(ParamId), "imgurl", "./img/")
    Global.playUrl  = getData(gE(ParamId), "playurl","")
    Global.homeUrl  = getData(gE(ParamId), "homeurl", "")
    Global.wpNonce  = getData(gE(ParamId), "nonce", "")
    Global.pageId   = getData(gE(ParamId), "pageid", 0)
    Global.hostPageId = Global.pageId
    Global.turnstileSitekey = getData(gE(ParamId), "turnstilesitekey", "")
    Global.socialFacebook   = getData(gE(ParamId), "socialfacebook", "")
    Global.socialX          = getData(gE(ParamId), "socialx", "")
    Global.socialInstagram  = getData(gE(ParamId), "socialinstagram", "")
    Global.fws         = getData(gE(ParamId), "fws", 1) == 1
    Global.isHomePage  = getData(gE(ParamId), "ishome", 0) == 1
    Global.canEditPage = getData(gE(ParamId), "canedit", 0) == 1
    Global.lang     = dom.window.navigator.language.take(2)
    Logging.setLogLevel(logLevel.toLowerCase())

    val slugAttr  = getData(gE(ParamId), "slug", "")
    val postType  = getData(gE(ParamId), "posttype", "")
    val startPage = getData(gE(ParamId), "page", "").toLowerCase()
    val effectiveTourney = if (tourney.nonEmpty) tourney else if (postType == "tourney") slugAttr else ""

    println(s"startApp -> dataUrl:${Global.dataUrl} version:${effectiveVersion} lang:${Global.lang} fws:${Global.fws} home:${Global.isHomePage} canEdit:${Global.canEditPage} page:${startPage} PageId:${Global.pageId} tourney:${effectiveTourney} logLevel:${logLevel} turnstile:${Global.turnstileSitekey}")

    dom.window.asInstanceOf[js.Dynamic].appLoadPage = (pageName: String, param: String) => appLoadPage(pageName, param)
    dom.window.asInstanceOf[js.Dynamic].appEvent    = (elem: HTMLElement, event: dom.Event) => appEvent(elem, event)
    // expose addon Console.start function, only if addon is included, otherwise dummy function
    dom.window.asInstanceOf[js.Dynamic].startConsole = () => addon.Console.start()

    // Zurück/Vor im Browser zwischen statischen Seiten (/preise/ …) und Startseite (URLs setzt pages.syncUrl)
    if (Global.fws) dom.window.addEventListener("popstate", (_: dom.PopStateEvent) => {
      val path = dom.window.location.pathname
      pages.MainView.staticPageOfPath(path) match
        case Some(page) => pages.loadPage(pages.MainView.name, page)
        case None if path == new dom.URL(pages.MainView.staticUrl("")).pathname => pages.loadPage(pages.MainView.name, "")
        case None => dom.window.location.reload()
    })

    // Ausstehende Änderungen sofort schreiben. Server-Requests sind asynchron und werden beim
    // Entladen der Seite abgebrochen, daher den Browser-Dialog "Seite verlassen?" anzeigen,
    // damit die Seite offen bleibt, bis der Sync abgeschlossen ist (lokal/Demo schreibt synchron).
    dom.window.addEventListener("beforeunload", (e: dom.BeforeUnloadEvent) => {
      val dbs = List(services.StageDB, services.CompetitionDB, services.ClubDB, services.PlayerDB, services.TourneyDB)
      if (!Global.isDemoMode && !Global.isLocalMode && dbs.exists(_.hasPending)) {
        e.preventDefault()
        e.returnValue = ""
      }
      dbs.foreach(_.flushSync())
    })

    Messages.initMsg(effectiveVersion, Global.dataUrl, Global.lang).map {
      case true  => initApp(startPage, effectiveTourney, postType == "tourney")
      case false => println("Main program failed to initialize")  
    }


  /**
   * Lädt die Benutzerinformationen, rendert Navbar/Footer und stellt den letzten Seitenzustand
   * wieder her oder wählt die Startseite (siehe runDefaultRouting).
   *
   * @param startPage     Optionale Startseite aus dem Shortcode-Attribut page (login, register, verify)
   * @param tourneyParam  Optionaler Turnier-Slug (URL-Parameter, Permalink oder CPT-Slug)
   * @param isTourneyPost True, wenn die einbettende Seite ein CPT 'tourney' ist
   */
  def initApp(startPage: String, tourneyParam: String, isTourneyPost: Boolean): Unit =
    import services.*
    import shared.model.*
    import shared.model.UserInfo
    import shared.model.User

    ajaxGet[UserInfo]("/wp-json/tourney/v1/user", List(), Map("X-WP-NONCE"->Global.wpNonce), Global.homeUrl).map {
      case Right(ui) => 
        if (ui.user_id > 0) {
            Global.user = Some(User(id = ("", ui.user_id), username = ui.username, email = ui.email, firstname = ui.firstname,
              lastname = ui.lastname, org = ui.club, picUrl = ui.avatar_url, description = ui.description, roles = ui.roles,
              hasPasskey = ui.has_passkey
            ))
        } 
        
        // Render Framework Structure
        Navbar.render()
        Footer.render()
        
        // Attempt to restore page state from sessionStorage
        val storage = org.scalajs.dom.window.sessionStorage
        val lastWpPageId = storage.getItem("tourney_last_wp_page_id")
        val lastPage = storage.getItem("tourney_last_page")
        val lastParam = Option(storage.getItem("tourney_last_param")).getOrElse("")
        val selectionJson = storage.getItem("tourney_last_selection")

        // Full Web Site: Wiederherstellung nur auf der Homepage; eingebettet: nur Bearbeitungsseiten für Schreibberechtigte
        val restoreAllowed = if (Global.fws) Global.isHomePage && tourneyParam.isEmpty
                             else Global.canEditPage && lastPage != null && Global.isTourneyPage(lastPage) && lastPage != "TourneyWelcome" && lastPage != "CompetitionWelcome"
        // Statische Seiten haben eine eigene URL; auf der Startseite nicht wiederherstellen
        val lastIsStatic = lastPage == pages.MainView.name.value && pages.MainView.isStaticPage(lastParam)
        val canRestore = startPage.isEmpty && !lastIsStatic &&
                         restoreAllowed &&
                         lastWpPageId != null && 
                         lastWpPageId == Global.pageId.toString && 
                         lastPage != null && 
                         pages.pagesMap.contains(PageNameTyp(lastPage))

        debug(s"initApp -> state recovery check: canRestore=$canRestore, lastWpPageId=$lastWpPageId, Global.pageId=${Global.pageId}, lastPage=$lastPage, containsPage=${if (lastPage != null) pages.pagesMap.contains(PageNameTyp(lastPage)) else false}")

        if (canRestore) {
          debug(s"initApp -> Restoring page state: page=$lastPage, param=$lastParam")
          var restoredSelection = Selection()
          if (selectionJson != null) {
            try {
              restoredSelection = shared.basic.Pickle.read[Selection](selectionJson)
            } catch {
              case _: Exception => debug("initApp -> Deserialization of selection failed")
            }
          }

          if (!Global.isTourneyPage(lastPage)) {
            Global.currentSelection = Selection()
            pages.loadPage(PageNameTyp(lastPage), lastParam)
          } else {
            restoredSelection.tourney match {
              case Some(t) if t.wpId > 0 =>
                // Initialize Tourney database before rendering the page
                TourneyDB.init(t.wpId).map {
                  case Right(ts) =>
                    debug(s"initApp -> Tourney initialized during state restoration, timestamp: $ts")
                    Global.currentSelection = restoredSelection.copy(tourney = Some(TourneyDB.tourney))
                    pages.loadPage(PageNameTyp(lastPage), lastParam)
                  case Left(err) =>
                    error(s"initApp -> Error initializing tournament: $err")
                    runDefaultRouting(startPage, tourneyParam, isTourneyPost)
                }
              case _ =>
                Global.currentSelection = restoredSelection
                pages.loadPage(PageNameTyp(lastPage), lastParam)
            }
          }
        } else {
          runDefaultRouting(startPage, tourneyParam, isTourneyPost)
        }

      case Left(err) => 
        debug(s"initApp -> Error loading user info: ${err}")
        Navbar.render()
        Footer.render()
        runDefaultRouting(startPage, tourneyParam, isTourneyPost)
    }

  /**
   * Wählt die Startseite abhängig von Plugin-Variante (fws), Homepage und Schreibrechten:
   *  - fws, Homepage: MainView
   *  - fws, andere Seite: Turnier im Edit- bzw. View-Modus, ohne gültiges Turnier Fehlermeldung
   *  - eingebettet, CPT 'tourney' mit Turnier: Edit- bzw. View-Modus
   *  - eingebettet, ohne Turnier: PureView (Schreibrecht) bzw. Hinweisseite
   *
   * @param startPage     Optionale Startseite aus dem Shortcode-Attribut page (login, register, verify)
   * @param tourneyParam  Optionaler Turnier-Slug
   * @param isTourneyPost True, wenn die einbettende Seite ein CPT 'tourney' ist
   */
  private def runDefaultRouting(startPage: String, tourneyParam: String, isTourneyPost: Boolean): Unit =
    startPage match {
      case "login"    => pages.loadPage(pages.UserLogin.name, "")
      case "register" => pages.loadPage(pages.UserRegistration.name, "")
      case "verify"   => pages.loadPage(pages.VerifyAccount.name, "")
      case s if s.startsWith("static:") => pages.loadPage(pages.MainView.name, s.stripPrefix("static:"))
      case _ =>
        if (Global.fws && Global.isHomePage && tourneyParam.isEmpty) {
          services.Stats.trackOncePerSession("home")
          pages.loadPage(pages.MainView.name, "")
        }
        else if (Global.fws) openPageTourney(if (tourneyParam.nonEmpty) tourneyParam else Global.pageId)
        else if (isTourneyPost && Global.hostPageId > 0) openPageTourney(Global.hostPageId)
        else showNoTourney()
    }

  /**
   * Lädt das Turnier der aktuellen Seite und zeigt es im Edit-Modus (Schreibrecht auf die Seite)
   * oder im View-Modus an; ohne gültiges Turnier wird showNoTourney aufgerufen.
   *
   * @param idOrSlug Turnier-Post-ID oder Slug.
   */
  def openPageTourney(idOrSlug: Int | String): Unit =
    import services.*
    import shared.model.*

    debug(s"openPageTourney -> idOrSlug: ${idOrSlug} canEdit: ${Global.canEditPage}")
    TourneyDB.init(idOrSlug).map {
      case Right(_) if TourneyDB.tourney != null && TourneyDB.tourney.name.nonEmpty =>
        val t = TourneyDB.tourney
        Global.isLocalMode = false
        Global.isDemoMode = false
        if (Global.canEditPage) {
          comps.Navbar.openTourneyView(t)
        } else {
          val compOpt = t.competitions.find(c => c != null && !c.deleted)
          if (t.ident == "SIMPLE" && compOpt.isDefined) {
            Global.currentSelection = Selection(Some(t), compOpt)
            pages.loadPage(pages.CompetitionWelcome.name, "")
          } else {
            Global.currentSelection = Selection(Some(t))
            pages.loadPage(pages.TourneyWelcome.name, "")
          }
        }
      case Right(_) =>
        showNoTourney()
      case Left(err) =>
        error(s"openPageTourney -> Error loading tournament ${idOrSlug}: ${err}")
        showNoTourney()
    }

  /**
   * Anzeige, wenn die aktuelle Seite kein gültiges Turnier enthält:
   * Full Web Site: Fehlermeldung; eingebettet: PureView (Schreibrecht) bzw. Hinweisseite.
   */
  def showNoTourney(): Unit =
    if (Global.fws) pages.loadPage(pages.TourneyNotice.name, "notFound")
    else if (Global.canEditPage) pages.loadPage(pages.PureView.name, "")
    else pages.loadPage(pages.TourneyNotice.name, "comingSoon")

  def handleGoogleCredential(credentials: String): Unit = pages.Auth.googleLogin(credentials)

  def appLoadPage(pageName: String, param: String): Unit = 
    pages.loadPage(PageNameTyp(pageName), param)


  def render(param: String = ""): Boolean = true

  def appEvent(elem: HTMLElement, event: dom.Event): Unit =
    try
      val (name, key) = elem.id.toTuple("_")
      val pgDlgName = PageNameTyp(name)

      debug(s"event -> pgDlgName:${name} key:${key} elem:${elem.id}")

      if pages.pagesMap.contains(pgDlgName) then
        pages.pagesMap(pgDlgName).handleEvent(elem, event)
      else if comps.compsMap.contains(pgDlgName) then
        comps.compsMap(pgDlgName).handleEvent(elem, event)
      else
        dialogs.dlgMap(pgDlgName).handleEvent(elem, event)
    catch
      case e: Exception => error(s"event -> elem:${elem.id} failed") 



  @JSExportTopLevel("getLogLevel")
  def getLogLevel():Option[String] =  Logging.getLogLevel()
 
  @JSExportTopLevel("setLogLevel")
  def setLogLevel(value: String="") = Logging.setLogLevel(value)
