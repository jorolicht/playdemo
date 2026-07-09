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
  var parsedCtt: Option[CttTournament] = None
  private var lastCttXmlString: String = ""

  private var activeTab = "IMPEXP" // Tabs: "IMPEXP" (Import/Export), "CTT" (ClickTT Update), "CERT" (Urkunden Konfiguration)

  def render(param: String = ""): Boolean =
    Global.currentSelection.tourney match
      case Some(tourney) =>
        if (param.nonEmpty && List("IMPEXP", "CTT", "CERT").contains(param.toUpperCase)) {
          activeTab = param.toUpperCase
        }

        // Fetch templates if we select "CERT" and haven't loaded them yet
        if (parsedCtt.isEmpty && tourney.clicktt != null && tourney.clicktt.trim.nonEmpty) {
          services.ClickTTParser.parse(tourney.clicktt) match {
            case Right(ctt) =>
              parsedCtt = Some(ctt)
              xmlPersons = ctt.competitions.flatMap(_.players).flatMap(_.persons).distinctBy(_.licenceNr)
              unassignedPlayers = tourney.players.filter(p => p != null && (p.meta.licenceNr.isEmpty || p.meta.internalNr.isEmpty)).toSeq
              
              // Pre-select best match
              playerAssignments = unassignedPlayers.map { p =>
                val existingLicence = p.meta.licenceNr.getOrElse("").trim
                val bestLicence = if (existingLicence.nonEmpty) {
                  existingLicence
                } else {
                  val bestMatch = xmlPersons.map(xp => (xp, getSimilarity(p.fullName, s"${xp.firstname} ${xp.lastname}")))
                                             .filter(_._2 > 0.4)
                                             .sortBy(-_._2)
                                             .headOption
                  bestMatch.map(_._1.licenceNr).getOrElse("")
                }
                p.id.value -> bestLicence
              }.toMap
            case Left(_) =>
          }
        }

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
                lastCttXmlString = xmlString
                services.ClickTTParser.parse(xmlString) match {
                  case Right(ctt) =>
                    parsedCtt = Some(ctt)
                    xmlPersons = ctt.competitions.flatMap(_.players).flatMap(_.persons).distinctBy(_.licenceNr)
                    val activeTourney = services.TourneyDB.tourney
                    unassignedPlayers = activeTourney.players.filter(p => p != null && (p.meta.licenceNr.isEmpty || p.meta.internalNr.isEmpty)).toSeq
                    
                    // Pre-select best match
                    playerAssignments = unassignedPlayers.map { p =>
                      val existingLicence = p.meta.licenceNr.getOrElse("").trim
                      val bestLicence = if (existingLicence.nonEmpty) {
                        existingLicence
                      } else {
                        val bestMatch = xmlPersons.map(xp => (xp, getSimilarity(p.fullName, s"${xp.firstname} ${xp.lastname}")))
                                                   .filter(_._2 > 0.4)
                                                   .sortBy(-_._2)
                                                   .headOption
                        bestMatch.map(_._1.licenceNr).getOrElse("")
                      }
                      p.id.value -> bestLicence
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

        // Wire ClickTT Results Generator Button
        val generateBtn = dom.document.getElementById("generate-ctt-results-btn").asInstanceOf[dom.html.Button]
        if (generateBtn != null) {
          generateBtn.onclick = (e: dom.Event) => {
            if (tourney.clicktt == null || tourney.clicktt.trim.isEmpty) {
              dom.window.alert("Keine ClickTT XML-Datei im Turnier vorhanden. Bitte laden Sie zuerst eine ClickTT XML-Datei unter 'ClickTT Update' hoch.")
            } else {
              // Find all players missing a ClickTT ID mapping
              val missingPlayers = tourney.competitions.filter(_ != null).flatMap { comp =>
                comp.pants1Stage
                  .filter(p => p != null && !p.id.isBye && !p.id.isNN)
                  .filter(p => !comp.cttSNO2Ident.contains(p.id) || comp.cttSNO2Ident(p.id).trim.isEmpty)
                  .map(p => (comp.name, p.name))
              }.distinct

              val warningBox = dom.document.getElementById("ctt-export-warning-box").asInstanceOf[dom.html.Div]
              val warningList = dom.document.getElementById("ctt-export-warning-players-list").asInstanceOf[dom.html.UList]
              if (warningBox != null && warningList != null) {
                if (missingPlayers.nonEmpty) {
                  warningBox.style.display = "block"
                  val listItems = missingPlayers.map { case (compName, pName) =>
                    s"<li>$pName ($compName)</li>"
                  }.mkString("\n")
                  warningList.innerHTML = listItems
                } else {
                  warningBox.style.display = "none"
                }
              }

              // Show dynamic list of competitions and stages
              val selectionContainer = dom.document.getElementById("ctt-export-selection").asInstanceOf[dom.html.Div]
              val listContainer = dom.document.getElementById("ctt-export-competitions-list").asInstanceOf[dom.html.Div]
              if (selectionContainer != null && listContainer != null) {
                selectionContainer.style.display = "block"
                
                // Construct HTML list of competitions and checkboxes for stages
                val listHtml = new StringBuilder()
                tourney.competitions.filter(_ != null).foreach { comp =>
                  val compStages = tourney.stages.filter(s => s != null && s.coId == comp.id)
                  if (compStages.nonEmpty) {
                    listHtml.append(s"""<div class="mb-3 border-bottom pb-2">""")
                    listHtml.append(s"""  <div class="fw-bold text-dark mb-2">${comp.name}</div>""")
                    listHtml.append(s"""  <div class="ms-3 d-flex flex-wrap gap-3">""")
                    compStages.foreach { stage =>
                      listHtml.append(s"""
                        <div class="form-check">
                          <input class="form-check-input ctt-stage-checkbox" type="checkbox" id="chk-stage-${stage.id.value}" data-comp-id="${comp.id.value}" data-stage-id="${stage.id.value}" checked>
                          <label class="form-check-label text-muted small fw-semibold" for="chk-stage-${stage.id.value}">${stage.name}</label>
                        </div>
                      """)
                    }
                    listHtml.append(s"""  </div>""")
                    listHtml.append(s"""</div>""")
                  }
                }
                
                if (listHtml.isEmpty) {
                  listContainer.innerHTML = "<div class='text-muted small'>Keine aktiven Wettbewerbsphasen (Stages) gefunden.</div>"
                } else {
                  listContainer.innerHTML = listHtml.toString()
                }
              }
            }
          }
        }

        // Wire ClickTT Download XML button
        val downloadBtn = dom.document.getElementById("download-ctt-results-btn").asInstanceOf[dom.html.Button]
        if (downloadBtn != null) {
          downloadBtn.onclick = (e: dom.Event) => {
            generateCttResultsXml(tourney)
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
    val existingLicence = p.meta.licenceNr.getOrElse("").trim
    val matchedPersonOpt = if (existingLicence.nonEmpty) persons.find(_.licenceNr == existingLicence) else None
    
    val baseCandidates = persons.map(xp => (xp, getSimilarity(p.fullName, s"${xp.firstname} ${xp.lastname}")))
                                .filter(_._2 > 0.25)
                                .sortBy(-_._2)
                                .take(5)
                                
    matchedPersonOpt match {
      case Some(mp) =>
        val filtered = baseCandidates.filterNot(_._1.licenceNr == mp.licenceNr)
        (mp, 1.0) +: filtered
      case None =>
        baseCandidates
    }

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
    
    // Step 2: Update Competition.cttSNO2Ident
    parsedCtt.foreach { ctt =>
      tourney.competitions.filter(_ != null).foreach { comp =>
        val cttCompOpt = comp.cttInfo.flatMap { info =>
          ctt.competitions.find { cc =>
            cc.ageGroup == info.ageGroup &&
            cc.ttrRemarks.getOrElse("") == info.ratingRemark &&
            cc.ttrFrom.getOrElse(0) == info.ratingLowLevel &&
            cc.ttrTo.getOrElse(0) == info.ratingUpperLevel &&
            cc.sex.getOrElse(0) == info.sex
          }
        }.orElse(ctt.competitions.lift(comp.id.value - 1))

        // Clear existing mappings to completely rebuild them
        comp.cttSNO2Ident.clear()
        
        comp.pants1Stage.filter(p => p != null && !p.id.isBye && !p.id.isNN).foreach { p =>
          val licenceNrsOpt = if (p.id.isSingle) {
            p.id.singleIdOpt.flatMap(pid => tourney.players.find(_.id == pid)).flatMap(_.meta.licenceNr).map(lic => Seq(lic))
          } else if (p.id.isDouble) {
            p.id.doubleIdsOpt.flatMap { case (pid1, pid2) =>
              for {
                pl1 <- tourney.players.find(_.id == pid1)
                pl2 <- tourney.players.find(_.id == pid2)
                lic1 <- pl1.meta.licenceNr
                lic2 <- pl2.meta.licenceNr
              } yield Seq(lic1, lic2)
            }
          } else {
            None
          }

          licenceNrsOpt.foreach { licenceNrs =>
            val matchedCttPlayerOpt = cttCompOpt.flatMap { cttComp =>
              if (licenceNrs.length == 1) {
                val lic = licenceNrs.head
                cttComp.players.find(cp => cp.persons.length == 1 && cp.persons.head.licenceNr == lic)
              } else if (licenceNrs.length == 2) {
                val lic1 = licenceNrs(0)
                val lic2 = licenceNrs(1)
                cttComp.players.find(cp => cp.persons.length == 2 && 
                                           cp.persons.exists(_.licenceNr == lic1) && 
                                           cp.persons.exists(_.licenceNr == lic2))
              } else {
                None
              }
            }.orElse {
              // Global fallback
              if (licenceNrs.length == 1) {
                val lic = licenceNrs.head
                ctt.competitions.flatMap(_.players)
                  .find(cp => cp.persons.length == 1 && cp.persons.head.licenceNr == lic)
              } else if (licenceNrs.length == 2) {
                val lic1 = licenceNrs(0)
                val lic2 = licenceNrs(1)
                ctt.competitions.flatMap(_.players)
                  .find(cp => cp.persons.length == 2 && 
                              cp.persons.exists(_.licenceNr == lic1) && 
                              cp.persons.exists(_.licenceNr == lic2))
              } else {
                None
              }
            }

            matchedCttPlayerOpt.foreach { cttPlayer =>
              comp.cttSNO2Ident(p.id) = cttPlayer.id
            }
          }
        }
        
        // Re-save the competition
        tourney.updateCompetition(comp, doSync = false)

        // Testwise Ausgabe aller cttSNO2Ident mappings
        dom.console.log(s"Wettbewerb: ${comp.name} - cttSNO2Ident Mappings:")
        comp.cttSNO2Ident.foreach { case (sno, xmlId) =>
          dom.console.log(s"  $sno -> $xmlId")
        }
      }
    }
    
    // Store the loaded XML string in the tourney clicktt metadata field
    if (lastCttXmlString.nonEmpty) {
      tourney.clicktt = lastCttXmlString
    }
    
    // Always trigger syncs and update tournament on save click
    tourney.triggerAllSyncs()
    services.TourneyDB.update(tourney)
    dom.window.alert("Erfolgreich! Die ClickTT-Zuordnung und alle Mappings wurden aktualisiert.")
    
    // Reset state
    xmlPersons = Seq.empty
    unassignedPlayers = Seq.empty
    playerAssignments = Map.empty
    parsedCtt = None
    lastCttXmlString = ""
    render()

  private def generateCttResultsXml(tourney: Tourney): Unit = {
    if (tourney.clicktt == null || tourney.clicktt.trim.isEmpty) {
      dom.window.alert("Fehler: Keine ClickTT XML-Datei vorhanden.")
      return
    }

    try {
      // 1. Parse XML String using DOMParser
      val parser = new dom.DOMParser()
      val doc = parser.parseFromString(tourney.clicktt, dom.MIMEType.`application/xml`)
      val xmlComps = doc.getElementsByTagName("competition")

      // 2. Read selected stage checkboxes
      val checkboxes = dom.document.querySelectorAll(".ctt-stage-checkbox")
      val selectedStages = scala.collection.mutable.Map[Int, scala.collection.mutable.ListBuffer[Int]]()
      
      for (i <- 0 until checkboxes.length) {
        val cb = checkboxes.item(i).asInstanceOf[dom.html.Input]
        if (cb.checked) {
          val compId = cb.getAttribute("data-comp-id").toInt
          val stageId = cb.getAttribute("data-stage-id").toInt
          selectedStages.getOrElseUpdate(compId, scala.collection.mutable.ListBuffer()).append(stageId)
        }
      }

      // 3. Populate matches in each competition
      tourney.competitions.filter(_ != null).foreach { comp =>
        val selectedStageIds = selectedStages.get(comp.id.value).map(_.toSeq).getOrElse(Seq.empty)
        if (selectedStageIds.nonEmpty) {
          val xmlComp = xmlComps.item(comp.id.value - 1).asInstanceOf[dom.Element]
          if (xmlComp != null) {
            // Remove existing <matches> element direct children to avoid duplicates
            val childNodes = xmlComp.childNodes
            var i = 0
            while (i < childNodes.length) {
              val node = childNodes.item(i)
              if (node.nodeName == "matches") {
                xmlComp.removeChild(node)
              } else {
                i += 1
              }
            }

            // Create new <matches> element
            val xmlMatches = doc.createElement("matches")
            var matchCounter = 0

            selectedStageIds.foreach { stageId =>
              val stageOpt = tourney.stages.find(s => s != null && s.id.value == stageId)
              stageOpt.foreach { stage =>
                val playedMatches = stage.matches.filter(m => m.finished && !m.stNoA.isBye && !m.stNoB.isBye && !m.stNoA.isNN && !m.stNoB.isNN)
                playedMatches.foreach { m =>
                  val xmlMatch = doc.createElement("match")
                  xmlMatch.setAttribute("nr", matchCounter.toString)
                  
                  val groupVal = if (m.stageFormat == StageFormat.GR) {
                    m match {
                      case gr: MEntryGr => "Gruppe " + ('A'.toInt + gr.grId - 1).toChar.toString
                      case _ => ""
                    }
                  } else {
                    ""
                  }
                  xmlMatch.setAttribute("group", groupVal)
                  xmlMatch.setAttribute("scheduled", "")

                  val playerAId = comp.cttSNO2Ident.get(m.stNoA).getOrElse("")
                  val playerBId = comp.cttSNO2Ident.get(m.stNoB).getOrElse("")
                  xmlMatch.setAttribute("player-a", playerAId)
                  xmlMatch.setAttribute("player-b", playerBId)

                  val balls = m.getBalls
                  for (setIdx <- 1 to 7) {
                    if (setIdx <= balls.length) {
                      val b = balls(setIdx - 1)
                      xmlMatch.setAttribute(s"set-a-$setIdx", b._1.toString)
                      xmlMatch.setAttribute(s"set-b-$setIdx", b._2.toString)
                    } else {
                      xmlMatch.setAttribute(s"set-a-$setIdx", "0")
                      xmlMatch.setAttribute(s"set-b-$setIdx", "0")
                    }
                  }

                  xmlMatch.setAttribute("sets-a", m.sets._1.toString)
                  xmlMatch.setAttribute("sets-b", m.sets._2.toString)

                  val matchesA = if (m.sets._1 > m.sets._2) "1" else "0"
                  val matchesB = if (m.sets._2 > m.sets._1) "1" else "0"
                  xmlMatch.setAttribute("matches-a", matchesA)
                  xmlMatch.setAttribute("matches-b", matchesB)

                  val gamesA = balls.map(_._1).filter(_ > 0).sum
                  val gamesB = balls.map(_._2).filter(_ > 0).sum
                  xmlMatch.setAttribute("games-a", gamesA.toString)
                  xmlMatch.setAttribute("games-b", gamesB.toString)

                  xmlMatches.appendChild(xmlMatch)
                  matchCounter += 1
                }
              }
            }

            // Insert <matches> element after <players>
            val playersElems = xmlComp.getElementsByTagName("players")
            if (playersElems.length > 0) {
              val playersElem = playersElems.item(0)
              val nextSibling = playersElem.nextSibling
              if (nextSibling != null) {
                xmlComp.insertBefore(xmlMatches, nextSibling)
              } else {
                xmlComp.appendChild(xmlMatches)
              }
            } else {
              xmlComp.appendChild(xmlMatches)
            }
          }
        }
      }

      // 4. Serialize back to XML String
      val serializer = new dom.XMLSerializer()
      val xmlResult = serializer.serializeToString(doc)

      // 5. Trigger download
      val blob = new dom.Blob(scala.scalajs.js.Array(xmlResult), dom.BlobPropertyBag(`type` = "application/xml"))
      val url = dom.URL.createObjectURL(blob)
      val a = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
      a.href = url
      a.download = s"clicktt_export_ergebnisse_${tourney.wpId}.xml"
      dom.document.body.appendChild(a)
      a.click()
      dom.document.body.removeChild(a)
      dom.URL.revokeObjectURL(url)

    } catch {
      case ex: Throwable =>
        dom.window.alert(s"Fehler beim Erzeugen der ClickTT XML-Ergebnisdatei: ${ex.getMessage}")
    }
  }

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
