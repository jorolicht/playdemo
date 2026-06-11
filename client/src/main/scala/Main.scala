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
    Logging.setLogLevel(logLevel.toLowerCase())
    
    Global.dataUrl  = config.dataUrl.asInstanceOf[String]
    Global.imgUrl   = config.imgUrl.asInstanceOf[String]
    Global.playUrl  = config.playUrl.asInstanceOf[String]
    Global.homeUrl  = config.homeUrl.asInstanceOf[String]
    Global.wpNonce  = config.nonce.asInstanceOf[String]
    Global.pageId   = config.pageId.asInstanceOf[String].toIntOption.getOrElse(0)
    Global.turnstileSitekey = config.turnstileSitekey.asInstanceOf[String]
    Global.lang = dom.window.navigator.language.take(2)
    
    println(s"appMain -> dataUrl:${Global.dataUrl} version:${version} lang:${Global.lang} mode:${mode} PageId:${Global.pageId}  logLevel:${logLevel}")    

    dom.window.asInstanceOf[js.Dynamic].appLoadPage = (pageName: String, param: String) => appLoadPage(pageName, param)
    dom.window.asInstanceOf[js.Dynamic].appEvent    = (elem: HTMLElement, event: dom.Event) => appEvent(elem, event)
    dom.window.asInstanceOf[js.Dynamic].startConsole = () => addon.Console.start()

    Messages.initMsg(version, Global.dataUrl, Global.lang).map {
      case true   => initApp(mode.toLowerCase())
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
    Global.turnstileSitekey = getData(gE(ParamId), "turnstileSitekey", "")
    Global.lang     = dom.window.navigator.language.take(2)
    Logging.setLogLevel(logLevel.toLowerCase())

    println(s"startApp -> dataUrl:${Global.dataUrl} version:${version} lang:${Global.lang} mode:${mode} PageId:${Global.pageId}  logLevel:${logLevel} tourney:${tourney}")

    dom.window.asInstanceOf[js.Dynamic].appLoadPage = (pageName: String, param: String) => appLoadPage(pageName, param)
    dom.window.asInstanceOf[js.Dynamic].appEvent    = (elem: HTMLElement, event: dom.Event) => appEvent(elem, event)
    // expose addon Console.start function, only if addon is included, otherwise dummy function
    dom.window.asInstanceOf[js.Dynamic].startConsole = () => addon.Console.start()

    Messages.initMsg(version, Global.dataUrl, Global.lang).map {
      case true  => initApp(mode)        
      case false => println("Main program failed to initialize")  
    }


  def initApp(mode: String): Unit =
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
        
        // Route based on mode
        mode match {
          case "login"     => pages.loadPage(pages.UserLogin.name, "")
          case "register"  => pages.loadPage(pages.UserRegistration.name, "")
          case "verify"    => pages.loadPage(pages.VerifyAccount.name, "")
          case "tourney"   => modeTourney(Global.pageId) // Use modeTourney for 'multi' mode
          case "view"      => modeView(Global.pageId)
          case "home"      => modeHome()
          case _           => modeDefault(Global.pageId)
        }

      case Left(err) => 
        debug(s"initApp -> Error loading user info: ${err}")
        Navbar.render()
        Footer.render()
        pages.loadPage(pages.MainView.name, "")
    }

  def modeView(pageId: Int): Unit = 
    pages.loadPage(pages.MainView.name, pageId.toString)

  def modeHome(): Unit = 
    pages.loadPage(pages.MainView.name, "")

  def modeDefault(pageId: Int): Unit = 
    pages.loadPage(pages.MainView.name, pageId.toString)

  def modeTourney(pageId: Int): Unit = 
    import services.*
    import shared.model.*

    debug(s"modeTourney -> pageId: ${pageId}")
    if (pageId > 0) {
      TourneyDB.init(pageId).map {
        case Right(ts) => 
          debug(s"Tourney initialized, timestamp: $ts")
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
