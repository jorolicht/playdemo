package frontend

import org.scalajs.dom
import org.scalajs.dom.HttpMethod
import org.scalajs.dom.{console, Fetch}
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.Thenable.Implicits.*
import scala.scalajs.js.annotation.*
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.concurrent.Future


def fetchJsonGet(url: String): Future[js.Any] =
  dom.fetch(url)
    .toFuture
    .flatMap(_.json().toFuture)

def fetchJsonPost(url: String, bodyParam: js.Any): Future[js.Any] =
  dom.fetch(
    url,
    new dom.RequestInit {
      method = HttpMethod.POST
      headers = js.Dictionary(
        "Content-Type" -> "application/json"
      )
      body = JSON.stringify(bodyParam)
    }
  )
  .toFuture
  .flatMap(_.json().toFuture)  


object Passkey:

  @JSExportTopLevel("startApp")
  def startApp(userId: String): Unit =
    console.log("Starting Passkey App for", userId)
  
    fetchJsonGet(s"/auth/register/$userId").foreach { options =>
      val credentials =
        dom.window.navigator.asInstanceOf[js.Dynamic].credentials
    
      credentials
        .create(js.Dynamic.literal(
          publicKey = options
        ))
        .asInstanceOf[js.Promise[js.Any]]
        .toFuture
        .foreach { cred =>
          println(s"Passkey created: $cred")
        }
    }

// Ablauf in der Praxis

// 1️⃣ User gibt E-Mail ein
// 2️⃣ FaceID / TouchID Popup
// 3️⃣ Kein Passwort
// 4️⃣ Session gesetzt
// 5️⃣ Login fertig


  @JSExportTopLevel("startLogin")
  def startLogin(email: String): Unit =
    fetchJsonPost("/auth/login/start", js.Dynamic.literal(
      email = email
    )).foreach { options =>
  
      val credentials =
        dom.window.navigator.asInstanceOf[js.Dynamic].credentials
  
      credentials
        .get(js.Dynamic.literal(
          publicKey = options
        ))
        .asInstanceOf[js.Promise[js.Any]]
        .toFuture
        .foreach { cred =>
          fetchJsonPost("/auth/login/finish", js.Dynamic.literal(
            credential = cred
          ))
        }
    }