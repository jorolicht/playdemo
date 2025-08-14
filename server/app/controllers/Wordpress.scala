package controllers

import javax.inject._
import upickle.default._
import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc._
import play.api.libs.ws._
import play.api.libs.json._
import play.api.{ Configuration, Logging }
import play.api.i18n.{ I18nSupport, Messages, Langs, Lang }

import models._
import shared._
import shared.model._
import shared.model._


case class WpUserLogin(
    user: String,
    club: String,
    apName: String,
    apPassword: String
)

object WpUserLogin {
  // Implicit Reads for UserData case class
  // This macro generates the necessary JSON reader from the case class fields
  implicit val userDataReads: Reads[WpUserLogin] = Json.reads[WpUserLogin]
}


@Singleton
class Wordpress @Inject()
 (cc: ControllerComponents, cfg: Configuration, ws: WSClient)
 (implicit ec: ExecutionContext) 
  extends AbstractController(cc) with I18nSupport with Logging  {

  // Lesen der Konfigurationsdaten aus application.conf
  val wpDomain: String   = cfg.get[String]("wordpress.api.domain")
  val wpUsername: String = cfg.get[String]("wordpress.api.username")
  val wpPassword: String = cfg.get[String]("wordpress.api.password")
  val wpCPT:      String = cfg.get[String]("wordpress.api.cpt")


  def getPost(slug: String, id: Int, field: String, status: String): Action[AnyContent] = Action.async { implicit request =>
    import cats.data.EitherT
    import cats.implicits._ 

    (for {
      postId    <- EitherT(if id!=0 then Future(Right(id)) else getPostIdFromSlug(slug, status))
      result    <- EitherT(getPostFromId(postId))
    } yield { (postId, result) }).value.map {
      case Right(res)  => if field=="" then Ok(s"${res._2}") else Ok((res._2.as[JsObject] \ "meta" \ s"${field}").asOpt[String].getOrElse(""))
      case Left(err)   => BadRequest(err.toString)
    }
  } 

  def putPost(slug: String, id: Int, field: String, status: String): Action[AnyContent] = Action.async { implicit request =>
    import cats.data.EitherT
    import cats.implicits._ 

    logger.debug(s"Attempting to put field '$field' with '${request.body.asText.getOrElse("")}' ")
    (for {
      postId    <- EitherT(if id!=0 then Future(Right(id)) else getPostIdFromSlug(slug, status))
      result    <- EitherT(putPostById(postId, field, request.body.asText.getOrElse("")))
    } yield { (postId, result) }).value.map {
      case Right(res)  => Ok(res._2.toString)
      case Left(err)   => BadRequest(err.toString)
    }
  } 


  def postToken(): Action[JsValue] = Action(parse.json) { implicit request =>
    val url = s"${wpDomain}/wp-json/jwt-auth/v1/token"
    logger.debug(s"Attempting to get jwt token for user: ${request.body}")

    // Attempt to bind the JSON to our UserData case class
    request.body.validate[WpUserLogin] match {
      case JsSuccess(userData, path) =>
        println(s"Received UserData: $userData") // Log the entire object

        // Access individual elements
        val user       = userData.user
        val club       = userData.club
        val apName     = userData.apName
        val apPassword = userData.apPassword
        println(s"Name: $user")
        println(s"Club: $club")


        // You would typically save this data to a database here
        // userStorage.save(userData)

        // Return a successful response, maybe with the created resource's ID
        Ok(Json.obj("message" -> "User created successfully", "userId" -> 123))

      case JsError(errors) =>
        // If JSON parsing or validation fails
        val errorMessages = errors.map { case (path, validationErrors) =>
          s"Error at $path: ${validationErrors.map(_.message).mkString(", ")}"
        }.mkString("\n")
        BadRequest(Json.obj("message" -> "Invalid JSON data", "errors" -> errorMessages))
    }
  }




  //   ws.url(url)
  //     .withAuth(wpUsername, wpPassword, WSAuthScheme.BASIC) // Fügt Basic Authentication hinzu
  //     .withHttpHeaders("Content-Type" -> "application/json")
  //     .put(Json.obj("meta" -> Json.obj(username -> "robert", password -> "4571")))
  //     .map { response =>
  //       response.status match {
  //         case OK => 
  //           logger.debug(s"Token: ${response.body}")
  //           Right(response.body)
  //         case _  => Left(AppError("err00056.wordpress.error", response.status, response.body))
  //       }
  //     } recover {
  //       case ex: Throwable =>Left(AppError("err00052.wordpress.network.unexpected", ex.getMessage))
  //     }
  // } 

  /**
   * Retrieves the ID of a WordPress custom post type by its slug.
   *
   * This method constructs a GET request to the WordPress REST API's `wp/v2/<cpt>?slug=<slug>` endpoint.
   * It performs authentication using Basic HTTP Authentication and expects a JSON response.
   *
   * @param slug The unique slug of the WordPress post or custom post type.
   * @return A `Future` that will eventually contain an `Either`.
   * - `Right(Int)`: If a post with the given slug is found and its ID is successfully parsed.
   * - `Left(AppError)`: If the post is not found, the API call fails (non-200 status),
   * the JSON response is malformed, or a network error occurs.
   */
  def getPostIdFromSlug(slug: String, status: String): Future[Either[AppError, Int]] = {
    // Construct the URL for the WordPress REST API endpoint to query posts by slug.
    // Example URL: "http://your-wordpress-domain.com/wp-json/wp/v2/posts?slug=your-post-slug"
    val url = s"${wpDomain}/wp-json/wp/v2/${wpCPT}?slug=${slug}&status=${status}"
    logger.debug(s"Attempting to get post ID for slug '$slug' with status '${status}' from URL: $url")

    ws.url(url)
      // Apply Basic HTTP Authentication using configured WordPress username and password.
      // NOTE: Basic Auth sends credentials in base64. Always use HTTPS in production.
      // Use WordPress Application Passwords
      .withAuth(wpUsername, wpPassword, WSAuthScheme.BASIC)
      // Set the Accept header to "application/json" to explicitly request JSON responses.
      .addHttpHeaders("Accept" -> "application/json")
      // Execute the asynchronous GET request to the WordPress API.
      .get()
      // Process the HTTP response received from the WordPress API.
      .map { response =>
        // Check if the HTTP response status is OK (200).
        // A 200 OK status indicates a successful response from the server,
        // though the result might still be an empty JSON array if no post matches the slug.
        if (response.status == OK) {
          logger.debug(s"Received 200 OK for slug '$slug'. Parsing response.")
          val jsonResponse = response.json // Parse the successful HTTP response body as a Play JsValue.

          // WordPress returns results for slug queries as a JSON array, even if only one item matches.
          // This line attempts to extract the 'id' field from the first element of the JSON array.
          // `\ 0` navigates to the first element of the array (expecting `[ { ... } ]`).
          // `\ "id"` accesses the 'id' field within that object.
          // `.asOpt[Int]` attempts to safely convert the extracted value to an Int and wraps it in an Option.
          (jsonResponse \ 0 \ "id").asOpt[Int] match {
            case Some(id) =>
              logger.info(s"Successfully retrieved ID '$id' for slug '$slug' ")
              Right(id) // If an 'id' is successfully extracted, return Right with the ID.
            case None =>
              // If no 'id' could be extracted (e.g., the JSON array was empty, or 'id' field missing/malformed).
              // This typically means no post was found for the given slug.
              logger.warn(s"No ID found or failed to parse ID for slug '$slug'. JSON: ${jsonResponse.toString()}")
              Left(AppError("err00050.wordpress.notfound.parse", slug))
          }
        } else {
          // If the HTTP response status was not OK (e.g., 404, 500, 401 Unauthorized).
          logger.error(s"WordPress API returned non-OK status ${response.status} for slug '$slug'. Response body: ${response.body}")
          Left(AppError("err00051.wordpress.api.non.ok", response.status, slug))
        }
      }.recover {
        // Handles potential exceptions during the asynchronous HTTP request itself 
        // (e.g., network connectivity issues, DNS resolution failure).
        case e: Exception =>
          logger.error(s"Network or unexpected error while fetching ID for slug '$slug': ${e.getMessage}", e)
          Left(AppError("err00052.wordpress.network.unexpected", e.getMessage))
      }
  }


  def getPostFromId(id: Int): Future[Either[AppError, JsObject]] = {
    val url = s"$wpDomain/wp-json/wp/v2/${wpCPT}/$id"
    ws.url(url)
      .withAuth(wpUsername, wpPassword, WSAuthScheme.BASIC) // Fügt Basic Authentication hinzu
      .addHttpHeaders("Accept" -> "application/json")       // Fordert JSON-Antwort an
      .get() // Führt eine GET-Anfrage aus
      .map { response =>
        // Erfolgreiche HTTP-Antwort (Status 2xx)
        response.status match {
          case OK => // HTTP 200
            // Parse die JSON-Antwort. response.json gibt ein JsValue zurück.
            // .asOpt[JsObject] versucht, es sicher in ein JsObject zu casten.
            response.json.asOpt[JsObject] match {
              case Some(postJson) => Right(postJson)
              case None           => Left(AppError("err00053.wordpress.response.invalid", wpCPT, id))
            }
          case NOT_FOUND                => Left(AppError("err00054.wordpress.data.notfound", wpCPT, id))
          case UNAUTHORIZED | FORBIDDEN => Left(AppError("err00055.wordpress.notallowed"))
          case otherStatus              => Left(AppError("err00056.wordpress.error", otherStatus, response.body))
        }
      }.recover {
        case e: Exception => Left(AppError("err00052.wordpress.network.unexpected", e.getMessage))
      }
  }


  def putPostById(id: Int, field: String, value: String): Future[Either[AppError,Int]] = 
    val url = s"$wpDomain/wp-json/wp/v2/${wpCPT}/$id"
    ws.url(url)
      .withAuth(wpUsername, wpPassword, WSAuthScheme.BASIC) // Fügt Basic Authentication hinzu
      .withHttpHeaders("Content-Type" -> "application/json")
      .put(Json.obj("meta" -> Json.obj(field -> value)))
      .map { response =>
        response.status match {
          case OK => Right(value.length)
          case _  => Left(AppError("err00056.wordpress.error", response.status, response.body))
        }
      } recover {
        case ex: Throwable =>Left(AppError("err00052.wordpress.network.unexpected", ex.getMessage))
      }
  
 

}