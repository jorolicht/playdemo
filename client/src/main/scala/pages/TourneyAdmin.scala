package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.MainIds.*
import shared.model.*
import dialogs.*
import shared.basic.Pickle.*

object TourneyAdmin extends BasePage with JsWrapper with services.ComWrapper:
  def name = PageNameTyp("TourneyAdmin")

  val BtnExportId:      HtmlId = genId(name)
  val BtnImportId:      HtmlId = genId(name)
  val BtnClickTTId:     HtmlId = genId(name)
  val RadioCertId:      HtmlId = genId(name)

  case class WpContent(rendered: String) derives ReadWriter
  case class WpTitle(rendered: String) derives ReadWriter
  case class WpPage(id: Int, parent: Int, title: WpTitle, content: WpContent) derives ReadWriter

  var templates: Seq[WpPage] = Seq.empty
  var isLoadingTemplates = false
  var templatesLoaded = false

  // ClickTT Update State
  var xmlPersons: Seq[CttPerson] = Seq.empty
  var unassignedPlayers: Seq[Player] = Seq.empty
  var playerAssignments: Map[Int, String] = Map.empty // PlayerId.value -> CttPerson.licenceNr

  private var activeTab = "IMPEXP" // Tabs: "IMPEXP" (Import/Export), "CTT" (ClickTT Update), "CERT" (Urkunden Konfiguration)

  def render(param: String = ""): Boolean =
    Global.currentSelection.tourney match
      case Some(tourney) =>
        if (param.nonEmpty && List("IMPEXP", "CTT", "CERT").contains(param.toUpperCase)) {
          activeTab = param.toUpperCase
        }

        // Fetch templates if we select "CERT" and haven't loaded them yet
        if (activeTab == "CERT" && !templatesLoaded && !isLoadingTemplates) {
          fetchTemplates()
        }

        comps.ContextHeader.render()
        setMain(cviews.pages.html.TourneyAdmin(tourney, activeTab, templates, isLoadingTemplates))

        // Wire change listener for adminImportFile
        val fileInput = dom.document.getElementById("adminImportFile").asInstanceOf[dom.html.Input]
        if (fileInput != null) {
          fileInput.onchange = (e: dom.Event) => {
            if (fileInput.files.length > 0) {
              val file = fileInput.files(0)
              val reader = new dom.FileReader()
              reader.onload = (e: dom.Event) => {
                val jsonString = reader.result.asInstanceOf[String]
                services.AdminManager.importTourney(jsonString).map {
                  case Right(slug) =>
                    dom.window.alert("Import erfolgreich! Das Turnier wird nun geladen.")
                    fileInput.value = "" // reset
                    loadPage(TourneyInfo.name, "")
                  case Left(err) =>
                    dom.window.alert(s"Fehler beim Import: ${err.msgCode}")
                    fileInput.value = "" // reset
                }
              }
              reader.readAsText(file)
            }
          }
        }

        // Wire ClickTT Update file input
        val clickttInp = dom.document.getElementById("clickttUpdateFile").asInstanceOf[dom.html.Input]
        if (clickttInp != null) {
          clickttInp.onchange = (e: dom.Event) => {
            if (clickttInp.files.length > 0) {
              val file = clickttInp.files(0)
              val reader = new dom.FileReader()
              reader.onload = (e: dom.Event) => {
                val xmlString = reader.result.asInstanceOf[String]
                services.ClickTTParser.parse(xmlString) match {
                  case Right(ctt) =>
                    xmlPersons = ctt.competitions.flatMap(_.players).flatMap(_.persons).distinctBy(_.licenceNr)
                    val activeTourney = services.TourneyDB.tourney
                    unassignedPlayers = activeTourney.players.filter(p => p != null && (p.meta.licenceNr.isEmpty || p.meta.internalNr.isEmpty)).toSeq
                    
                    // Pre-select best match
                    playerAssignments = unassignedPlayers.map { p =>
                      val bestMatch = xmlPersons.map(xp => (xp, getSimilarity(p.fullName, s"${xp.firstname} ${xp.lastname}")))
                                                 .filter(_._2 > 0.4)
                                                 .sortBy(-_._2)
                                                 .headOption
                      p.id.value -> bestMatch.map(_._1.licenceNr).getOrElse("")
                    }.toMap
                    
                    if (unassignedPlayers.isEmpty) {
                      dom.window.alert("Es wurden keine Spieler ohne ID/Lizenznummer im Turnier gefunden.")
                    } else {
                      dom.window.alert(s"Abgleich bereit! ${unassignedPlayers.length} Spieler ohne ID/Lizenznummer gefunden.")
                    }
                    render()
                  case Left(err) =>
                    dom.window.alert(s"Fehler beim Parsen der ClickTT-Datei: $err")
                }
              }
              reader.readAsText(file)
            }
          }
        }

        // Wire dynamic select events for assignments
        unassignedPlayers.foreach { p =>
          val select = dom.document.getElementById(s"select-assign-${p.id.value}").asInstanceOf[dom.html.Select]
          if (select != null) {
            select.onchange = (e: dom.Event) => {
              playerAssignments = playerAssignments + (p.id.value -> select.value)
            }
          }
        }

        // Wire save button click
        val saveBtn = dom.document.getElementById("save-assignments-btn").asInstanceOf[dom.html.Button]
        if (saveBtn != null) {
          saveBtn.onclick = (e: dom.Event) => {
            saveAssignments()
          }
        }

        true
      case None =>
        debug("TourneyAdmin: No tournament selected, redirecting to Main Search")
        loadPage(MainSearch.name, "")
        false

  private def fetchTemplates(): Unit =
    isLoadingTemplates = true
    ajaxGet[Seq[WpPage]]("/wp-json/wp/v2/pages?per_page=100", List(), host = Global.homeUrl).map {
      case Right(pages) =>
        val parentOpt = pages.find(_.title.rendered.trim.equalsIgnoreCase("templates"))
        parentOpt match {
          case Some(parentPage) =>
            templates = pages.filter(p => p.parent == parentPage.id && p.title.rendered.trim.toUpperCase.startsWith("TT"))
          case None =>
            debug("Parent page 'Templates' not found in pages list")
            templates = pages.filter(_.title.rendered.trim.toUpperCase.startsWith("TT"))
        }
        isLoadingTemplates = false
        templatesLoaded = true
        render()
      case Left(err) =>
        debug(s"Failed to fetch pages: ${err.msgCode}")
        isLoadingTemplates = false
        templatesLoaded = true
        render()
    }.recover {
      case ex =>
        debug(s"Failed to fetch pages: ${ex.getMessage}")
        isLoadingTemplates = false
        templatesLoaded = true
        render()
    }

  private def selectTemplate(templateName: String): Unit =
    Global.currentSelection.tourney.foreach { t =>
      t.certTemplate = templateName
      services.TourneyDB.update(t)
      dom.window.alert(s"Template '$templateName' erfolgreich gespeichert!")
      render()
    }

  // --- ClickTT Update Alignment Helper Methods ---

  def getBestCandidates(p: Player, persons: Seq[CttPerson]): Seq[(CttPerson, Double)] =
    persons.map(xp => (xp, getSimilarity(p.fullName, s"${xp.firstname} ${xp.lastname}")))
           .filter(_._2 > 0.25)
           .sortBy(-_._2)
           .take(5)

  private def getSimilarity(s1: String, s2: String): Double =
    val n1 = s1.trim.toLowerCase
    val n2 = s2.trim.toLowerCase
    if (n1 == n2) 1.0
    else {
      val maxLen = scala.math.max(n1.length, n2.length)
      if (maxLen == 0) 1.0
      else {
        val dist = levenshteinDistance(n1, n2)
        1.0 - (dist.toDouble / maxLen.toDouble)
      }
    }

  private def levenshteinDistance(s1: String, s2: String): Int =
    val memo = Array.fill(s1.length + 1, s2.length + 1)(-1)
    def dist(i: Int, j: Int): Int =
      if (memo(i)(j) != -1) memo(i)(j)
      else {
        val res = if (i == 0) j
        else if (j == 0) i
        else if (s1(i - 1) == s2(j - 1)) dist(i - 1, j - 1)
        else 1 + scala.math.min(dist(i - 1, j), scala.math.min(dist(i, j - 1), dist(i - 1, j - 1)))
        memo(i)(j) = res
        res
      }
    dist(s1.length, s2.length)

  private def saveAssignments(): Unit =
    val tourney = services.TourneyDB.tourney
    var updateCount = 0
    
    playerAssignments.foreach { case (playerIdVal, licenceNr) =>
      if (licenceNr.nonEmpty) {
        val targetPlayer = tourney.players.find(_.id.value == playerIdVal)
        val matchedPerson = xmlPersons.find(_.licenceNr == licenceNr)
        
        for {
          player <- targetPlayer
          xp <- matchedPerson
        } {
          val clubName = xp.clubName.getOrElse("Unbekannter Verein")
          val club = tourney.clubs.find(_.name == clubName).getOrElse {
            tourney.addClub(clubName, checkSimilarity = false, doSync = false) match {
              case Right(c) => c
              case Left(_) => Club(ClubId(0), clubName, Club.normalize(clubName))
            }
          }

          val updatedMeta = player.meta.copy(
            internalNr = Some(xp.internalNr),
            licenceNr = Some(xp.licenceNr),
            clubNr = xp.clubNr,
            clubFedNick = xp.clubFederationNickname,
            ttr = xp.ttr,
            ttrMatchCnt = xp.ttrMatchCount,
            nationality = xp.nationality,
            foreignerEqState = xp.foreignerEqState,
            region = xp.region,
            subRegion = xp.subRegion
          )

          val bYear = try Some(xp.birthyear.toInt) catch { case _: Exception => None }
          val sex = xp.sex match {
            case 1 => Sex.Male
            case 2 => Sex.Female
            case _ => Sex.Unknown
          }

          val updatedPlayer = player.copy(
            firstName = xp.firstname,
            lastName = xp.lastname,
            clubId = club.id.toInt,
            birthYear = bYear,
            sex = sex,
            meta = updatedMeta
          )
          
          tourney.updatePlayer(updatedPlayer, doSync = false)



          updateCount += 1
        }
      }
    }
    
    if (updateCount > 0) {
      services.TourneyDB.update(tourney)
      dom.window.alert(s"Erfolgreich! $updateCount Spieler wurden mit ClickTT-Daten aktualisiert.")
    } else {
      dom.window.alert("Keine Spieler zuzuordnen.")
    }
    
    // Reset state
    xmlPersons = Seq.empty
    unassignedPlayers = Seq.empty
    playerAssignments = Map.empty
    render()

  override def handleEvent(elem: HTMLElement, event: Event): Unit =
    HtmlId(elem.id) match
      case `BtnExportId` =>
        services.AdminManager.exportCurrentTourney()

      case `BtnImportId` =>
        val fileInput = dom.document.getElementById("adminImportFile").asInstanceOf[dom.html.Input]
        if (fileInput != null) {
          fileInput.value = ""
          fileInput.click()
        }

      case id if id.id.startsWith(RadioCertId.id) =>
        val suffix = elem.id.substring(RadioCertId.id.length + 1)
        if (suffix.startsWith("SETTMPL-")) {
          val templateName = suffix.substring("SETTMPL-".length)
          selectTemplate(templateName)
        }

      case _ =>
        debug(s"TourneyAdmin handleEvent: ${elem.id}")
