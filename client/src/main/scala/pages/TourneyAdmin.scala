package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.Event
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.MainIds.*
import shared.model.*
import dialogs.*
import shared.basic.Pickle.*

object TourneyAdmin extends BasePage with JsWrapper with services.ComWrapper:
  def name = PageNameTyp("TourneyAdmin")

  val BtnClickTTId:     HtmlId = genId(name)
  val RadioCertId:      HtmlId = genId(name)

  case class WpContent(rendered: String) derives ReadWriter
  case class WpTitle(rendered: String) derives ReadWriter
  case class WpPage(id: Int, parent: Int, title: WpTitle, content: WpContent) derives ReadWriter

  var templates: Seq[WpPage] = Seq.empty
  var isLoadingTemplates = false
  var templatesLoaded = false

  var socketMessages: List[String] = List.empty

  def addMessage(msg: String): Unit = {
    socketMessages = socketMessages :+ msg
    val container = dom.document.getElementById("ws-message-log")
    if (container != null) {
      val item = dom.document.createElement("div").asInstanceOf[dom.html.Div]
      val isSent = msg.startsWith("[Gesendet]")
      item.className = if (isSent) "mb-2 text-end" else "mb-2 text-start"
      
      val badgeClass = if (isSent) "badge bg-primary text-white" else "badge bg-success text-white"
      val textContent = msg.substring(msg.indexOf(" ") + 1)
      val prefix = if (isSent) "Gesendet" else "Empfangen"
      
      item.innerHTML = s"""
        <span class="$badgeClass">$prefix</span>
        <div class="d-inline-block bg-white border rounded p-2 ms-2 me-2" style="max-width: 75%; text-align: left; word-break: break-all;">
          $textContent
        </div>
      """
      container.appendChild(item)
      container.scrollTop = container.scrollHeight
    }
  }

  private var activeTab = "CERT" // Tabs: "CERT", "MESSAGES", "CHANGELOG", "MATCHBOARD", "HOMEPAGE", "DATEFORMAT"

  /** Multiuser: zuletzt geladenes Änderungsprotokoll (neueste Einträge zuerst), Reiter "CHANGELOG". */
  var changelog: Seq[services.MultiUser.ChangeLogEntry] = Seq.empty

  /**
   * Zeitpunkt eines Protokolleintrags für die Anzeige.
   *
   * @param ts Zeitpunkt "JJJJ-MM-TT hh:mm:ss"
   * @return "TT.MM.JJJJ hh:mm:ss", bei unbekanntem Format unverändert
   */
  def formatTs(ts: String): String =
    ts match
      case s"$y-$m-$d $time" => s"$d.$m.$y $time"
      case other             => other

  /** Lädt das Änderungsprotokoll und zeigt den Reiter erneut an. */
  private def fetchChangelog(): Unit =
    services.MultiUser.loadChangelog().map {
      case Right(entries) =>
        changelog = entries
        if (Global.activePageName == name.value && activeTab == "CHANGELOG") render()
      case Left(err) =>
        error(s"TourneyAdmin: Änderungsprotokoll konnte nicht geladen werden: ${err.msgCode}")
    }

  /**
   * Renders the tournament administration page for the currently selected tournament.
   * For standalone competitions (ident == "SIMPLE"), the Homepage ("HOMEPAGE") tab is hidden and falls back to "CERT".
   * ClickTT (Import/Update/Export) liegt in der Turnierübersicht, siehe [[ClickTTPanel]].
   *
   * @param param The optional tab name to activate.
   * @return True if a tournament is loaded and rendered, false otherwise.
   */
  def render(param: String = ""): Boolean =
    Global.currentSelection.tourney match
      case Some(tourney) =>
        val tabParam = param.nonEmpty && List("CERT", "MESSAGES", "CHANGELOG", "MATCHBOARD", "HOMEPAGE", "DATEFORMAT").contains(param.toUpperCase)
        if (tabParam) {
          activeTab = param.toUpperCase
        }
        if (activeTab == "CHANGELOG" && !services.MultiUser.enabled) {
          activeTab = "CERT"
        }
        // Protokoll beim Öffnen des Reiters (Aufruf mit Parameter) neu laden
        if (activeTab == "CHANGELOG" && tabParam) {
          fetchChangelog()
        }
        if (tourney.ident == "SIMPLE" && activeTab == "HOMEPAGE") {
          activeTab = "CERT"
        }
        if (!Global.currentAci.showMessagesTab && activeTab == "MESSAGES") {
          activeTab = "CERT"
        }

        if (activeTab == "CERT" && !templatesLoaded && !isLoadingTemplates) {
          fetchTemplates()
        }

        comps.ContextHeader.render()
        setMain(cviews.pages.html.TourneyAdmin(tourney, activeTab, templates, isLoadingTemplates))

        if (activeTab == "HOMEPAGE") {
          val ta = dom.document.getElementById("homepage-info-textarea").asInstanceOf[dom.html.TextArea]
          val cb = dom.document.getElementById("allow-registration-checkbox").asInstanceOf[dom.html.Input]
          val hintTa = dom.document.getElementById("registration-hint-textarea").asInstanceOf[dom.html.TextArea]
          val btn = dom.document.getElementById("homepage-save-btn").asInstanceOf[dom.html.Button]
          
          if (ta != null) ta.value = Global.currentAci.homepageInfo
          if (cb != null) cb.checked = Global.currentAci.allowRegistration
          if (hintTa != null) hintTa.value = dialogs.DlgPublicRegistration.registrationHint
          
          if (btn != null) {
            btn.onclick = (e: dom.Event) => {
              val updatedAci = Global.currentAci.copy(
                homepageInfo = ta.value,
                allowRegistration = cb.checked,
                registrationHint = Some(hintTa.value.trim)
              )
              services.TourneyDB.saveAci(tourney.wpId, updatedAci).map {
                case Right(_) =>
                  dom.window.alert("Homepage-Einstellungen erfolgreich gespeichert.")
                  render()
                case Left(err) =>
                  dialogs.DlgMsgbox.showError(err, "Fehler beim Speichern")
              }
            }
          }
        }
        
        if (activeTab == "DATEFORMAT") {
          val btn = dom.document.getElementById("dateformat-save-btn").asInstanceOf[dom.html.Button]
          val deRadio = dom.document.getElementById("date-format-de").asInstanceOf[dom.html.Input]
          val ukRadio = dom.document.getElementById("date-format-uk").asInstanceOf[dom.html.Input]
          val usRadio = dom.document.getElementById("date-format-us").asInstanceOf[dom.html.Input]
          val msgCb = dom.document.getElementById("show-messages-tab-cb").asInstanceOf[dom.html.Input]
          
          if (deRadio != null && ukRadio != null && usRadio != null) {
            Global.currentAci.dateFormat match {
              case "UK" => ukRadio.checked = true
              case "US" => usRadio.checked = true
              case _ => deRadio.checked = true
            }
          }
          if (msgCb != null) {
            msgCb.checked = Global.currentAci.showMessagesTab
            msgCb.onchange = (_: dom.Event) => {
              val updatedAci = Global.currentAci.copy(showMessagesTab = msgCb.checked)
              services.TourneyDB.saveAci(tourney.wpId, updatedAci).map {
                case Right(_) =>
                  render()
                case Left(err) =>
                  dialogs.DlgMsgbox.showError(err, "Fehler beim Speichern")
              }
            }
          }
          
          if (btn != null) {
            btn.onclick = (e: dom.Event) => {
              val selectedFormat = if (deRadio != null && deRadio.checked) "DE"
                                   else if (ukRadio != null && ukRadio.checked) "UK"
                                   else if (usRadio != null && usRadio.checked) "US"
                                   else "DE"
              val showMessages = if (msgCb != null) msgCb.checked else Global.currentAci.showMessagesTab
              val updatedAci = Global.currentAci.copy(dateFormat = selectedFormat, showMessagesTab = showMessages)
              services.TourneyDB.saveAci(tourney.wpId, updatedAci).map {
                case Right(_) =>
                  dom.window.alert("Einstellungen erfolgreich gespeichert.")
                  render()
                case Left(err) =>
                  dialogs.DlgMsgbox.showError(err, "Fehler beim Speichern")
              }
            }
          }
        }

        // Wire WebSocket messaging in TourneyAdmin
        services.WebSocketService.setListener { msg =>
          addMessage(s"[Empfangen] $msg")
        }

        if (activeTab == "MESSAGES") {
          val sendBtn = dom.document.getElementById("ws-send-btn").asInstanceOf[dom.html.Button]
          val inputEl = dom.document.getElementById("ws-message-input").asInstanceOf[dom.html.Input]
          if (sendBtn != null && inputEl != null) {
            sendBtn.onclick = (e: dom.Event) => {
              val text = inputEl.value.trim
              if (text.nonEmpty) {
                services.WebSocketService.send(text)
                addMessage(s"[Gesendet] $text")
                inputEl.value = ""
              }
            }
            inputEl.onkeypress = (e: dom.KeyboardEvent) => {
              if (e.key == "Enter") {
                sendBtn.click()
              }
            }
          }

          // Scroll message log to bottom after rendering
          dom.window.setTimeout(() => {
            val container = dom.document.getElementById("ws-message-log")
            if (container != null) {
              container.scrollTop = container.scrollHeight
            }
          }, 50)
        }

        if (activeTab == "MATCHBOARD") {
          // Dynamic window bindings for raw HTML callbacks
          val dynWin = dom.window.asInstanceOf[js.Dynamic]
          dynWin.toggleMatchboardFormFields = () => {
            val selectEl = dom.document.getElementById("mb-entry-type-select").asInstanceOf[dom.html.Select]
            val infoFields = dom.document.getElementById("mb-info-fields").asInstanceOf[dom.html.Div]
            val matchFields = dom.document.getElementById("mb-match-fields").asInstanceOf[dom.html.Div]
            if (selectEl != null && infoFields != null && matchFields != null) {
              if (selectEl.value == "info") {
                infoFields.style.display = "block"
                matchFields.style.display = "none"
              } else {
                infoFields.style.display = "none"
                matchFields.style.display = "block"
              }
            }
          }

          dynWin.appTourneyAdminDeleteMatchboardEntry = (id: String) => {
            deleteMatchboardEntry(id)
          }

          fetchMatchboard()

          val addBtn = dom.document.getElementById("mb-add-btn").asInstanceOf[dom.html.Button]
          if (addBtn != null) {
            addBtn.onclick = (e: dom.Event) => {
              addMatchboardEntry()
            }
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

  override def handleEvent(elem: HTMLElement, event: Event): Unit =
    HtmlId(elem.id) match
      case id if id.id.startsWith(RadioCertId.id) =>
        val suffix = elem.id.substring(RadioCertId.id.length + 1)
        if (suffix.startsWith("SETTMPL-")) {
          val templateName = suffix.substring("SETTMPL-".length)
          selectTemplate(templateName)
        }

      case _ =>
        debug(s"TourneyAdmin handleEvent: ${elem.id}")

  private var matchboard: Option[shared.model.Matchboard] = None

  private def fetchMatchboard(): Unit =
    Global.currentSelection.tourney.foreach { t =>
      val slugName = t.slug.split('/').lastOption.getOrElse(t.slug)
      val slugParam = s"${slugName.trim}-${t.wpId}"
      
      ajaxPost[String, shared.model.Matchboard]("/matchboard/get", List("slug" -> slugParam), "").map {
        case Right(mb) =>
          matchboard = Some(mb)
          renderMatchboardEntries()
        case Left(err) =>
          debug(s"Failed to fetch matchboard: ${err.msgCode}")
      }
    }

  private def renderMatchboardEntries(): Unit =
    val tbody = dom.document.getElementById("mb-entries-tbody")
    if (tbody != null) {
      matchboard match {
        case Some(mb) if mb.entries.nonEmpty =>
          val html = mb.entries.map { entry =>
            val isMatch = entry.entryType == "start" || entry.entryType == "match"
            val typeText = if (isMatch) "Spiel" else "Info"
            val typeClass = if (isMatch) "badge bg-primary text-white" else "badge bg-info text-white"
            val content = if (isMatch) {
              s"<strong>${entry.court.getOrElse("")}</strong>: ${entry.nameA.getOrElse("")} vs ${entry.nameB.getOrElse("")} (${entry.compName.getOrElse("")})"
            } else {
              entry.text.getOrElse("")
            }
            s"""
              <tr>
                <td><span class="$typeClass">$typeText</span></td>
                <td>$content</td>
                <td class="text-end">
                  <button class="btn btn-danger btn-sm py-0" onclick="appTourneyAdminDeleteMatchboardEntry('${entry.id}')">
                    <i class="bi bi-trash"></i> Löschen
                  </button>
                </td>
              </tr>
            """
          }.mkString
          tbody.innerHTML = html
        case _ =>
          tbody.innerHTML = "<tr><td colspan='3' class='text-center text-muted py-3'>Keine Einträge vorhanden.</td></tr>"
      }
    }

  private def deleteMatchboardEntry(id: String): Unit =
    Global.currentSelection.tourney.foreach { t =>
      val slugName = t.slug.split('/').lastOption.getOrElse(t.slug)
      val slugParam = s"${slugName.trim}-${t.wpId}"
      
      val entry = shared.model.MatchboardEntry(id = id, entryType = "delete")
      val request = shared.model.MatchboardSetRequest(action = "delete", entry = Some(entry))
      
      ajaxPost[shared.model.MatchboardSetRequest, shared.model.MatchboardSetResponse]("/matchboard/set", List("slug" -> slugParam), request).map {
        case Right(res) if res.success =>
          fetchMatchboard()
        case Right(res) =>
          debug("Failed to delete matchboard entry: response success is false")
        case Left(err) =>
          debug(s"Failed to delete matchboard entry: ${err.msgCode}")
      }
    }

  private def addMatchboardEntry(): Unit =
    Global.currentSelection.tourney.foreach { t =>
      val slugName = t.slug.split('/').lastOption.getOrElse(t.slug)
      val slugParam = s"${slugName.trim}-${t.wpId}"
      
      val selectEl = dom.document.getElementById("mb-entry-type-select").asInstanceOf[dom.html.Select]
      if (selectEl != null) {
        val entryType = selectEl.value
        val entryOpt = if (entryType == "info") {
          val textEl = dom.document.getElementById("mb-info-text").asInstanceOf[dom.html.Input]
          val text = if (textEl != null) textEl.value.trim else ""
          if (text.nonEmpty) {
            // Generate a random ID
            val randId = scala.util.Random.alphanumeric.take(8).mkString
            Some(shared.model.MatchboardEntry(
              id = randId,
              entryType = "info",
              text = Some(text)
            ))
          } else None
        } else {
          val courtEl = dom.document.getElementById("mb-match-court").asInstanceOf[dom.html.Input]
          val playerAEl = dom.document.getElementById("mb-match-player-a").asInstanceOf[dom.html.Input]
          val playerBEl = dom.document.getElementById("mb-match-player-b").asInstanceOf[dom.html.Input]
          val compEl = dom.document.getElementById("mb-match-comp").asInstanceOf[dom.html.Input]
          val stageIdEl = dom.document.getElementById("mb-match-stage-id").asInstanceOf[dom.html.Input]
          val compIdEl = dom.document.getElementById("mb-match-comp-id").asInstanceOf[dom.html.Input]
          
          val court = if (courtEl != null) courtEl.value.trim else ""
          val playerA = if (playerAEl != null) playerAEl.value.trim else ""
          val playerB = if (playerBEl != null) playerBEl.value.trim else ""
          val compName = if (compEl != null) compEl.value.trim else ""
          
          val stageIdVal = if (stageIdEl != null && stageIdEl.value.trim.nonEmpty) {
            scala.util.Try(stageIdEl.value.trim.toInt).toOption.map(StageId.apply)
          } else None
          
          val gameNoVal = if (compIdEl != null && compIdEl.value.trim.nonEmpty) {
            scala.util.Try(compIdEl.value.trim.toInt).toOption
          } else None
          
          if (court.nonEmpty && playerA.nonEmpty && playerB.nonEmpty) {
            val randId = scala.util.Random.alphanumeric.take(8).mkString
            Some(shared.model.MatchboardEntry(
              id = randId,
              entryType = "start",
              court = Some(court),
              nameA = Some(playerA),
              nameB = Some(playerB),
              compName = Some(compName),
              stageId = stageIdVal,
              gameNo = gameNoVal
            ))
          } else None
        }
        
        entryOpt.foreach { entry =>
          val request = shared.model.MatchboardSetRequest(action = "set", entry = Some(entry))
          ajaxPost[shared.model.MatchboardSetRequest, shared.model.MatchboardSetResponse]("/matchboard/set", List("slug" -> slugParam), request).map {
            case Right(res) if res.success =>
              // Clear fields
              val textEl = dom.document.getElementById("mb-info-text").asInstanceOf[dom.html.Input]
              if (textEl != null) textEl.value = ""
              val courtEl = dom.document.getElementById("mb-match-court").asInstanceOf[dom.html.Input]
              if (courtEl != null) courtEl.value = ""
              val playerAEl = dom.document.getElementById("mb-match-player-a").asInstanceOf[dom.html.Input]
              if (playerAEl != null) playerAEl.value = ""
              val playerBEl = dom.document.getElementById("mb-match-player-b").asInstanceOf[dom.html.Input]
              if (playerBEl != null) playerBEl.value = ""
              val compEl = dom.document.getElementById("mb-match-comp").asInstanceOf[dom.html.Input]
              if (compEl != null) compEl.value = ""
              val stageIdEl = dom.document.getElementById("mb-match-stage-id").asInstanceOf[dom.html.Input]
              if (stageIdEl != null) stageIdEl.value = ""
              val compIdEl = dom.document.getElementById("mb-match-comp-id").asInstanceOf[dom.html.Input]
              if (compIdEl != null) compIdEl.value = ""
              
              fetchMatchboard()
            case Right(_) =>
              debug("Failed to add matchboard entry: response success is false")
            case Left(err) =>
              debug(s"Failed to add matchboard entry: ${err.msgCode}")
          }
        }
      }
    }
