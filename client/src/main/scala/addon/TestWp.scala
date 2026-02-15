package addon

import upickle.default._

import shared.basic.*
import base.*
import services.Authentication

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import upickle.default.{ReadWriter => RW, macroRW}




object TestWp extends Authentication:
  case class People(name: String, age: Int)

  object People:
    implicit val rw: RW[People] = macroRW     

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match 
      case 1 => testWp_saveJson(group, number, param)
      case 2 => testWp_readJson(group, number, param)
      case _ => 
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknonw test number")))
          
   
  def testWp_saveJson(group: String, number: Int, param: String): Future[Either[AppError, String]] = 
    val people = People("Robert", 63)
    ajaxPost[People, String]("/wp-json/tourney/v1/set-meta/ftp", List(("key","basic")), people,
                    Map("Content-Type"->"application/json", "X-WP-NONCE"->Global.nonce),
                    Global.homeUrl).map {
      case Left(err) => addOutput(s"ERROR: ${err}");        Left(err)
      case Right(res) => addOutput(s"RESULT: res->${res}"); Right(s"FINISHED: ${group}-Test:${number} param:${param}")
    }

  def testWp_readJson(group: String, number: Int, param: String): Future[Either[AppError, String]] = 

    ajaxGet[People]("/wp-json/tourney/v1/get-meta/ftp", List(("key","basic")), 
                    Map("Content-Type"->"application/json", "X-WP-NONCE"->Global.nonce),
                    Global.homeUrl).map {
      case Left(err) => addOutput(s"ERROR: ${err}");        Left(err)
      case Right(res) => addOutput(s"RESULT: res->${res}"); Right(s"FINISHED: ${group}-Test:${number} param:${param}")
    }    
