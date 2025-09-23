import upickle.default._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation._
// import scala.collection.mutable.Map

import services.ComWrapper
import base.{ Global, JsWrapper, Messages, Logging, _ }
import shared._
import shared.Ids._

// Define an external object that represents the global JavaScript object created by wp_localize_script.
@js.native
@JSGlobal("PlaydemoAppData")
object PlaydemoAppData extends js.Object {
  val user: String = js.native
  val user_id: Int = js.native
  val email: String = js.native
  val club: String = js.native
  val nonce: String = js.native
}


@JSExportTopLevel("Main")
object Main extends ComWrapper with JsWrapper with Mgmt:

  def gMP(key: String, inserts: String*) = Messages.getMsg(s"Main.${key}", inserts*)
  def gM(key: String, inserts: String*)  = Messages.getMsg(key, inserts*)
  given ucp:UseCaseParam = UseCaseParam("Main", gMP, gM)

  @JSExport
  def wpStart(server: String, wpUrl: String, nonce: String): Unit = 
    import cats.data.EitherT
    import cats.implicits._ 

    Logging.setLogLevel("debug")
    debug(s"wpStart -> server:${server} wordpress_url: ${wpUrl} nonce: ${nonce} ")

    setServer(server)
    setHtml(gE("appcontent"), cviews.html.wpMain())

    (for {
      version  <- EitherT( ajaxGet[String]("/helper/getMsg", List(("msgCode", "app.version"))) ) 
      lang     <- EitherT( ajaxGet[String]("/helper/getMsg", List(("msgCode", "app.lang"))) ) 
    } yield { (version, lang) }).value.map {
      case Left(err)    => error(s"Main program failed to initialize: ${err}")
      case Right(res)   => Messages.initMsg(res._1, res._2).map {
        case true  =>
          setVersion(res._1) 
          setLang(res._2)
          
          // add sidebar
          setHtml(gE("sidebar"), cviews.html.sidebar()) 

          ajaxGet[String]("/wp-json/playdemo/v1/user", List(), Map("X-WP-NONCE"->nonce), "http://localhost:8080").map { 
            case Left(err)  => error(s"Fehler: ${err}")
            case Right(res) => debug(s"Result: ${res}")  
          }

          debug(s"Main program initialized")
          

        case false  =>  
          error("Main program failed to initialize")
      }    
    }


  /** main - entry point of application
   */  
  @JSExport
  def start(usecase: String, param: String, version: String, language: String, csrfToken: String): Unit = 

    // init app global variables
    setVersion(version) 
    setLang(language) 
    setCsrf(csrfToken)

    // set visibility of basic html elements
    addClass(gE(Main_JavascriptEnableInfo), "d-none")

    val evtSource = new dom.raw.EventSource(s"/helper/sse?id=${randomString(6)}")  
    evtSource.onmessage = { (e: dom.MessageEvent) => debug(s"Message from Server: ${e.data}") }

    Logging.setLogLevel("debug")
    
    println(s"Main program start usecase:${usecase} param:${param} version:${version} lang:${language}")
    Messages.initMsg(version, language).map {
      case true  =>
        // add nav-bar header
        setHtml(gE("navbar"), cviews.html.navbar())
        // add sidebar
        setHtml(gE("sidebar"), cviews.html.sidebar())   
       
        initUser

        debug(s"Main program initialized usecase/param:${usecase}/${param} version:${version} lang:${Global.lang}")
        ucExec(usecase, param)
      case false => error("Main program failed to initialize")
    }
  

  @JSExport
  def exec(usecase: String, param: String): Unit = ucExec(usecase, param)

  @JSExport
  def handleGoogleCredential(credentials: String): Unit = usecases.Auth.googleLogin(credentials)

  @JSExport
  def event(elem: HTMLElement, event: dom.Event): Unit =
    try
      val (usecase, key) = elem.id.toTuple("_")
      debug(s"event -> usecase:${usecase} key:${key} elem:${elem.id}")      
      Global.ucMap(usecase).event(elem, event)
    catch
      case e: Exception => error(s"event -> elem:${elem.id} failed") 

  @JSExport
  def getLogLevel():Option[String] =  Logging.getLogLevel()
 
  @JSExport
  def setLogLevel(value: String="") = Logging.setLogLevel(value)
