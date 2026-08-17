package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.model.*
import services.ComWrapper
import shared.basic.Pickle.*

object Management extends BasePage with JsWrapper with ComWrapper:
  def name = PageNameTyp("Management")

  val BtnSearchUser:       HtmlId = genId(name)
  val BtnDecAvailable:     HtmlId = genId(name)
  val BtnIncAvailable:     HtmlId = genId(name)
  val BtnAdd5Available:    HtmlId = genId(name)
  val BtnAdd10Available:   HtmlId = genId(name)
  val BtnSaveUserProfile:  HtmlId = genId(name)
  val BtnRefreshUnmatched: HtmlId = genId(name)

  private var searchResults: List[AdminUserInfo] = Nil
  private var selectedUser: Option[AdminUserInfo] = None
  private var unmatchedList: List[UnmatchedPurchase] = Nil

  def render(param: String = ""): Boolean =
    if (!Global.user.exists(_.roles.contains("administrator"))) {
      pages.loadPage(PageNameTyp("PgError"), "Zugriff verweigert: Die Management-Seite steht nur Administratoren zur Verfügung.")
      false
    } else {
      setMain(cviews.pages.html.Management(Global.user))
      // Automatically load users list and unmatched purchases upon rendering
      doSearchUsers("")
      doLoadUnmatchedPurchases()
      true
    }

  override def handleEvent(elem: HTMLElement, event: dom.Event): Unit =
    HtmlId(elem.id) match
      case `BtnSearchUser` =>
        val inputElem = dom.document.getElementById("mgmt-user-search-input").asInstanceOf[dom.html.Input]
        val input = if (inputElem != null) inputElem.value else ""
        doSearchUsers(input)

      case `BtnDecAvailable` =>
        adjustAvailable(-1)

      case `BtnIncAvailable` =>
        adjustAvailable(1)

      case `BtnAdd5Available` =>
        adjustAvailable(5)

      case `BtnAdd10Available` =>
        adjustAvailable(10)

      case `BtnSaveUserProfile` =>
        doSaveUserProfile()

      case `BtnRefreshUnmatched` =>
        doLoadUnmatchedPurchases()

      case _ => ()

  private def adjustAvailable(delta: Int): Unit =
    val elem = dom.document.getElementById("mgmt-available-input").asInstanceOf[dom.html.Input]
    if (elem != null) {
      val current = elem.value.toIntOption.getOrElse(0)
      val updated = math.max(0, current + delta)
      elem.value = updated.toString
    }

  private def doSearchUsers(query: String): Unit =
    showStatusAlert("Lade Benutzer...", isSuccess = true)
    val encodedQuery = scala.scalajs.js.URIUtils.encodeURIComponent(query.trim)
    val url = if (query.trim.nonEmpty) s"/wp-json/tourney/v1/admin/users?query=$encodedQuery" else "/wp-json/tourney/v1/admin/users"
    
    ajaxGet[List[AdminUserInfo]](url, List(), Map("X-WP-NONCE" -> Global.wpNonce), Global.homeUrl).map {
      case Right(list) =>
        searchResults = list
        renderUsersList(list)
        hideStatusAlert()
      case Left(err) =>
        showStatusAlert(s"Fehler beim Suchen von Benutzern: ${err.msgCode}", isSuccess = false)
    }

  private def renderUsersList(users: List[AdminUserInfo]): Unit =
    val container = dom.document.getElementById("mgmt-users-list-container")
    if (container == null) return

    if (users.isEmpty) {
      container.innerHTML = """<div class="text-muted small p-3 text-center bg-light rounded border">Keine Benutzer gefunden.</div>"""
      return
    }

    val sb = new StringBuilder()
    users.zipWithIndex.foreach { case (u, idx) =>
      val isSelected = selectedUser.exists(_.user_id == u.user_id)
      val activeClass = if (isSelected) "active" else ""
      val badgeColor = if (isSelected) "bg-light text-dark" else "bg-warning text-dark"
      sb.append(s"""
        <button type="button" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center $activeClass" id="mgmt-user-item-$idx">
          <div>
            <div class="fw-bold">${u.username}</div>
            <div class="small text-muted">${u.email}</div>
          </div>
          <span class="badge $badgeColor rounded-pill fs-6">${u.user_profile.available} Turniere</span>
        </button>
      """)
    }
    container.innerHTML = sb.toString()

    // Attach click listeners to user list items
    users.zipWithIndex.foreach { case (u, idx) =>
      val btn = dom.document.getElementById(s"mgmt-user-item-$idx")
      if (btn != null) {
        btn.addEventListener("click", (_: dom.Event) => {
          selectUser(u)
          renderUsersList(searchResults)
        })
      }
    }

  private def selectUser(u: AdminUserInfo): Unit =
    selectedUser = Some(u)
    val placeholder = dom.document.getElementById("mgmt-user-details-placeholder")
    val card = dom.document.getElementById("mgmt-user-details-card")
    if (placeholder != null) placeholder.classList.add("d-none")
    if (card != null) card.classList.remove("d-none")

    val nameElem = dom.document.getElementById("mgmt-selected-username")
    val emailElem = dom.document.getElementById("mgmt-selected-email")
    val rolesElem = dom.document.getElementById("mgmt-selected-roles")
    val availableInput = dom.document.getElementById("mgmt-available-input").asInstanceOf[dom.html.Input]
    val executedInput = dom.document.getElementById("mgmt-executed-input").asInstanceOf[dom.html.Input]
    val tbody = dom.document.getElementById("mgmt-history-tbody")

    if (nameElem != null) nameElem.textContent = u.username
    if (emailElem != null) emailElem.textContent = u.email
    if (rolesElem != null) rolesElem.textContent = u.roles.mkString(", ")
    if (availableInput != null) availableInput.value = u.user_profile.available.toString
    if (executedInput != null) executedInput.value = u.user_profile.executed.toString

    if (tbody != null) {
      if (u.user_profile.history.isEmpty) {
        tbody.innerHTML = """<tr><td colspan="3" class="text-muted small text-center">Keine Käufe vorhanden.</td></tr>"""
      } else {
        val hSb = new StringBuilder()
        u.user_profile.history.foreach { p =>
          val formattedDate = Global.formatPurchaseDate(p.date)
          val formattedPrice = f"${p.price}%.2f €"
          hSb.append(s"""
            <tr>
              <td>${formattedDate}</td>
              <td class="text-center fw-bold">${p.count}</td>
              <td class="text-end">${formattedPrice}</td>
            </tr>
          """)
        }
        tbody.innerHTML = hSb.toString()
      }
    }

  private def doSaveUserProfile(): Unit =
    selectedUser match
      case None =>
        showStatusAlert("Bitte wählen Sie zuerst einen Benutzer aus.", isSuccess = false)
      case Some(u) =>
        val availableInput = dom.document.getElementById("mgmt-available-input").asInstanceOf[dom.html.Input]
        val executedInput = dom.document.getElementById("mgmt-executed-input").asInstanceOf[dom.html.Input]
        val newAvailable = if (availableInput != null) availableInput.value.toIntOption.getOrElse(0) else u.user_profile.available
        val newExecuted = if (executedInput != null) executedInput.value.toIntOption.getOrElse(0) else u.user_profile.executed

        showStatusAlert("Speichere UserProfile...", isSuccess = true)

        val payloadMap = Map(
          "user_id" -> u.user_id,
          "available" -> newAvailable,
          "executed" -> newExecuted
        )

        ajaxPost[String, Map[String, Int]]("/wp-json/tourney/v1/admin/update_user_profile", List(), write(payloadMap), Map("X-WP-NONCE" -> Global.wpNonce), Global.homeUrl).map {
          case Right(_) =>
            val updatedProfile = u.user_profile.copy(available = newAvailable, executed = newExecuted)
            val updatedUser = u.copy(user_profile = updatedProfile, allowed_tourneys = newAvailable)
            selectedUser = Some(updatedUser)
            searchResults = searchResults.map(x => if (x.user_id == u.user_id) updatedUser else x)
            renderUsersList(searchResults)
            showStatusAlert(s"UserProfile für ${u.username} erfolgreich gespeichert. Verfügbare Turniere: $newAvailable.", isSuccess = true)
          case Left(err) =>
            showStatusAlert(s"Fehler beim Speichern von UserProfile: ${err.msgCode}", isSuccess = false)
        }

  // --- Schritt 3: Unmatched Purchases Handling ---

  private def doLoadUnmatchedPurchases(): Unit =
    ajaxGet[List[UnmatchedPurchase]]("/wp-json/tourney/v1/admin/unmatched_purchases", List(), Map("X-WP-NONCE" -> Global.wpNonce), Global.homeUrl).map {
      case Right(list) =>
        unmatchedList = list
        renderUnmatchedPurchases(list)
      case Left(err) =>
        debug(s"Failed to load unmatched purchases: ${err.msgCode}")
    }

  private def renderUnmatchedPurchases(list: List[UnmatchedPurchase]): Unit =
    val tbody = dom.document.getElementById("mgmt-unmatched-tbody")
    if (tbody == null) return

    if (list.isEmpty) {
      tbody.innerHTML = """<tr><td colspan="6" class="text-muted small text-center">Keine nicht zugewiesenen Käufe vorhanden.</td></tr>"""
      return
    }

    val sb = new StringBuilder()
    list.zipWithIndex.foreach { case (p, idx) =>
      val formattedDate = Global.formatPurchaseDate(p.date)
      val formattedPrice = f"${p.price}%.2f €"
      val productName = if (p.product_name.nonEmpty) p.product_name else "-"
      
      sb.append(s"""
        <tr>
          <td class="fw-bold text-primary">${p.email}</td>
          <td>$formattedDate</td>
          <td class="text-center fw-bold">${p.count}</td>
          <td class="text-end">$formattedPrice</td>
          <td><span class="badge bg-light text-dark border">$productName</span></td>
          <td class="text-end">
            <button type="button" class="btn btn-sm btn-outline-success fw-bold" id="mgmt-assign-btn-$idx">
              <i class="bi bi-person-check me-1"></i>Zuweisen
            </button>
          </td>
        </tr>
      """)
    }
    tbody.innerHTML = sb.toString()

    // Attach click listeners to assign buttons
    list.zipWithIndex.foreach { case (p, idx) =>
      val btn = dom.document.getElementById(s"mgmt-assign-btn-$idx")
      if (btn != null) {
        btn.addEventListener("click", (_: dom.Event) => {
          doAssignUnmatchedPurchase(idx, p)
        })
      }
    }

  private def doAssignUnmatchedPurchase(index: Int, purchase: UnmatchedPurchase): Unit =
    selectedUser match {
      case None =>
        dom.window.alert(s"Bitte wählen Sie zuerst links in der User-Verwaltung den Ziel-Benutzer aus, dem der Kauf von '${purchase.email}' zugewiesen werden soll.")
      case Some(u) =>
        val confirmMsg = s"Möchten Sie den Kauf über ${purchase.count} Turnier(e) (${f"${purchase.price}%.2f €"}) von E-Mail '${purchase.email}' wirklich dem Benutzer '${u.username}' (ID: ${u.user_id}) zuweisen?"
        if (dom.window.confirm(confirmMsg)) {
          showStatusAlert(s"Zuweisung des Kaufs an ${u.username}...", isSuccess = true)
          val payload = AssignPayload(u.user_id, purchase.email, index)
          ajaxPost[String, AssignPayload]("/wp-json/tourney/v1/admin/assign_unmatched_purchase", List(), write(payload), Map("X-WP-NONCE" -> Global.wpNonce), Global.homeUrl).map {
            case Right(_) =>
              showStatusAlert(s"Kauf von ${purchase.email} (${purchase.count} Turniere) erfolgreich an ${u.username} zugewiesen!", isSuccess = true)
              // Refresh selected user profile & unmatched list
              doSearchUsers(u.username)
              doLoadUnmatchedPurchases()
            case Left(err) =>
              showStatusAlert(s"Fehler bei der Zuweisung: ${err.msgCode}", isSuccess = false)
          }
        }
    }

  private def showStatusAlert(msg: String, isSuccess: Boolean): Unit =
    val alert = dom.document.getElementById("mgmt-status-alert")
    val text = dom.document.getElementById("mgmt-status-text")
    if (alert != null && text != null) {
      text.textContent = msg
      alert.classList.remove("d-none")
      alert.classList.remove("alert-success")
      alert.classList.remove("alert-danger")
      if (isSuccess) alert.classList.add("alert-success") else alert.classList.add("alert-danger")
    }

  private def hideStatusAlert(): Unit =
    val alert = dom.document.getElementById("mgmt-status-alert")
    if (alert != null) alert.classList.add("d-none")
