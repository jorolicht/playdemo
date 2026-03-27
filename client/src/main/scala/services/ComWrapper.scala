package services

import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.reflect.ClassTag
import shared.basic.Pickle._

import base.Global
import base.Logging.*
import base.*
import shared.basic.*


// PlayerApi - example of an authenticated API call using JWT token
// object PlayerApi:
//   def list()(using cw: ComWrapper): Future[Either[AppError,List[Player]]] =
//     AuthComWrapper.ajaxGetAuth[List[Player]](
//       "/wp-json/tourney/v1/players"
//     )

// Example usage of PlayerApi.list() - this would typically be called from a component or service that needs the player list
// PlayerApi.list().map {
//   case Right(players) =>
//     println(players)
//   case Left(err) =>
//     println(err)
// }

// Example of how to use the ComWrapper for an authenticated POST request
// case class CreatePlayer(name: String)
// case class Player(id: Int, name: String)

// AuthComWrapper.ajaxPostAuth[CreatePlayer, Player](
//   "/wp-json/tourney/v1/player",
//   data = CreatePlayer("Roger")
// )

object AuthComWrapper:

  def ajaxGetAuth[T](
      route: String,
      params: List[(String,String)] = Nil
  )(using cw: ComWrapper, r: Reader[T], ct: ClassTag[T]): Future[Either[AppError,T]] =

    JwtService.getToken.flatMap {
      case Right(token) =>
        cw.ajaxGet[T](
          route,
          params,
          hdrs = Map("Authorization" -> s"Bearer $token"),
          host = Global.homeUrl
        )
      case Left(err) =>
        Future.successful(Left(err))
    }

  def ajaxPostAuth[IN, OUT](
      route: String,
      params: List[(String,String)] = Nil,
      data: IN,
      hdrs: Map[String,String] = Map("Content-Type" -> "application/json")
  )(using cw: ComWrapper, r: Reader[OUT], ct: ClassTag[OUT], w: Writer[IN]): Future[Either[AppError, OUT]] =

    JwtService.getToken.flatMap {
      case Right(token) =>
        val headers = hdrs ++ Map("Authorization" -> s"Bearer $token")

        cw.ajaxPost[IN, OUT](
          route = route,
          params = params,
          data = data,
          hdrs = headers,
          host = Global.homeUrl
        )

      case Left(err) =>
        Future.successful(Left(err))
    }



trait ComWrapper: 

  def ajaxPost[IN, OUT](
    route: String,
    params: List[(String,String)],
    data: IN,
    hdrs: Map[String,String] =
      Map("Content-Type" -> "application/json", "Csrf-Token" -> Global.csrf),
    host: String = Global.playUrl,
    cred: Boolean = true
  )(
    using
      r: Reader[OUT],
      ct: ClassTag[OUT],
      w: Writer[IN]
  ): Future[Either[AppError, OUT]] =
      val name = route.split("/").lastOption.getOrElse("ajaxPost")
      debug(
        s"ajaxPost -> route:$route " +
        s"params:${params.mkString(", ")} " +
        s"data:${write(data).take(20)} " +
        s"hdrs:${hdrs.mkString(", ")}"
      )
      Ajax.post(genPath(host, route, params), write(data), headers = hdrs, withCredentials = cred)
        .map(_.responseText).map(content => Return.decode[OUT](content) )
        .recover({
          // Recover from a failed error code into a successful future
          case dom.ext.AjaxException(req) => Left(parseError(req.responseText, name))   
          case _: Throwable               => Left(AppError("err00001.ajax.post", s"${route}/${params.mkString(":")}", "request status unknown", name))    
        })

  /** ajaxGet - basic wrapper for get requests   
   * @return either an error or a result type T 
   */
  def ajaxGet[T](route: String, params: List[(String,String)]=List(), hdrs: Map[String,String]=Map(), host: String = Global.playUrl)
                (using r: Reader[T], ct: ClassTag[T]): Future[Either[AppError,T]] =
    val name = route.split("/").lastOption.getOrElse("ajaxGet")
    debug(s"ajaxGet -> route:${route} params:${params.mkString("=")} hdrs: ${hdrs.mkString("=")}")
    Ajax.get(genPath(host, route, params), headers = hdrs).map(_.responseText)
      .map(content => parseJson[T](content) )  
      .recover({
        case dom.ext.AjaxException(req) => Left(parseError(req.responseText, name)) 
        case _: Throwable               => Left(AppError("err00009.ajax.get", s"${route}/${params.mkString(":")}", "request status unknown", name))   
    })


  // genPath - encodes params to URL encoded 
  def genPath(host: String, route: String, params: List[(String,String)]): String = 
    val urlParams = params.map(x => s"${x._1}=${x._2}").mkString("&") 
    if (params.isEmpty) s"${host}${route}" else s"${host}${route}?${urlParams}"