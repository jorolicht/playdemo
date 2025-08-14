package controllers

import javax.inject._
import java.time.Instant
import java.util.Base64
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._

import scala.concurrent.{ExecutionContext, Future}
import upickle.default._
import play.api.mvc._
import play.api.libs.mailer._
import play.api.{ Environment, Configuration, Logging }
import play.api.i18n.{ I18nSupport, Messages, Langs, Lang }

import org.apache.commons.mail.EmailAttachment

import models._
import shared._
import services._
import shared.model.{ AppError, User }

@Singleton
class Auth @Inject()(cc: ControllerComponents, mailer: MailerClient, cfg: Configuration, userRepo: UserRepository)
  (implicit ec: ExecutionContext) extends AbstractController(cc) with I18nSupport with Logging with Encryption {

  given decoder: Base64.Decoder = Base64.getDecoder
  given encoder: Base64.Encoder = Base64.getEncoder

  def googleLogin(): Action[AnyContent] = Action.async { implicit request =>
    import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
    import com.google.api.client.http.javanet.NetHttpTransport
    import com.google.api.client.json.gson.GsonFactory

    logger.trace(s"googleLogin")

    val googleClientID = cfg.get[String]("google.ClientID")
    val transport   = new NetHttpTransport()
    val jsonFactory = GsonFactory.getDefaultInstance

    // Specify the CLIENT_ID of the app that accesses the backend:
    val verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
      .setAudience(Seq(googleClientID).asJava)
      .build()

    val idTokenString= request.body.asText.getOrElse("")

    val idToken = verifier.verify(idTokenString)
    if (idToken != null) then
      val payload = idToken.getPayload
      // Get profile information from payload
      val userId     = payload.getSubject
      val email      = payload.getEmail
      val picUrl     = payload.get("picture").asInstanceOf[String]
      val locale     = payload.get("locale").asInstanceOf[String]
      val lastname   = payload.get("family_name").asInstanceOf[String]
      val firstname  = payload.get("given_name").asInstanceOf[String]

      // see https://developers.google.com/identity/gsi/web/guides/verify-google-id-token
      // Once the token's validity is confirmed, you can use the information in the Google ID token to 
      // correlate the account status of your site:

      // - An unregistered user: You can show a sign-up user interface (UI) that allows the user to 
      //   provide additional profile information, if required. It also allows the user to silently 
      //   create the new account and a logged-in user session.
      // - An existing account that already exists in your site: You can show a web page that allows the 
      //   end user to input their password and link the legacy account with their Google credentials. 
      //   This confirms that the user has access to the existing account.
      //   A returning federated user: You can silently sign the user in.

      userRepo.getUser(email).flatMap( _ match {
        case Left(err)  => 
          val user = User(firstname, lastname, email, "Google", picUrl, locale, true)
          userRepo.insert(user).map { 
            case Left(err)  => BadRequest(toJson(err)).discardingCookies(genDiscardAuthCookie) 
            case Right(usr) => Ok(toJson(usr)).withCookies(genUserAuthCookie(usr)) 
          }  
        case Right(usr) => if (usr.lastname.toLowerCase == lastname.toLowerCase) 
          then Future(Ok(toJson(usr)).withCookies(genUserAuthCookie(usr)) )  
          else Future(BadRequest(toJson(AppError("err00013.auth.google",usr.lastname, lastname))).discardingCookies(genDiscardAuthCookie)) 
      })
    else 
      Future(BadRequest(toJson(AppError("err00014.auth.token.invalid"))))
  }



  def basicLogin(): Action[AnyContent] = Action.async { implicit request =>
    logger.trace(s"basicLogin")
    try 
      // get authorization header, decode and split 
      val authHeader = request.headers.get("Authorization").getOrElse("")
      val authBase   = authHeader.split(" ").drop(1).headOption.getOrElse("x:x")
      val (email,pw) = decode64(authBase).toTuple(":")
      
      userRepo.verify(email, pw).map {
        case Left(err)  => BadRequest(toJson(err)).discardingCookies(genDiscardAuthCookie)
        case Right(usr) => Ok(toJson(usr)).withCookies(genUserAuthCookie(usr))  
      }
    catch case e: Throwable => Future(BadRequest(toJson(AppError("err00006.parseJson"))))
  }


  def logout(): Action[AnyContent] = Action { implicit request =>
    logger.trace(s"logout")

    // logout only when user has valid cookie
    getUserFromCookie(request) match 
      case Left(err)  => BadRequest(toJson(err))
      case Right(usr1) => 
        parseJson[User](request.body.asText.getOrElse("")) match 
          case Left(err)   => BadRequest(toJson(err))
          case Right(usr2) => Ok(toJson(usr1.id == usr2.id)).discardingCookies(genDiscardAuthCookie) 
  }


  def verifyUser(code: String): Action[AnyContent] = Action.async { implicit request =>
    logger.trace(s"verifyUser -> code=${code}")

    parseJson[(Long,String,Long)](decode64(code)) match 
      case Left(err)    => Future(Ok("Error"))
      case Right(vCode) => userRepo.setEmailVerified(vCode._1).map {
        case Left(err)  => Ok(views.html.main("Error", encode64(toJson(AppError("verifyUserNotPossible"))) ))
        case Right(res) => if (res) 
          then Ok(views.html.main("Home", "verified")) 
          else Ok(views.html.main("Error", encode64(toJson(AppError("verifyUserNotPossible"))) ))
      }
  }     


  /** request to register a user return generated user (with id) else AppError
   *  sends verification email to user
   */
  def regUser(): Action[AnyContent] = Action.async { implicit request =>
    logger.trace(s"regUser")

    val msgs:  Messages   = messagesApi.preferred(request)
    val curUnixTime: Long = Instant.now().getEpochSecond
    
    parseJson[User](request.body.asText.getOrElse("")) match 
      case Left(err)  => Future(BadRequest(toJson(err)))
      case Right(usr) => userRepo.insert(usr.copy(request=curUnixTime,verified=false)).map { 
        case Right(regUsr)  => 
          val code  = encode64(regUsr.verifyInfo)
          val email = Email(
            subject  = msgs("email.register.subject"),
            from     = msgs("email.register.from"),
            to       = Seq(usr.email),
            bodyHtml = Some(views.html.email.verify(usr.firstname, usr.lastname, code).toString),
            bodyText = Some("TODO")
          )
          mailer.send(email)
          Ok(toJson(regUsr))
        case Left(err)  => BadRequest(toJson(err))
      }
  }


  // setUserPassword - set users password
  def setUserPassword(email: String): Action[AnyContent] = Action.async { implicit request =>
    logger.trace(s"setUserPassword -> email=${email}")

    // TODO: Only the user himself or the admin should be able to do that
    val password = request.body.asText.getOrElse("")
    logger.debug(s"setUserPassword: email->${email} password->${password}")
    userRepo.setPassword(email, password).map {
      case Left(err)  => BadRequest(toJson(err.add("setUserPassword")))
      case Right(res) => Ok(toJson(res))
    }
  }  


}
