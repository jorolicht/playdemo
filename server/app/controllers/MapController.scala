package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.libs.ws.*
import play.api.libs.json.*
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.concurrent.TrieMap
import org.apache.pekko.util.ByteString

/**
 * DSGVO / GDPR Compliant Map Controller (Alternative 3).
 *
 * Serves map tiles, geocoding, and static map images directly from the Play server
 * by proxying open-data tile providers server-to-server.
 *
 * This ensures that client browsers never send IP addresses or cookies to third-party servers.
 */
@Singleton
class MapController @Inject()(
    cc: ControllerComponents,
    ws: WSClient
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  // In-memory cache for map tiles (tileKey -> (bytes, contentType))
  private val tileCache = TrieMap[String, (ByteString, String)]()

  // In-memory cache for geocoding queries (query -> (lat, lon, displayName))
  private val geocodeCache = TrieMap[String, (Double, Double, String)]()

  /**
   * Serverseitiger Tile-Proxy (GET /api/map/tile/:z/:x/:y)
   *
   * Fetches map tiles server-to-server and serves them from the Play server.
   */
  def tile(z: Int, x: Int, y: Int): Action[AnyContent] = Action.async { implicit request =>
    val cacheKey = s"$z/$x/$y"

    tileCache.get(cacheKey) match {
      case Some((bytes, contentType)) =>
        Future.successful(
          Ok(bytes)
            .as(contentType)
            .withHeaders("Cache-Control" -> "public, max-age=86400")
        )

      case None =>
        val tileUrl = s"https://tile.openstreetmap.org/$z/$x/$y.png"
        ws.url(tileUrl)
          .withHttpHeaders("User-Agent" -> "PlaydemoMapProxy/1.0 (PlayFramework Server-Side Map Proxy)")
          .withRequestTimeout(scala.concurrent.duration.Duration(5, "seconds"))
          .get()
          .map { response =>
            if (response.status == 200) {
              val bytes = response.bodyAsBytes
              val contentType = response.contentType
              if (tileCache.size < 5000) {
                tileCache.put(cacheKey, (bytes, contentType))
              }
              Ok(bytes)
                .as(contentType)
                .withHeaders("Cache-Control" -> "public, max-age=86400")
            } else {
              NotFound("Kachel nicht gefunden")
            }
          }
          .recover {
            case _: Exception =>
              InternalServerError("Fehler beim Laden der Karten-Kachel")
          }
    }
  }

  /**
   * Serverseitiges Geocoding (GET /api/map/geocode)
   *
   * Resolves address query to lat/lon coordinates server-to-server.
   */
  def geocode(query: String): Action[AnyContent] = Action.async { implicit request =>
    val cleanQuery = query.trim.toLowerCase
    if (cleanQuery.isEmpty) {
      Future.successful(BadRequest(Json.obj("error" -> "Query string is empty")))
    } else {
      geocodeCache.get(cleanQuery) match {
        case Some((lat, lon, name)) =>
          Future.successful(Ok(Json.obj(
            "lat" -> lat,
            "lon" -> lon,
            "display_name" -> name,
            "cached" -> true
          )))

        case None =>
          val url = "https://nominatim.openstreetmap.org/search"
          ws.url(url)
            .withQueryStringParameters("q" -> query, "format" -> "json", "limit" -> "1")
            .withHttpHeaders("User-Agent" -> "PlaydemoServer/1.0 (PlayFramework DSGVO Map Proxy)")
            .withRequestTimeout(scala.concurrent.duration.Duration(5, "seconds"))
            .get()
            .map { response =>
              if (response.status == 200) {
                val json = response.json
                if (json.asOpt[JsArray].exists(_.value.nonEmpty)) {
                  val first = (json \ 0).get
                  val latStr = (first \ "lat").asOpt[String].getOrElse("0.0")
                  val lonStr = (first \ "lon").asOpt[String].getOrElse("0.0")
                  val lat = latStr.toDoubleOption.getOrElse(0.0)
                  val lon = lonStr.toDoubleOption.getOrElse(0.0)
                  val name = (first \ "display_name").asOpt[String].getOrElse(query)

                  geocodeCache.put(cleanQuery, (lat, lon, name))
                  Ok(Json.obj(
                    "lat" -> lat,
                    "lon" -> lon,
                    "display_name" -> name,
                    "cached" -> false
                  ))
                } else {
                  NotFound(Json.obj("error" -> "Adresse nicht gefunden"))
                }
              } else {
                InternalServerError(Json.obj("error" -> "Geocoding Service nicht erreichbar"))
              }
            }
            .recover {
              case e: Exception =>
                InternalServerError(Json.obj("error" -> s"Geocoding Fehler: ${e.getMessage}"))
            }
      }
    }
  }

  /**
   * Serverseitige statische Vorschaukarte (GET /api/map/static)
   *
   * Delivers a static map tile image directly from Play server for a given location query or lat/lon.
   */
  def staticMap(lat: Option[Double], lon: Option[Double], query: Option[String], zoom: Option[Int]): Action[AnyContent] = Action.async { implicit request =>
    val z = zoom.getOrElse(15)

    def fetchTileForCoords(latitude: Double, longitude: Double): Future[Result] = {
      val n = Math.pow(2, z)
      val xtile = Math.floor((longitude + 180.0) / 360.0 * n).toInt
      val latRad = Math.toRadians(latitude)
      val ytile = Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt

      val cacheKey = s"$z/$xtile/$ytile"
      tileCache.get(cacheKey) match {
        case Some((bytes, contentType)) =>
          Future.successful(Ok(bytes).as(contentType).withHeaders("Cache-Control" -> "public, max-age=86400"))
        case None =>
          val tileUrl = s"https://tile.openstreetmap.org/$z/$xtile/$ytile.png"
          ws.url(tileUrl)
            .withHttpHeaders("User-Agent" -> "PlaydemoMapProxy/1.0")
            .get()
            .map { res =>
              if (res.status == 200) {
                val bytes = res.bodyAsBytes
                val contentType = res.contentType
                tileCache.put(cacheKey, (bytes, contentType))
                Ok(bytes).as(contentType).withHeaders("Cache-Control" -> "public, max-age=86400")
              } else {
                NotFound("Static map tile not found")
              }
            }
      }
    }

    (lat, lon) match {
      case (Some(la), Some(lo)) =>
        fetchTileForCoords(la, lo)
      case _ =>
        query match {
          case Some(q) if q.trim.nonEmpty =>
            ws.url("https://nominatim.openstreetmap.org/search")
              .withQueryStringParameters("q" -> q, "format" -> "json", "limit" -> "1")
              .withHttpHeaders("User-Agent" -> "PlaydemoServer/1.0")
              .get()
              .flatMap { response =>
                if (response.status == 200 && (response.json \ 0).isDefined) {
                  val first = (response.json \ 0).get
                  val latStr = (first \ "lat").asOpt[String].getOrElse("0.0")
                  val lonStr = (first \ "lon").asOpt[String].getOrElse("0.0")
                  val la = latStr.toDoubleOption.getOrElse(51.1657)
                  val lo = lonStr.toDoubleOption.getOrElse(10.4515)
                  fetchTileForCoords(la, lo)
                } else {
                  fetchTileForCoords(51.1657, 10.4515)
                }
              }
              .recoverWith { case _ => fetchTileForCoords(51.1657, 10.4515) }
          case _ =>
            fetchTileForCoords(51.1657, 10.4515)
        }
    }
  }
}
