package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import base.*
import shared.model.*
import scala.scalajs.js
import services.ComWrapper

object VerifyAccount extends BasePage with JsWrapper with ComWrapper:
  def name = PageNameTyp("VerifyAccount")

  def render(param: String): Boolean = 
    // UI initialisieren
    setMain("""<div class='text-center mt-5'><div class='spinner-border text-primary'></div><p class='mt-3'>Verifiziere deinen Account...</p></div>""")
    
    // URL Parameter extrahieren (z.B. ?uid=1&hash=abc)
    val urlParams = new dom.URLSearchParams(dom.window.location.search)
    val uid = urlParams.get("uid")
    val hash = urlParams.get("hash")

    if (uid != null && hash != null) {
      val data = Map("uid" -> uid, "hash" -> hash)
      
      // API Call an den PHP Endpoint
      ajaxPost[Map[String, String], Map[String, String]]("/wp-json/playdemo/v1/auth/verify", List(), data, host = Global.homeUrl).map {
        case Right(res) => 
          val msg = res.getOrElse("message", "E-Mail erfolgreich bestätigt.")
          setMain(s"""
            <div class='container mt-5 text-center'>
              <div class='alert alert-success shadow-sm p-5'>
                <i class='bi bi-check-circle text-success' style='font-size: 5rem;'></i><br>
                <h2 class='mt-4'>Erfolgreich!</h2>
                <p class='fs-5 mt-3'>$msg</p>
                <hr class='my-4'>
                <button class='btn btn-primary btn-lg px-5 fw-bold' onclick='appLoadPage("UserLogin", "")'>ZUM LOGIN</button>
              </div>
            </div>""")
        case Left(err) => 
          setMain(s"""
            <div class='container mt-5 text-center'>
              <div class='alert alert-danger shadow-sm p-5'>
                <i class='bi bi-exclamation-triangle text-danger' style='font-size: 5rem;'></i><br>
                <h2 class='mt-4'>Fehler</h2>
                <p class='fs-5 mt-3'>Verifizierung fehlgeschlagen: $err</p>
                <hr class='my-4'>
                <button class='btn btn-outline-secondary px-4' onclick='appLoadPage("Home", "")'>Zur Startseite</button>
              </div>
            </div>""")
      }
    } else {
      setMain("""
        <div class='container mt-5 text-center'>
          <div class='alert alert-warning shadow-sm p-5'>
            <i class='bi bi-question-circle text-warning' style='font-size: 5rem;'></i><br>
            <h2 class='mt-4'>Ungültiger Link</h2>
            <p class='fs-5 mt-3'>Der Verifizierungslink ist unvollständig oder ungültig.</p>
            <hr class='my-4'>
            <button class='btn btn-outline-secondary px-4' onclick='appLoadPage("Home", "")'>Zur Startseite</button>
          </div>
        </div>""")
    }
    true
