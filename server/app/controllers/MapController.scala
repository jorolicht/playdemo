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

  /** Breite und Höhe der statischen Karte in Pixeln. */
  private val MapWidth  = 640
  private val MapHeight = 320

  /** Kachelgröße der OSM-Kacheln in Pixeln. */
  private val TileSize = 256

  // Cache der zusammengesetzten Karten (z/lat/lon -> PNG) und der nicht gefundenen Adressen
  private val staticCache = TrieMap[String, ByteString]()
  private val geocodeMiss = TrieMap[String, Unit]()

  /**
   * Ermittelt die Koordinaten einer Adresse über Nominatim (mit Cache, auch für nicht gefundene Adressen).
   *
   * @param query Adresse
   * @return (Breite, Länge) oder None, wenn die Adresse nicht gefunden wurde
   */
  private def lookup(query: String): Future[Option[(Double, Double)]] = {
    val key = query.trim.toLowerCase
    geocodeCache.get(key) match {
      case Some((lat, lon, _)) => Future.successful(Some((lat, lon)))
      case None if geocodeMiss.contains(key) => Future.successful(None)
      case None =>
        ws.url("https://nominatim.openstreetmap.org/search")
          .withQueryStringParameters("q" -> query, "format" -> "json", "limit" -> "1")
          .withHttpHeaders("User-Agent" -> "PlaydemoServer/1.0 (PlayFramework DSGVO Map Proxy)")
          .withRequestTimeout(scala.concurrent.duration.Duration(5, "seconds"))
          .get()
          .map { response =>
            val first = if (response.status == 200) response.json.asOpt[JsArray].flatMap(_.value.headOption) else None
            val coords = first.flatMap { f =>
              for {
                lat <- (f \ "lat").asOpt[String].flatMap(_.toDoubleOption)
                lon <- (f \ "lon").asOpt[String].flatMap(_.toDoubleOption)
              } yield (lat, lon, (f \ "display_name").asOpt[String].getOrElse(query))
            }
            coords match {
              case Some((lat, lon, name)) =>
                geocodeCache.put(key, (lat, lon, name))
                Some((lat, lon))
              case None =>
                // nur echte "nicht gefunden"-Antworten merken, keine Ausfälle des Dienstes
                if (response.status == 200 && geocodeMiss.size < 5000) geocodeMiss.put(key, ())
                None
            }
          }
          .recover { case _: Exception => None }
    }
  }

  /**
   * Lädt eine OSM-Kachel (mit Cache).
   *
   * @return Bild der Kachel oder None bei Fehler
   */
  private def tileImage(z: Int, x: Int, y: Int): Future[Option[java.awt.image.BufferedImage]] = {
    val n = 1 << z
    if (y < 0 || y >= n) Future.successful(None)
    else {
      val xw = ((x % n) + n) % n
      val cacheKey = s"$z/$xw/$y"
      val bytesF: Future[Option[ByteString]] = tileCache.get(cacheKey) match {
        case Some((bytes, _)) => Future.successful(Some(bytes))
        case None =>
          ws.url(s"https://tile.openstreetmap.org/$z/$xw/$y.png")
            .withHttpHeaders("User-Agent" -> "PlaydemoMapProxy/1.0 (PlayFramework Server-Side Map Proxy)")
            .withRequestTimeout(scala.concurrent.duration.Duration(5, "seconds"))
            .get()
            .map { res =>
              if (res.status == 200) {
                val bytes = res.bodyAsBytes
                if (tileCache.size < 5000) tileCache.put(cacheKey, (bytes, res.contentType))
                Some(bytes)
              } else None
            }
            .recover { case _: Exception => None }
      }
      bytesF.map(_.flatMap(b => Option(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(b.toArray)))))
    }
  }

  /**
   * Setzt eine Karte (MapWidth × MapHeight) mit dem Ort in der Mitte, einem Marker und dem OSM-Hinweis zusammen.
   *
   * @param lat  geographische Breite
   * @param lon  geographische Länge
   * @param z    Zoomstufe
   * @return PNG-Bild
   */
  private def renderMap(lat: Double, lon: Double, z: Int): Future[ByteString] = {
    val worldPx = TileSize.toDouble * (1 << z)
    val latRad  = Math.toRadians(lat)
    val px = (lon + 180.0) / 360.0 * worldPx
    val py = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * worldPx
    val left = px - MapWidth / 2.0
    val top  = py - MapHeight / 2.0
    val tiles = for {
      tx <- Math.floor(left / TileSize).toInt to Math.floor((left + MapWidth - 1) / TileSize).toInt
      ty <- Math.floor(top / TileSize).toInt to Math.floor((top + MapHeight - 1) / TileSize).toInt
    } yield (tx, ty)

    Future.traverse(tiles) { case (tx, ty) => tileImage(z, tx, ty).map(img => (tx, ty, img)) }.map { loaded =>
      import java.awt.{BasicStroke, Color, Font, RenderingHints}
      import java.awt.geom.{Ellipse2D, Path2D}
      val img = new java.awt.image.BufferedImage(MapWidth, MapHeight, java.awt.image.BufferedImage.TYPE_INT_RGB)
      val g = img.createGraphics()
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
      g.setColor(new Color(0xE5, 0xE3, 0xDF))
      g.fillRect(0, 0, MapWidth, MapHeight)
      loaded.foreach { case (tx, ty, t) =>
        t.foreach(ti => g.drawImage(ti, Math.round(tx * TileSize - left).toInt, Math.round(ty * TileSize - top).toInt, null))
      }

      // Marker (Stecknadel), Spitze genau auf dem Ort
      val cx = MapWidth / 2.0
      val cy = MapHeight / 2.0
      val r  = 11.0
      val pin = new Path2D.Double()
      pin.moveTo(cx, cy)
      pin.curveTo(cx - 4, cy - 10, cx - r, cy - 16, cx - r, cy - 25)
      pin.curveTo(cx - r, cy - 38, cx + r, cy - 38, cx + r, cy - 25)
      pin.curveTo(cx + r, cy - 16, cx + 4, cy - 10, cx, cy)
      pin.closePath()
      g.setColor(new Color(0, 0, 0, 60))
      g.fill(new Ellipse2D.Double(cx - 7, cy - 3, 14, 6))
      g.setColor(new Color(0xDC, 0x35, 0x45))
      g.fill(pin)
      g.setColor(new Color(0x8B, 0x1A, 0x26))
      g.setStroke(new BasicStroke(1.5f))
      g.draw(pin)
      g.setColor(Color.WHITE)
      g.fill(new Ellipse2D.Double(cx - 4.5, cy - 29.5, 9, 9))

      // Namensnennung (Lizenz ODbL)
      val text = "© OpenStreetMap-Mitwirkende"
      g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11))
      val fm = g.getFontMetrics
      val tw = fm.stringWidth(text)
      g.setColor(new Color(255, 255, 255, 200))
      g.fillRect(MapWidth - tw - 8, MapHeight - fm.getHeight - 2, tw + 8, fm.getHeight + 2)
      g.setColor(new Color(0x33, 0x33, 0x33))
      g.drawString(text, MapWidth - tw - 4, MapHeight - fm.getDescent - 2)
      g.dispose()

      val out = new java.io.ByteArrayOutputStream()
      javax.imageio.ImageIO.write(img, "png", out)
      ByteString(out.toByteArray)
    }
  }

  /**
   * Serverseitige statische Vorschaukarte (GET /api/map/static)
   *
   * Liefert ein PNG (640 × 320) mit dem Ort in der Mitte, Marker und OSM-Namensnennung. Die Kacheln und die
   * Adresssuche werden serverseitig geladen, der Browser kontaktiert keine Dritt-Server (DSGVO).
   *
   * @param lat   geographische Breite (zusammen mit lon statt query)
   * @param lon   geographische Länge
   * @param query Adresse, wird über Nominatim aufgelöst
   * @param zoom  Zoomstufe (3–18, Default 15)
   * @return PNG, 404 wenn die Adresse nicht gefunden wurde (der Client blendet die Karte dann aus)
   */
  def staticMap(lat: Option[Double], lon: Option[Double], query: Option[String], zoom: Option[Int]): Action[AnyContent] = Action.async { implicit request =>
    val z = zoom.getOrElse(15).max(3).min(18)
    val coordsF: Future[Option[(Double, Double)]] = (lat, lon) match {
      case (Some(la), Some(lo)) => Future.successful(Some((la, lo)))
      case _ => query.filter(_.trim.nonEmpty).map(lookup).getOrElse(Future.successful(None))
    }
    coordsF.flatMap {
      case Some((la, lo)) if la.abs <= 85 && lo.abs <= 180 =>
        val key = f"$z/$la%.5f/$lo%.5f"
        val pngF = staticCache.get(key) match {
          case Some(png) => Future.successful(png)
          case None => renderMap(la, lo, z).map { png => if (staticCache.size < 500) staticCache.put(key, png); png }
        }
        pngF.map(png => Ok(png).as("image/png").withHeaders("Cache-Control" -> "public, max-age=86400"))
      case _ =>
        Future.successful(NotFound("Adresse nicht gefunden"))
    }.recover { case _: Exception => InternalServerError("Karte konnte nicht erzeugt werden") }
  }
}
