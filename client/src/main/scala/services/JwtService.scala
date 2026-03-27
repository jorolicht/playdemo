package services

import shared.basic.AppError
import base.Global
import base.AuthMode
import scala.concurrent.*
import scala.concurrent.ExecutionContext.Implicits.global
import shared.basic.Pickle.*


case class JwtPayload(iss: String, iat: Long, exp: Long, user_id: Int)
object JwtPayload:
  given Reader[JwtPayload] = macroR


case class JwtResponse(token: String)
object JwtResponse:
  given Reader[JwtResponse] = macroR
  given Writer[JwtResponse] = macroW


object JwtService:
  private var cachedToken: Option[String] = None
  private var expiresAt: Long = 0

  private def now: Long = System.currentTimeMillis() / 1000

  def getToken(using cw: ComWrapper): Future[Either[AppError, String]] =
    cachedToken match
      case Some(token) if now < expiresAt =>
        Future.successful(Right(token))
      case _ =>
        refreshToken

  def refreshToken(using cw: ComWrapper): Future[Either[AppError, String]] =
    Global.authMode match
        case AuthMode.Nonce       => refreshTokenNonce
        case AuthMode.AppPassword => refreshTokenAppPassword      

  def refreshTokenNonce(using cw: ComWrapper): Future[Either[AppError, String]] =
    cw.ajaxPost[Map[String, String], JwtResponse](
        route = "/wp-json/tourney/v1/issue-jwt",
        params = Nil,
        data = Map.empty,
        hdrs = Map("Content-Type" -> "application/json", "X-WP-Nonce" -> Global.wpNonce),
        host = Global.homeUrl
    ).map {
        case Right(resp) =>
            val token = resp.token
            decodePayload(token).foreach { payload =>
                expiresAt = payload.exp
            }
            cachedToken = Some(token)
            Right(token)
        case Left(err) =>
            Left(err)
    }


  def refreshTokenAppPassword(using cw: ComWrapper): Future[Either[AppError, String]] =

    // 1. Credentials zusammenbauen
    val credentials = s"${Global.wpUserName}:${Global.wpAppPassword}"

    // 2. Base64 Kodierung (Sicherstellen, dass keine Newlines entstehen)
    // In Scala.js / Browser-Umfeld kann man auch 'btoa' nutzen, 
    // aber java.util.Base64 ist in Scala 3 meist stabiler.
    val basicAuth = java.util.Base64.getEncoder.encodeToString(credentials.getBytes("UTF-8"))

    println(s"1. Credentials: $credentials")
    println(s"2. Sending Auth Header: Basic $basicAuth")

    cw.ajaxPost[Map[String, String], JwtResponse](
        route = "/wp-json/tourney/v1/get-jwt-token",
        params = Nil,
        data = Map.empty, // Leer lassen, da curl auch keinen Body sendet
        hdrs = Map(
            "Authorization" -> s"Basic $basicAuth",
            "Accept" -> "application/json" // Manche Plugins brauchen diesen Hinweis
        ),
        host = Global.homeUrl,
        cred = false
    ).map {
        case Right(resp) =>
            val token = resp.token
            decodePayload(token).foreach { payload =>
                expiresAt = payload.exp
            }
            cachedToken = Some(token)
            Right(token)
        case Left(err) =>
            Left(err)
    }


  def decodePayload(jwt: String): Option[JwtPayload] =
    try
        val parts = jwt.split("\\.")
        val json =
        new String(java.util.Base64.getUrlDecoder.decode(parts(1)))
        Some(read[JwtPayload](json))
    catch
        case _: Throwable => None
      
