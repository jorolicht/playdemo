import upickle.default.*
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.*


// import scala.collection.mutable.Map
import services.ComWrapper
import base.{ Global, JsWrapper, Messages, Logging, _ }
import shared._
import shared.IdsGlobal.*


object Main extends ComWrapper with JsWrapper with Mgmt:

  def gMP(key: String, inserts: String*) = Messages.getMsg(s"Main.${key}", inserts*)
  def gM(key: String, inserts: String*)  = Messages.getMsg(key, inserts*)
  given ucp:UseCaseParam = UseCaseParam("Main", gMP, gM)


  @JSExportTopLevel("startApp")
  def startApp(version: String, startEnv: String, logLevel: String): Unit = 
    Global.lang = dom.window.navigator.language.take(2)

    Logging.setLogLevel(logLevel)
    println(s"startApp -> version:${version} lang:${Global.lang} env:${startEnv} logLevel:${logLevel}")

    Messages.initMsg(version, Global.lang).map { 
      case true  => startEnv.toLowerCase() match 
        case "play"  => startPlay()
        case "wp"    => startWp()
        case "vite"  => startVite()
      case false => println("Main program failed to initialize")  
    }


  def startPlay() : Unit = 
    val usecase = gE2(AppParamId).getAttribute("data-usecase")
    val param   = gE2(AppParamId).getAttribute("data-param")
    Global.csrf  = gE2(AppParamId).getAttribute("data-csrf")

    debug(s"startPlay -> usecase:${usecase} param:${param} csrf:${Global.csrf}")
    
    // set visibility of basic html elements
    addClass(gE2(JavascriptEnabledInfoId), "d-none")

    val evtSource = new dom.raw.EventSource(s"/helper/sse?id=${randomString(6)}")  
    evtSource.onmessage = { (e: dom.MessageEvent) => debug(s"Message from Server: ${e.data}") }

    // add nav-bar header
    setHtml(gE2(NavbarId), cviews.html.navbar())
    // add sidebar
    setHtml(gE2(SidebarId), cviews.html.sidebar())   
    initUser
    ucExec(usecase, param)  


  def startWp(): Unit = 
    import cats.data.EitherT
    import cats.implicits._ 

    Global.srvUrl = gE2(AppParamId).getAttribute("data-serverUrl")
    Global.wpUrl  = gE2(AppParamId).getAttribute("data-wpUrl")
    Global.nonce  = gE2(AppParamId).getAttribute("data-nonce")

    debug(s"wpStart -> serverUrl:${Global.srvUrl} wpUrl: ${Global.wpUrl} nonce: ${Global.nonce}")

    setHtml(gE2(AppContentId), cviews.html.wpMain())

    // add sidebar
    setHtml(gE2(SidebarId), cviews.html.sidebar()) 

    ajaxGet[String]("/wp-json/playdemo/v1/user", List(), Map("X-WP-NONCE"->Global.nonce), "http://localhost:8080").map { 
      case Left(err)  => error(s"Fehler: ${err}")
      case Right(res) => debug(s"Result: ${res}")  
    }


  def startVite(): Unit =
    setHtml(gE2(AppContentId), "Start successful")



  @JSExportTopLevel("execUsecase")
  def exec(usecase: String, param: String): Unit = ucExec(usecase, param)

  @JSExport
  def handleGoogleCredential(credentials: String): Unit = usecases.Auth.googleLogin(credentials)

  @JSExportTopLevel("eventApp")
  def event(elem: HTMLElement, event: dom.Event): Unit =
    try
      val (usecase, key) = elem.id.toTuple("_")
      debug(s"event -> usecase:${usecase} key:${key} elem:${elem.id}")      
      Global.ucMap(usecase).event(elem, event)
    catch
      case e: Exception => error(s"event -> elem:${elem.id} failed") 

  @JSExportTopLevel("getLogLevel")
  def getLogLevel():Option[String] =  Logging.getLogLevel()
 
  @JSExportTopLevel("setLogLevel")
  def setLogLevel(value: String="") = Logging.setLogLevel(value)
