import upickle.default.*
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.*

import services.ComWrapper
import base.{ Global, JsWrapper, _ }
import comps.{ CompBase, Navbar, Sidebar }

import shared.basic.*
import shared.MainIds.*
import comps.Wordpress
import addon.Console

object Main extends CompBase with ComWrapper with JsWrapper with Mgmt:
  def name = PageNameTyp("Main")
  
  @JSExportTopLevel("startApp")
  def startApp(version: String, startEnv: String, logLevel: String): Unit = 
    Global.lang = dom.window.navigator.language.take(2)
    Global.dataUrl  = getData(gE(ParamId), "dataurl", "./data/")

    Logging.setLogLevel(logLevel)
    println(s"startApp -> dataUrl:${Global.dataUrl} version:${version} lang:${Global.lang} env:${startEnv} logLevel:${logLevel}")

    dom.window.asInstanceOf[js.Dynamic].sayHello    = (name: String) => s"Hello $name"
    dom.window.asInstanceOf[js.Dynamic].appLoadPage = (pageName: String, param: String) => appLoadPage(pageName, param)
    dom.window.asInstanceOf[js.Dynamic].appEvent    = (elem: HTMLElement, event: dom.Event) => appEvent(elem, event)

    // expose addon Console.start function, only if addon is included, otherwise dummy function
    dom.window.asInstanceOf[js.Dynamic].startConsole = () => addon.Console.start()

    Messages.initMsg(version, Global.dataUrl, Global.lang).map { 
      case true  => startEnv.toLowerCase() match 
        case "play"  => startPlay()
        case "wp"    => startWp()
        case "vite"  => startVite()
      case false => println("Main program failed to initialize")  
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

    // add nav-bar header and sidebar
    Navbar.render("")
    Sidebar.render("") 

    appLoadPage(usecase, param)  


  def startWp(): Unit = 
    import shared.model.UserInfo
    import cats.data.EitherT
    import cats.implicits._ 

    Global.playUrl = getData(gE(ParamId),"playurl","")
    Global.homeUrl = getData(gE(ParamId), "homeurl", "")
    Global.wpNonce   = getData(gE(ParamId), "nonce", "")
    Global.pageId  = getData(gE(ParamId), "pageid", 0)

    debug(s"wpStart -> playUrl:${Global.playUrl} homeUrl: ${Global.homeUrl} lang: ${Global.lang} nonce: ${Global.wpNonce} pageId: ${Global.pageId}")

    // init wordpress main page
    Wordpress.render("")

    ajaxGet[UserInfo]("/wp-json/playdemo/v1/user", List(), Map("X-WP-NONCE"->Global.wpNonce), Global.homeUrl).map { 
      case Left(err)  => error(s"Fehler: ${err}")
      case Right(res) => debug(s"Result: ${res}")  
    }


  def startVite(): Unit =
    setHtml(gE(ContentId), "Start successful")


  def sayHello(name: String): Unit = {
    println(s"Hallo $name")
  }


  def handleGoogleCredential(credentials: String): Unit = pages.Auth.googleLogin(credentials)

  def appLoadPage(pageName: String, param: String): Unit = pages.loadPage(PageNameTyp(pageName), param)

  def render(param: String = ""): Boolean = true

  def appEvent(elem: HTMLElement, event: dom.Event): Unit =
    try
      val (name, key) = elem.id.toTuple("_")
      val pgDlgName = PageNameTyp(name)

      debug(s"event -> pgDlgName:${name} key:${key} elem:${elem.id}")

      if pages.pagesMap.contains(pgDlgName) then
        pages.pagesMap(pgDlgName).event(elem, event)
      else
        dialogs.dlgMap(pgDlgName).event(elem, event)
    catch
      case e: Exception => error(s"event -> elem:${elem.id} failed") 



  @JSExportTopLevel("getLogLevel")
  def getLogLevel():Option[String] =  Logging.getLogLevel()
 
  @JSExportTopLevel("setLogLevel")
  def setLogLevel(value: String="") = Logging.setLogLevel(value)
