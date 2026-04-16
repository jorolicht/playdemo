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
import comps.{ BaseComp, Navbar, Sidebar }

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
        case "play"  => startPlay()
        case "wp"    => startWp()
        case "vite"  => startVite()
        case "view"  => startView()
        case "overview" => startOverview()
      case false => println("Main program failed to initialize")  
    }


  def startOverview(): Unit =
    import shared.model.*
    
    case class Organizer(id: Int, title: String, slug: String, count: Int) derives ReadWriter

    Global.playUrl = getData(gE(ParamId), "playurl","")
    Global.homeUrl = getData(gE(ParamId), "homeurl", "")
    Global.wpNonce = getData(gE(ParamId), "nonce", "")

    debug(s"startOverview -> Fetching organizers")

    ajaxGet[Seq[Organizer]]("/wp-json/tourney/v1/organizers", List()).map {
      case Right(orgs) =>
        val organizers = orgs.map(o => (o.id, o.title, o.slug, o.count))
        val html = cviews.pages.html.ViewOverview(organizers)
        setHtml(gE(WordpressId), html)
      case Left(err) =>
        error(s"Failed to load organizers: ${err.msgCode}")
        setHtml(gE(WordpressId), s"<div class='alert alert-danger'>Fehler beim Laden der Organisatoren: ${err.msgCode}</div>")
    }


  def startView(): Unit =
    import services.*
    import shared.model.*

    Global.playUrl = getData(gE(ParamId), "playurl","")
    Global.homeUrl = getData(gE(ParamId), "homeurl", "")
    Global.wpNonce = getData(gE(ParamId), "nonce", "")
    Global.pageId  = getData(gE(ParamId), "pageid", 0)

    debug(s"startView -> pageId: ${Global.pageId}")

    TourneyDB.init().map {
      case Right(_) =>
        val html = cviews.pages.html.ViewTourney(
          TourneyDB.tourney,
          CompetitionDB.competitions.toSeq.filter(_ != null),
          RoundDB.rounds.toSeq.filter(_ != null),
          ClubDB.clubs.toSeq,
          PlayerDB.players.toSeq
        )
        setHtml(gE(WordpressId), html)
        //setHtml(gE(WordpressId), "<div class='container'>Tourney Content</div>")
      case Left(err) =>
        error(s"Initialization failed in startView: ${err.msgCode}")
        setHtml(gE(WordpressId), s"<div class='alert alert-danger'>Fehler beim Laden der Daten: ${err.msgCode}</div>")
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
    import shared.model.User
    import cats.data.EitherT
    import cats.implicits._ 

    Global.playUrl = getData(gE(ParamId), "playurl","")
    Global.homeUrl = getData(gE(ParamId), "homeurl", "")
    Global.wpNonce = getData(gE(ParamId), "nonce", "")
    Global.pageId  = getData(gE(ParamId), "pageid", 0)

    debug(s"startWp -> playUrl:${Global.playUrl} homeUrl: ${Global.homeUrl} lang: ${Global.lang} nonce: ${Global.wpNonce} pageId: ${Global.pageId}")

    // init wordpress main page
    Wordpress.render("")

    (for {
      user        <- EitherT(ajaxGet[UserInfo]("/wp-json/playdemo/v1/user", List(), Map("X-WP-NONCE"->Global.wpNonce), Global.homeUrl))
      timestamp1  <- EitherT(ClubDB.load())
      timestamp3  <- EitherT(PlayerDB.load())
    } yield  (user, timestamp1, timestamp3) ).value.map {
      case Right(res)  => 
        val ui = res._1
        debug(s"User loaded: ${ui}, Clubs timestamp: ${res._2}")
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
      case Left(err)   => debug(s"Error loading user or clubs: ${err}")
    }


  def startVite(): Unit =
    setHtml(gE(ContentId), "Start successful")


  def sayHello(name: String): Unit = {
    println(s"Hallo $name")
  }


  def handleGoogleCredential(credentials: String): Unit = pages.Auth.googleLogin(credentials)

  def appLoadPage(pageName: String, param: String): Unit = 
    if (pageName == "Overview") then
      loadTourneysForOrganizer(param)
    else
      pages.loadPage(PageNameTyp(pageName), param)


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
