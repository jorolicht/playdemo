package pages

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import base.*
import shared.model.*
import scala.scalajs.js
import services.ComWrapper

object VerifyAccount extends BasePage with JsWrapper with ComWrapper:
  def name = PageNameTyp("VerifyAccount")

  def render(param: String): Boolean = 
    // UI initialisieren (Spinner)
    setMain(cviews.pages.html.VerifyAccount("loading", ""))
    
    // URL Parameter extrahieren (z.B. ?uid=1&hash=abc)
    val urlParams = new dom.URLSearchParams(dom.window.location.search)
    val uid = urlParams.get("uid")
    val hash = urlParams.get("hash")

    if (uid != null && hash != null) {
      val data = Map("uid" -> uid, "hash" -> hash)
      
      // API Call an den PHP Endpoint
      ajaxPost[Map[String, String], Map[String, String]]("/wp-json/tourney/v1/auth/verify", List(), data, hdrs = Map("Content-Type" -> "application/json"), host = Global.homeUrl).map {
        case Right(res) => 
          val msg = res.getOrElse("message", "E-Mail erfolgreich bestätigt.")
          val loggedIn = res.get("logged_in").contains("true")
          setMain(cviews.pages.html.VerifyAccount("success", msg, loggedIn))
        case Left(err) => 
          setMain(cviews.pages.html.VerifyAccount("error", err.msg))
      }
    } else {
      setMain(cviews.pages.html.VerifyAccount("invalid", ""))
    }
    true
