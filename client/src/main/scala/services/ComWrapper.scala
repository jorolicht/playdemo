package services

import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.reflect.ClassTag
import upickle.default._

import base.Global
import base.*
import shared._
import shared.model._

trait ComWrapper: 

  // ajaxPost - basic wrapper routine for a Ajax post request 
  def ajaxPost[T](route: String, params: List[(String,String)], data: String, 
                  hdrs: Map[String,String]=Map("Content-Type"->"text/plain; charset=utf-8", "Csrf-Token" -> Global.csrf),
                  host: String=Global.server)
                 (using r: Reader[T], ct: ClassTag[T]): Future[Either[AppError,T]] = 
    val name = route.split("/").lastOption.getOrElse("ajaxPost")
    debug(s"ajaxPost -> route:${route} params:${params.mkString("=")} data:${data.take(20)} hdrs: ${hdrs.mkString("=")}")
    Ajax.post(genPath(host, route, params), data, headers = hdrs)
      .map(_.responseText).map(content => parseJson[T](content) )
      .recover({
        // Recover from a failed error code into a successful future
        case dom.ext.AjaxException(req) => Left(parseError(req.responseText, name))   
        case _: Throwable               => Left(AppError("err00001.ajax.post", s"${route}/${params.mkString(":")}", "request status unknown", name))    
      })


  /** ajaxGet - basic wrapper for get requests   
   * @return either an error or a result type T 
   */
  def ajaxGet[T](route: String, params: List[(String,String)]=List(), hdrs: Map[String,String]=Map(), host: String = Global.server)
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