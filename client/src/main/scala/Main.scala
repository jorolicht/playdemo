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
  
  // Direct Start without Shortcode
  @JSExportTopLevel("appMain")
  def appMain(): Unit = 
    val config = dom.window.asInstanceOf[js.Dynamic].playdemoConfig

    val version = "001DE1970-01"
    val mode        = config.mode.asInstanceOf[String]
    val logLevel    = config.logLevel.asInstanceOf[String]
    val slug        = Option(config.slug).map(_.asInstanceOf[String]).getOrElse("")
    Logging.setLogLevel(logLevel.toLowerCase())
    
    Global.dataUrl  = config.dataUrl.asInstanceOf[String]
    Global.imgUrl   = config.imgUrl.asInstanceOf[String]
    Global.playUrl  = config.playUrl.asInstanceOf[String]
    Global.homeUrl  = config.homeUrl.asInstanceOf[String]
    Global.wpNonce  = config.nonce.asInstanceOf[String]
    Global.pageId   = config.pageId.asInstanceOf[String].toIntOption.getOrElse(0)
    Global.hostPageId = Global.pageId
    Global.turnstileSitekey = config.turnstileSitekey.asInstanceOf[String]
    Global.lang = dom.window.navigator.language.take(2)
    
    println(s"appMain -> dataUrl:${Global.dataUrl} version:${version} lang:${Global.lang} mode:${mode} PageId:${Global.pageId} slug:${slug} logLevel:${logLevel}")    

    dom.window.asInstanceOf[js.Dynamic].appLoadPage = (pageName: String, param: String) => appLoadPage(pageName, param)
    dom.window.asInstanceOf[js.Dynamic].appEvent    = (elem: HTMLElement, event: dom.Event) => appEvent(elem, event)
    dom.window.asInstanceOf[js.Dynamic].startConsole = () => addon.Console.start()

    Messages.initMsg(version, Global.dataUrl, Global.lang).map {
      case true   => initApp(mode.toLowerCase(), slug)
       case false => println("Main program failed to initialize")  
    }

  // Start through Shortcode (non tourney CPT)
  @JSExportTopLevel("startApp")
  def startApp(version: String, mode: String, logLevel: String, tourney: String = ""): Unit = 

    Global.dataUrl  = getData(gE(ParamId), "dataurl", "./data/")
    Global.imgUrl   = getData(gE(ParamId), "imgurl", "./img/")
    Global.playUrl  = getData(gE(ParamId), "playurl","")
    Global.homeUrl  = getData(gE(ParamId), "homeurl", "")
    Global.wpNonce  = getData(gE(ParamId), "nonce", "")
    Global.pageId   = getData(gE(ParamId), "pageid", 0)
    Global.hostPageId = Global.pageId
    Global.turnstileSitekey = getData(gE(ParamId), "turnstileSitekey", "")
    Global.lang     = dom.window.navigator.language.take(2)
    Logging.setLogLevel(logLevel.toLowerCase())

    val slugAttr = getData(gE(ParamId), "slug", "")
    val postType = getData(gE(ParamId), "posttype", "")
    
    // If we are on a tourney post, or tourney param is set, or mode is tourney
    val effectiveMode = if (tourney.nonEmpty || postType == "tourney" || mode.toLowerCase() == "tourney") "tourney" else mode.toLowerCase()
    val effectiveTourney = if (tourney.nonEmpty) tourney else if (postType == "tourney") slugAttr else ""

    println(s"startApp -> dataUrl:${Global.dataUrl} version:${version} lang:${Global.lang} mode:${effectiveMode} PageId:${Global.pageId} tourney:${effectiveTourney} logLevel:${logLevel}")

    dom.window.asInstanceOf[js.Dynamic].appLoadPage = (pageName: String, param: String) => appLoadPage(pageName, param)
    dom.window.asInstanceOf[js.Dynamic].appEvent    = (elem: HTMLElement, event: dom.Event) => appEvent(elem, event)
    // expose addon Console.start function, only if addon is included, otherwise dummy function
    dom.window.asInstanceOf[js.Dynamic].startConsole = () => addon.Console.start()

    Messages.initMsg(version, Global.dataUrl, Global.lang).map {
      case true  => initApp(effectiveMode, effectiveTourney)        
      case false => println("Main program failed to initialize")  
    }


  def initApp(mode: String, tourneyParam: String = ""): Unit =
    import services.*
    import shared.model.*
    import shared.model.UserInfo
    import shared.model.User

    ajaxGet[UserInfo]("/wp-json/playdemo/v1/user", List(), Map("X-WP-NONCE"->Global.wpNonce), Global.homeUrl).map {
      case Right(ui) => 
        if (ui.user_id > 0) {
            Global.user = Some(User(
              id = ("", ui.user_id),
              username = ui.username,
              email = ui.email,
              firstname = ui.firstname,
              lastname = ui.lastname,
              org = ui.club,
              picUrl = ui.avatar_url,
              description = ui.description,
              roles = ui.roles
            ))
        } 
        
        // Render Framework Structure
        Navbar.render()
        Footer.render()
        
        // Attempt to restore page state from sessionStorage
        val storage = org.scalajs.dom.window.sessionStorage
        val lastWpPageId = storage.getItem("playdemo_last_wp_page_id")
        val lastPage = storage.getItem("playdemo_last_page")
        val lastParam = Option(storage.getItem("playdemo_last_param")).getOrElse("")
        val selectionJson = storage.getItem("playdemo_last_selection")

        val canRestore = lastWpPageId != null && 
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

          restoredSelection.tourney match {
            case Some(t) if t.wpId > 0 =>
              // Initialize Tourney database before rendering the page
              TourneyDB.init(t.wpId).map {
                case Right(ts) =>
                  debug(s"initApp -> Tourney initialized during state restoration, timestamp: $ts")
                  Global.currentSelection = restoredSelection
                  pages.loadPage(PageNameTyp(lastPage), lastParam)
                case Left(err) =>
                  error(s"initApp -> Error initializing tournament: $err")
                  runDefaultRouting(mode, tourneyParam)
              }
            case _ =>
              Global.currentSelection = restoredSelection
              pages.loadPage(PageNameTyp(lastPage), lastParam)
          }
        } else {
          runDefaultRouting(mode, tourneyParam)
        }

      case Left(err) => 
        debug(s"initApp -> Error loading user info: ${err}")
        Navbar.render()
        Footer.render()
        pages.loadPage(pages.MainView.name, "")
    }

  private def runDefaultRouting(mode: String, tourneyParam: String): Unit =
    // Route based on mode
    mode match {
      case "login"     => pages.loadPage(pages.UserLogin.name, "")
      case "register"  => pages.loadPage(pages.UserRegistration.name, "")
      case "verify"    => pages.loadPage(pages.VerifyAccount.name, "")
      case "tourney"   => modeTourney(if (tourneyParam.nonEmpty) tourneyParam else Global.pageId) 
      case "view"      => modeView(Global.pageId)
      case "home"      => modeHome()
      case _           => modeDefault(Global.pageId)
    }

  def modeView(pageId: Int): Unit = 
    pages.loadPage(pages.MainView.name, pageId.toString)

  def modeHome(): Unit = 
    pages.loadPage(pages.MainView.name, "")

  def modeDefault(pageId: Int): Unit = 
    pages.loadPage(pages.MainView.name, pageId.toString)

  def modeTourney(idOrSlug: Int | String): Unit = 
    import services.*
    import shared.model.*

    debug(s"modeTourney -> idOrSlug: ${idOrSlug}")
    
    val shouldLoad = idOrSlug match {
      case id: Int => id > 0
      case s: String => s.nonEmpty
    }

    if (shouldLoad) {
      TourneyDB.init(idOrSlug).map {
        case Right(ts) => 
          debug(s"Tourney initialized, timestamp: $ts")
          Global.currentSelection = Selection(Some(TourneyDB.tourney))
          pages.loadPage(pages.TourneyInfo.name, "")
        case Left(err) => 
          error(s"Error loading tournament: ${err}")
          pages.loadPage(pages.MainView.name, "")
      }
    } else {
      pages.loadPage(pages.MainView.name, "")
    }

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
