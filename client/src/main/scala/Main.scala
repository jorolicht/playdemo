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
import comps.{ BaseComp, Navbar, Sidebar, ContextHeader }

import shared.basic.*
import shared.MainIds.*
import comps.Wordpress
import addon.Console

object Main extends BaseComp with ComWrapper with JsWrapper with Mgmt:
  def name = PageNameTyp("Main")
  
  @JSExportTopLevel("startApp")
  def startApp(version: String, mode: String, logLevel: String, tourney: String = ""): Unit = 
    Global.lang = dom.window.navigator.language.take(2)
    Global.dataUrl  = getData(gE(ParamId), "dataurl", "./data/")
    Global.imgUrl   = getData(gE(ParamId), "imgurl", "./img/")
    Logging.setLogLevel(logLevel.toLowerCase())
    //Global.tourney  = tourney

    Logging.setLogLevel(logLevel)
    println(s"startApp -> dataUrl:${Global.dataUrl} version:${version} lang:${Global.lang} mode:${mode} logLevel:${logLevel} tourney:${tourney}")

    dom.window.asInstanceOf[js.Dynamic].sayHello    = (name: String) => s"Hello $name"
    dom.window.asInstanceOf[js.Dynamic].appLoadPage = (pageName: String, param: String) => appLoadPage(pageName, param)
    dom.window.asInstanceOf[js.Dynamic].appEvent    = (elem: HTMLElement, event: dom.Event) => appEvent(elem, event)

    // expose addon Console.start function, only if addon is included, otherwise dummy function
    dom.window.asInstanceOf[js.Dynamic].startConsole = () => addon.Console.start()

    Messages.initMsg(version, Global.dataUrl, Global.lang).map {
      case true  => mode.toLowerCase() match 
        case "play" => startPlay()
        case "vite" => startVite()
        case _      => startWp(mode.toLowerCase())
       case false => println("Main program failed to initialize")  
    }

  def startWp(mode: String): Unit =
    import services.*
    import shared.model.*

    Global.playUrl = getData(gE(ParamId), "playurl","")
    Global.homeUrl = getData(gE(ParamId), "homeurl", "")
    Global.wpNonce = getData(gE(ParamId), "nonce", "")
    Global.pageId  = getData(gE(ParamId), "pageid", 0)

    mode match {
      case "multi"     => modeMulti()
      case "single"    => modeSingle()
      case "page"      => modePage(getData(gE(ParamId), "page", ""))
      case _           => debug(s"startWp -> unknown mode: ${mode}") 
    }

  def modePage(page: String): Unit =
    pages.loadPage(PageNameTyp(page), "", withSidebar = false, async = true)


  def modeSingle(): Unit =
    import services.*
    import shared.model.*

    debug(s"modeSingle -> playUrl: ${Global.playUrl}, homeUrl: ${Global.homeUrl}, lang: ${Global.lang}, nonce: ${Global.wpNonce}, pageId: ${Global.pageId}")

    // Render components
    ContextHeader.render("")
    Wordpress.render("")

    TourneyDB.init(Global.pageId).map {
      case Right(_) =>
        val html = cviews.pages.html.ViewTourney(
          TourneyDB.tourney,
          CompetitionDB.competitions.toSeq.filter(_ != null),
          RoundDB.rounds.toSeq.filter(_ != null),
          TourneyDB.tourney.clubs.toSeq,
          TourneyDB.tourney.players.toSeq
        )
        setHtml(gE(ContentId), html)
      case Left(err) =>
        error(s"Initialization failed in modeSingle: ${err.msgCode}")
        setHtml(gE(ContentId), s"<div class='alert alert-danger'>Fehler beim Laden der Daten: ${err.msgCode}</div>")
    }


  def modeMulti(): Unit = 
    import services.*
    import shared.model.*
    import shared.model.UserInfo
    import shared.model.User
    import cats.data.EitherT
    import cats.implicits._ 

    Global.pageId  = getData(gE(ParamId), "pageid", 0)
    
    debug(s"modeMulti -> playUrl: ${Global.playUrl}, homeUrl: ${Global.homeUrl}, lang: ${Global.lang}, nonce: ${Global.wpNonce}, pageId: ${Global.pageId}")

    // Render components
    ContextHeader.render("")
    Wordpress.render("")

    // Start loading user and tournament
    (for {
      user        <- EitherT(ajaxGet[UserInfo]("/wp-json/playdemo/v1/user", List(), Map("X-WP-NONCE"->Global.wpNonce), Global.homeUrl))
      timestamp   <- EitherT(TourneyDB.init(Global.pageId))
    } yield (user, timestamp)).value.map {
      case Right((ui, ts))  => 
        debug(s"User loaded: ${ui}, Tourney initialized, timestamp: $ts")
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
        
        // Navigation logic based on tourney.ident
        if (TourneyDB.tourney.ident == "IGNORE") {
          pages.loadPage(pages.MainMulti.name, "")
        } else {
          pages.loadPage(pages.InfoTourney.name, "")
        }

      case Left(err)   => debug(s"Error loading user or tournament: ${err}")
    }


  def startPlay() : Unit = 
    val usecase = gE(ParamId).getAttribute("data-usecase")
    val param   = gE(ParamId).getAttribute("data-param")
    Global.csrf  = gE(ParamId).getAttribute("data-csrf")

    debug(s"startPlay -> usecase:${usecase} param:${param} csrf:${Global.csrf}")
    
    // set visibility of basic html elements
    addClass(gE(JScriptId), "d-none")

    val evtSource = new dom.raw.EventSource(s"/helper/sse?id=${randomString(6)}")  
    evtSource.onmessage = { (e: dom.MessageEvent) => debug(s"Message from Server: ${e.data}") }

    // add context header
    ContextHeader.render("")
    // Sidebar.render("") - Removed as requested

    appLoadPage(usecase, param)  



  def startVite(): Unit =
    setHtml(gE(ContentId), "Start successful")


  def sayHello(name: String): Unit = {
    println(s"Hallo $name")
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
