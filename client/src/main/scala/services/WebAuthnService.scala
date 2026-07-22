package services

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array
import scala.scalajs.js.typedarray.ArrayBuffer
import base.*
import shared.model.*

/**
 * WebAuthnService handles the browser side of Passkey registration and login.
 * It manages binary conversions and interactions with navigator.credentials.
 */
object WebAuthnService extends JsWrapper with ComWrapper:

  // --- PUBLIC API ---

  /**
   * Registers a new Passkey for the currently logged-in user.
   */
  def registerPasskey(): Future[Either[shared.basic.AppError, String]] =
    val url = genPath(Global.homeUrl, "/wp-json/tourney/v1/auth/webauthn/register-args", List())
    org.scalajs.dom.ext.Ajax.get(url, headers = Map("X-WP-Nonce" -> Global.wpNonce)).map { resp =>
      Right(resp.responseText)
    }.recover {
      case org.scalajs.dom.ext.AjaxException(req) => Left(shared.basic.parseError(req.responseText, "register-args"))
      case e: Throwable => Left(shared.basic.AppError("webauthn.error", e.getMessage))
    }.flatMap {
      case Right(argsStr) =>
        try {
          val args = js.JSON.parse(argsStr).asInstanceOf[js.Dynamic]
          
          // 2. Transform args for browser (Strings to ArrayBuffers)
          val credentialOptions = transformCreateArgs(args)
          
          // 3. Call Browser API (using js.Dynamic to bypass missing types)
          val credentials = dom.window.navigator.asInstanceOf[js.Dynamic].credentials
          val promise = credentials.create(credentialOptions).asInstanceOf[js.Promise[js.Dynamic]]
          
          promise.toFuture.flatMap { credential =>
            // 4. Transform response for server (ArrayBuffers to Base64/Strings)
            val data = transformCreateResponse(credential)
            
            // 5. Send to server
            ajaxPost[Map[String, String], Map[String, String]](
              "/wp-json/tourney/v1/auth/webauthn/register", 
              List(), 
              data, 
              host = Global.homeUrl
            ).map {
              case Right(success) => Right(success.getOrElse("message", "Passkey registriert."))
              case Left(err)      => Left(err)
            }
          }
        } catch {
          case e: Exception => Future.successful(Left(shared.basic.AppError("webauthn.error", e.getMessage)))
        }
      case Left(err) => Future.successful(Left(err))
    }

  def loginPasskey(): Future[Either[shared.basic.AppError, String]] =
    val url = genPath(Global.homeUrl, "/wp-json/tourney/v1/auth/webauthn/login-args", List())
    org.scalajs.dom.ext.Ajax.get(url).map { resp =>
      Right(resp.responseText)
    }.recover {
      case org.scalajs.dom.ext.AjaxException(req) => Left(shared.basic.parseError(req.responseText, "login-args"))
      case e: Throwable => Left(shared.basic.AppError("webauthn.error", e.getMessage))
    }.flatMap {
      case Right(resStr) =>
        try {
            val resJson = js.JSON.parse(resStr).asInstanceOf[js.Dynamic]
            val args = resJson.args
            val challengeId = resJson.challengeId.toString
            
            // 2. Transform for browser
            val requestOptions = transformGetArgs(args)
            
            // 3. Call Browser API
            val credentials = dom.window.navigator.asInstanceOf[js.Dynamic].credentials
            val promise = credentials.get(requestOptions).asInstanceOf[js.Promise[js.Dynamic]]
            
            promise.toFuture.flatMap { assertion =>
                // 4. Transform response
                val data = transformGetResponse(assertion) + ("challengeId" -> challengeId)
                
                // 5. Verify with server
                ajaxPost[Map[String, String], Map[String, String]](
                    "/wp-json/tourney/v1/auth/webauthn/login", 
                    List(), 
                    data, 
                    hdrs = Map("Content-Type" -> "application/json"),
                    host = Global.homeUrl
                ).map {
                    case Right(success) => Right(success.getOrElse("message", "Login erfolgreich."))
                    case Left(err)      => Left(err)
                }
            }
        } catch {
          case e: Exception => Future.successful(Left(shared.basic.AppError("webauthn.error", e.getMessage)))
        }
      case Left(err) => Future.successful(Left(err))
    }


  // --- HELPERS: Browser Transformation ---

  private def transformCreateArgs(args: js.Dynamic): js.Dynamic =
    val challenge = base64ToBuffer(args.challenge.toString)
    val user = args.user
    val userDict = js.Dynamic.literal(
        id = base64ToBuffer(user.id.toString),
        name = user.name,
        displayName = user.displayName
    )

    // Handle existing credentials if any
    val excludeCredentials = if (js.isUndefined(args.excludeCredentials)) js.Array() else args.excludeCredentials.asInstanceOf[js.Array[js.Dynamic]].map { c =>
        js.Dynamic.literal(
            id = base64ToBuffer(c.id.toString),
            `type` = c.`type`
        )
    }

    js.Dynamic.literal(
        publicKey = js.Dynamic.literal(
            rp = args.rp,
            user = userDict,
            challenge = challenge,
            pubKeyCredParams = args.pubKeyCredParams,
            timeout = args.timeout,
            excludeCredentials = excludeCredentials,
            authenticatorSelection = args.authenticatorSelection,
            attestation = args.attestation
        )
    )

  private def transformGetArgs(args: js.Dynamic): js.Dynamic =
    val challenge = base64ToBuffer(args.challenge.toString)
    
    js.Dynamic.literal(
        publicKey = js.Dynamic.literal(
            challenge = challenge,
            timeout = args.timeout,
            rpId = args.rpId,
            userVerification = args.userVerification
        )
    )


  // --- HELPERS: Server Transformation ---

  private def transformCreateResponse(res: js.Dynamic): Map[String, String] =
    val response = res.response
    Map(
        "id" -> res.id.toString,
        "rawId" -> bufferToBase64(res.rawId.asInstanceOf[ArrayBuffer]),
        "clientDataJSON" -> bufferToBase64(response.clientDataJSON.asInstanceOf[ArrayBuffer]),
        "attestationObject" -> bufferToBase64(response.attestationObject.asInstanceOf[ArrayBuffer]),
        "type" -> res.`type`.toString
    )

  private def transformGetResponse(res: js.Dynamic): Map[String, String] =
    val response = res.response
    Map(
        "id" -> res.id.toString,
        "rawId" -> bufferToBase64(res.rawId.asInstanceOf[ArrayBuffer]),
        "clientDataJSON" -> bufferToBase64(response.clientDataJSON.asInstanceOf[ArrayBuffer]),
        "authenticatorData" -> bufferToBase64(response.authenticatorData.asInstanceOf[ArrayBuffer]),
        "signature" -> bufferToBase64(response.signature.asInstanceOf[ArrayBuffer]),
        "userHandle" -> (if (response.userHandle != null) bufferToUtf8(response.userHandle.asInstanceOf[ArrayBuffer]) else ""),
        "type" -> res.`type`.toString
    )


  // --- BINARY UTILS ---

  private def base64ToBuffer(base64: String): ArrayBuffer =
    val binaryString = dom.window.atob(base64.replace("-", "+").replace("_", "/"))
    val bytes = new Uint8Array(binaryString.length)
    for (i <- 0 until binaryString.length) {
        bytes(i) = binaryString.charAt(i).toShort
    }
    bytes.buffer

  private def bufferToBase64(buffer: ArrayBuffer): String =
    val bytes = new Uint8Array(buffer)
    var binary = ""
    for (i <- 0 until bytes.length) {
        binary += dom.window.asInstanceOf[js.Dynamic].String.fromCharCode(bytes(i)).toString
    }
    dom.window.btoa(binary).replace("+", "-").replace("/", "_").replace("=", "")

  private def bufferToUtf8(buffer: ArrayBuffer): String =
    val bytes = new Uint8Array(buffer)
    var out = ""
    for (i <- 0 until bytes.length) {
        out += bytes(i).toChar
    }
    out
