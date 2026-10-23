package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import shared.basic.Pickle.*
import services.*
import base.*
import dialogs.*

/**
 * MainView page component for rendering home view and loading static content pages or WP pages.
 */
object MainView extends BasePage with JsWrapper with ComWrapper:
  /** Name identifier for the page. */
  def name = PageNameTyp("MainView") 

  val BtnDemo:       HtmlId = genId(name)
  val BtnOption1:    HtmlId = genId(name)
  val BtnOption2:    HtmlId = genId(name)
  val BtnSearch:     HtmlId = genId(name)
  val StatusTourney: HtmlId = genId(name)
  val BtnLoadLast:   HtmlId = genId(name)
  /** HTML-ID for button to delete the locally stored tournament in the status bar. */
  val BtnDeleteLast: HtmlId = genId(name)
  /** HTML-ID for the news bar container below the hero section. */
  val NewsBarId:     HtmlId = genId(name)
  /** HTML-ID for the news bar content element. */
  val NewsContentId: HtmlId = genId(name)
  /** HTML-ID for the close button ('x') in the news bar. */
  val BtnCloseNews:  HtmlId = genId(name)
  
  /** Models for standard WordPress REST API response parsing. */
  case class WpContent(rendered: String) derives ReadWriter
  case class WpTitle(rendered: String) derives ReadWriter
  case class WpPage(id: Int, title: WpTitle, content: WpContent, translations: Option[Map[String, Int]] = None) derives ReadWriter

  /**
   * Data container representing information about the last tournament or competition used in the session or local storage.
   *
   * @param name Tournament or competition display name or slug.
   * @param isLocal True if the tournament is stored locally in the browser, false if hosted in the cloud.
   * @param slug Identifier slug for the tournament ("local" for offline tournaments).
   * @param isSimple True if the tournament is a simple quick-start competition (ident == "SIMPLE").
   */
  case class LastTourneyInfo(name: String, isLocal: Boolean, slug: String, isSimple: Boolean = false)

  /**
   * Inspects browser storage (sessionStorage and localStorage) to detect if a previous tournament exists.
   * Extracts the name of the last local tournament from "App.demo_tourney" or cloud tournament from "tourney_slug" / "tourney_name".
   * Also detects whether the tournament is a simple competition (ident == "SIMPLE").
   *
   * @return Option containing LastTourneyInfo if corresponding storage keys exist, None otherwise.
   */
  def getLastTourneyInfo(): Option[LastTourneyInfo] = {
    try {
      val session = org.scalajs.dom.window.sessionStorage
      val local = org.scalajs.dom.window.localStorage

      val sessionLocal = if (session != null) session.getItem("tourney_is_local_mode") == "true" else false
      val localLocal = if (local != null) local.getItem("tourney_is_local_mode") == "true" else false
      val s1 = if (session != null) session.getItem("tourney_slug") else null
      val s2 = if (local != null) local.getItem("tourney_slug") else null
      val slug = if (s1 != null && s1.nonEmpty) s1
                 else if (s2 != null && s2.nonEmpty) s2
                 else ""

      val isLocal = sessionLocal || localLocal || slug == "local" || (local != null && local.getItem("App.demo_tourney") != null && slug.isEmpty)

      if (isLocal) {
        val raw = if (local != null) local.getItem("App.demo_tourney") else null
        if (raw != null && raw.trim.nonEmpty) {
          val (parsedName, parsedIdent): (Option[String], Option[String]) = try {
            val dyn = scala.scalajs.js.JSON.parse(raw)
            val n = if (!scala.scalajs.js.isUndefined(dyn.tourney) && dyn.tourney != null) {
              if (!scala.scalajs.js.isUndefined(dyn.tourney.name) && dyn.tourney.name != null) dyn.tourney.name.toString
              else if (!scala.scalajs.js.isUndefined(dyn.tourney.tourney) && dyn.tourney.tourney != null && !scala.scalajs.js.isUndefined(dyn.tourney.tourney.name) && dyn.tourney.tourney.name != null) {
                dyn.tourney.tourney.name.toString
              } else ""
            } else if (!scala.scalajs.js.isUndefined(dyn.name) && dyn.name != null) {
              dyn.name.toString
            } else ""

            val id = if (!scala.scalajs.js.isUndefined(dyn.tourney) && dyn.tourney != null) {
              if (!scala.scalajs.js.isUndefined(dyn.tourney.ident) && dyn.tourney.ident != null) dyn.tourney.ident.toString
              else if (!scala.scalajs.js.isUndefined(dyn.tourney.tourney) && dyn.tourney.tourney != null && !scala.scalajs.js.isUndefined(dyn.tourney.tourney.ident) && dyn.tourney.tourney.ident != null) {
                dyn.tourney.tourney.ident.toString
              } else ""
            } else if (!scala.scalajs.js.isUndefined(dyn.ident) && dyn.ident != null) {
              dyn.ident.toString
            } else ""

            (if (n.nonEmpty) Some(n) else None, if (id.nonEmpty) Some(id) else None)
          } catch {
            case _: Throwable =>
              try {
                val t = read[services.TourneyDB.TourneySyncRequest](raw).tourney
                (Some(t.name), Some(t.ident))
              } catch {
                case _: Throwable =>
                  try {
                    val t = read[shared.model.Tourney](raw)
                    (Some(t.name), Some(t.ident))
                  } catch {
                    case _: Throwable => (None, None)
                  }
              }
          }
          val tourneyName = parsedName.filter(_.nonEmpty)
            .orElse(Global.currentSelection.tourney.map(_.name).filter(_.nonEmpty))
            .orElse(Option(services.TourneyDB.tourney).map(_.name).filter(_.nonEmpty))
            .getOrElse("Lokales Turnier")
          val storedIdent = if (session != null && session.getItem("tourney_ident") != null) session.getItem("tourney_ident")
                            else if (local != null && local.getItem("tourney_ident") != null) local.getItem("tourney_ident")
                            else ""
          val isSimple = parsedIdent.contains("SIMPLE") || storedIdent == "SIMPLE" ||
            Global.currentSelection.tourney.exists(_.ident == "SIMPLE") ||
            Option(services.TourneyDB.tourney).exists(_.ident == "SIMPLE")

          Some(LastTourneyInfo(tourneyName, isLocal = true, "local", isSimple = isSimple))
        } else {
          None
        }
      } else {
        if (slug.nonEmpty && slug != "local") {
          val storedName = if (session != null && session.getItem("tourney_name") != null && session.getItem("tourney_name").nonEmpty) {
            session.getItem("tourney_name")
          } else if (local != null && local.getItem("tourney_name") != null && local.getItem("tourney_name").nonEmpty) {
            local.getItem("tourney_name")
          } else ""

          val (selName, selIdent) = try {
            val selJson = if (session != null) session.getItem("tourney_last_selection") else null
            if (selJson != null && selJson.nonEmpty) {
              val sel = read[shared.model.Selection](selJson)
              (sel.tourney.map(_.name).filter(_.nonEmpty), sel.tourney.map(_.ident).filter(_.nonEmpty))
            } else (None, None)
          } catch {
            case _: Throwable => (None, None)
          }

          val storedIdent = if (session != null && session.getItem("tourney_ident") != null && session.getItem("tourney_ident").nonEmpty) {
            session.getItem("tourney_ident")
          } else if (local != null && local.getItem("tourney_ident") != null && local.getItem("tourney_ident").nonEmpty) {
            local.getItem("tourney_ident")
          } else ""

          val memName = Global.currentSelection.tourney.filter(_.slug == slug).map(_.name).filter(_.nonEmpty)
            .orElse(Option(services.TourneyDB.tourney).filter(_.slug == slug).map(_.name).filter(_.nonEmpty))
          val memIdent = Global.currentSelection.tourney.filter(_.slug == slug).map(_.ident).filter(_.nonEmpty)
            .orElse(Option(services.TourneyDB.tourney).filter(_.slug == slug).map(_.ident).filter(_.nonEmpty))

          val finalName = if (storedName.nonEmpty) storedName
            else selName.getOrElse(memName.getOrElse(slug))
          val isSimple = storedIdent == "SIMPLE" || selIdent.contains("SIMPLE") || memIdent.contains("SIMPLE")

          Some(LastTourneyInfo(finalName, isLocal = false, slug, isSimple = isSimple))
        } else {
          None
        }
      }
    } catch {
      case _: Throwable => None
    }
  }

  /**
   * Renders the page view or static content page specified by param.
   *
   * @param param Page slug, ID, or page identifier.
   * @return Boolean true on render.
   */
  def render(param: String = ""): Boolean = 
    if (param.isEmpty) {
      val lastTourney = getLastTourneyInfo()
      setMain(cviews.pages.MainView.html.Base(lastTourney))
      loadLatestHistoryEntry()
    } else {
      // 1. Loading State
      setMain(cviews.pages.MainView.html.Loading(param))

      val knownStaticPages = Set("pricing", "preise", "funktionen", "functions", "gtc", "agb", "dataprotection", "privacy", "impressum", "imprint", "contact", "kontakt", "excel", "plugin", "history", "historie")

      if (knownStaticPages.contains(param.toLowerCase)) {
        loadStaticPageFallback(param)
      } else {
        // 2. Fetch WordPress Page Content by ID or Slug
        param.toIntOption match {
          case Some(pageId) =>
            ajaxGet[WpPage](s"/wp-json/wp/v2/pages/$pageId", List(), host = Global.homeUrl).map {
              case Right(page) =>
                setMain(cviews.pages.MainView.html.PageContent(page.title.rendered, page.content.rendered))
              case Left(err) =>
                setMain(cviews.pages.MainView.html.PageNotFound(pageId.toString, err.msgCode))
            }
          case None =>
            ajaxGet[Seq[WpPage]](s"/wp-json/wp/v2/pages?slug=$param", List(), host = Global.homeUrl).map {
              case Right(pages) if pages.nonEmpty =>
                val page = pages.head
                val lang = Global.lang
                
                val translatedIdOpt = page.translations.flatMap(_.get(lang))
                
                translatedIdOpt match {
                  case Some(transId) if transId != page.id =>
                    ajaxGet[WpPage](s"/wp-json/wp/v2/pages/$transId", List(), host = Global.homeUrl).map {
                      case Right(transPage) =>
                        setMain(cviews.pages.MainView.html.PageContent(transPage.title.rendered, transPage.content.rendered))
                      case Left(_) =>
                        setMain(cviews.pages.MainView.html.PageContent(page.title.rendered, page.content.rendered))
                    }
                  case _ =>
                    setMain(cviews.pages.MainView.html.PageContent(page.title.rendered, page.content.rendered))
                }
                
              case _ =>
                loadStaticPageFallback(param)
            }
        }
      }
    }
    true

  /**
   * Fetches the history HTML page and populates the news bar with the latest (first) .history-entry.
   *
   * @param prefix Element ID prefix ("MainView" or "PureView").
   */
  def loadLatestHistoryEntry(prefix: String = "MainView"): Unit = {
    val langSuffix = if (Global.lang == "en") "_en" else ""
    val staticName = s"history$langSuffix"
    val baseName = "history"

    val baseHome = if (Global.homeUrl != null && Global.homeUrl.nonEmpty) Global.homeUrl.stripSuffix("/") else ""
    val baseData = if (Global.dataUrl != null && Global.dataUrl.nonEmpty && Global.dataUrl.contains("/wp-content/plugins/")) {
      Global.dataUrl.replaceAll("data/?$", "")
    } else ""

    val candidateUrls = List(
      if (baseData.nonEmpty) s"${baseData}pages/$staticName.html" else "",
      s"$baseHome/wp-content/plugins/tourney/pages/$staticName.html",
      s"$baseHome/wp-content/plugins/playdemo/pages/$staticName.html",
      s"/wp-content/plugins/tourney/pages/$staticName.html",
      s"/wp-content/plugins/playdemo/pages/$staticName.html",
      s"./pages/$staticName.html",
      s"/pages/$staticName.html",
      if (baseData.nonEmpty) s"${baseData}pages/$baseName.html" else "",
      s"$baseHome/wp-content/plugins/tourney/pages/$baseName.html",
      s"/wp-content/plugins/tourney/pages/$baseName.html",
      s"./pages/$baseName.html"
    ).filter(_.nonEmpty).distinct

    def tryFetchHistory(urls: List[String]): Unit = urls match {
      case Nil => ()
      case head :: tail =>
        val cacheBustUrl = if (head.contains("?")) s"$head&_t=${dom.window.performance.now().toLong}" else s"$head?_t=${dom.window.performance.now().toLong}"
        org.scalajs.dom.fetch(cacheBustUrl).`then`[Unit] { resp =>
          if (resp.ok) {
            resp.text().`then`[Unit] { htmlText =>
              try {
                val tempDiv = dom.document.createElement("div")
                tempDiv.innerHTML = htmlText
                val firstEntry = tempDiv.querySelector(".history-entry")
                if (firstEntry != null) {
                  val version = Option(firstEntry.getAttribute("data-version")).filter(_.nonEmpty)
                    .orElse(Option(firstEntry.querySelector(".history-version")).map(_.textContent.trim))
                    .getOrElse("")
                  val date = Option(firstEntry.getAttribute("data-date")).filter(_.nonEmpty)
                    .orElse(Option(firstEntry.querySelector(".history-date")).map(_.textContent.trim))
                    .getOrElse("")
                  val summary = Option(firstEntry.getAttribute("data-summary")).filter(_.nonEmpty)
                    .orElse(Option(firstEntry.querySelector(".history-summary")).map(_.textContent.trim))
                    .getOrElse("")

                  val vElem = dom.document.getElementById(s"${prefix}_NewsVersion")
                  if (vElem != null && version.nonEmpty) {
                    vElem.textContent = if (version.startsWith("v") || version.toLowerCase.startsWith("version")) version else s"v$version"
                  }
                  val dElem = dom.document.getElementById(s"${prefix}_NewsDate")
                  if (dElem != null && date.nonEmpty) {
                    dElem.textContent = s"($date)"
                  }
                  val sElem = dom.document.getElementById(s"${prefix}_NewsSummary")
                  if (sElem != null && summary.nonEmpty) {
                    sElem.textContent = summary
                  }
                }
              } catch {
                case _: Throwable => ()
              }
              ()
            }
          } else {
            tryFetchHistory(tail)
          }
        }.`catch` { _ =>
          tryFetchHistory(tail)
        }
    }

    tryFetchHistory(candidateUrls)
  }

  /**
   * Fetches static HTML content files with cache-busting query parameter.
   *
   * @param param The page slug or identifier.
   */
  private def loadStaticPageFallback(param: String): Unit =
    val baseName = param.toLowerCase match {
      case "functions" | "funktionen"           => "funktionen"
      case "agb" | "gtc"                        => "gtc"
      case "privacy" | "dataprotection"         => "privacy"
      case "impressum" | "imprint"              => "impressum"
      case "contact" | "kontakt"                => "contact"
      case "pricing" | "preise"                 => "pricing"
      case "history" | "historie"               => "history"
      case other                                => other
    }
    val langSuffix = if (Global.lang == "en") "_en" else ""
    val staticName = s"$baseName$langSuffix"

    val baseHome = if (Global.homeUrl != null && Global.homeUrl.nonEmpty) Global.homeUrl.stripSuffix("/") else ""
    val baseData = if (Global.dataUrl != null && Global.dataUrl.nonEmpty && Global.dataUrl.contains("/wp-content/plugins/")) {
      Global.dataUrl.replaceAll("data/?$", "")
    } else ""

    val candidateUrls = List(
      if (baseData.nonEmpty) s"${baseData}pages/$staticName.html" else "",
      s"$baseHome/wp-content/plugins/tourney/pages/$staticName.html",
      s"$baseHome/wp-content/plugins/playdemo/pages/$staticName.html",
      s"/wp-content/plugins/tourney/pages/$staticName.html",
      s"/wp-content/plugins/playdemo/pages/$staticName.html",
      s"./pages/$staticName.html",
      s"/pages/$staticName.html",
      if (baseData.nonEmpty) s"${baseData}pages/$baseName.html" else "",
      s"$baseHome/wp-content/plugins/tourney/pages/$baseName.html",
      s"/wp-content/plugins/tourney/pages/$baseName.html",
      s"./pages/$baseName.html"
    ).filter(_.nonEmpty).distinct

    def tryFetch(urls: List[String]): Unit = urls match {
      case Nil =>
        setMain(cviews.pages.MainView.html.PageNotFound(param, s"Keine Seite mit diesem Slug gefunden (404)."))
      case head :: tail =>
        val cacheBustUrl = if (head.contains("?")) s"$head&_t=${dom.window.performance.now().toLong}" else s"$head?_t=${dom.window.performance.now().toLong}"
        org.scalajs.dom.fetch(cacheBustUrl).`then`[Unit] { resp =>
          if (resp.ok) {
            resp.text().`then`[Unit] { text =>
              val pageTitle = baseName match {
                case "funktionen"                   => if (Global.lang == "en") "Features & Functions" else "Funktionen & Features"
                case "gtc"                          => if (Global.lang == "en") "Terms and Conditions (AGB)" else "Allgemeine Geschäftsbedingungen"
                case "excel"                        => if (Global.lang == "en") "Excel Downloads & Software" else "Excel-Downloads & Software"
                case "plugin"                       => "WordPress Plugin"
                case "privacy" | "dataprotection"   => if (Global.lang == "en") "Privacy Policy" else "Datenschutzerklärung"
                case "impressum" | "imprint"        => if (Global.lang == "en") "Imprint" else "Impressum"
                case "contact" | "kontakt"          => if (Global.lang == "en") "Contact" else "Kontakt"
                case "pricing" | "preise"           => if (Global.lang == "en") "Support & Pricing" else "Support & Kosten"
                case "history"                      => if (Global.lang == "en") "Version History" else "Versionshistorie"
                case _                              => staticName.capitalize
              }
              setMain(cviews.pages.MainView.html.PageContent(pageTitle, text))
              ()
            }
          } else {
            tryFetch(tail)
          }
        }.`catch` { _ =>
          tryFetch(tail)
        }
    }

    tryFetch(candidateUrls)

  /**
   * Event handler for button clicks and interactions on MainView.
   *
   * @param elem Target HTML element.
   * @param event DOM event trigger.
   */
  override def handleEvent(elem: HTMLElement, event: Event): Unit = 
    HtmlId(elem.id) match
      case `BtnDemo` =>
        services.StartManager.start(services.StartKind.Demo)
      case `BtnCloseNews` =>
        addClass(gE(NewsBarId), "d-none")
      case `BtnLoadLast` | `StatusTourney` =>
        comps.Navbar.doReloadTourney()
      case `BtnDeleteLast` =>
        import shared.BoxButton
        val isSimple = getLastTourneyInfo().exists(_.isSimple)
        val msg = if (isSimple) "Möchten Sie diesen Wettbewerb wirklich unwiderruflich löschen?"
                  else "Möchten Sie dieses Turnier wirklich unwiderruflich löschen?"
        val title = if (isSimple) "Wettbewerb löschen" else "Turnier löschen"
        DlgMsgbox.show(msg, title, List(BoxButton.Yes, BoxButton.No)).map {
          case BoxButton.Yes =>
            services.TourneyDB.purgeAllTourneyStorage(999999)
            comps.ContextHeader.hide()
            comps.Navbar.render()
            render()
          case _ => debug("Delete local tourney cancelled")
        }
      case `BtnOption1` =>
        services.StartManager.start(services.StartKind.Tourney)
      case `BtnOption2` =>
        services.StartManager.start(services.StartKind.Competition)
      case `BtnSearch` => loadPage(MainSearch.name, "")
      case _ => debug(s"MainView handleEvent: ${elem.id}")
