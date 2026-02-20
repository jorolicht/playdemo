package addon

import upickle.default._

import shared.model._
import shared.basic.AppError
import base._
import services.Authentication

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import upickle.default.{ReadWriter => RW, macroRW}

case class People2(name: String, age: Int)

object People2:
  implicit val rw: RW[People2] = macroRW     


object TestWp extends Authentication:

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match 
      case 1 => testWp_saveJson(group, number, param)
      case 2 => testWp_readJson(group, number, param)
      case _ => 
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknonw test number")))
          


  // http://localhost:9555/main/Console?param=test_--group_basic_--number_6_--param_app.date
  // def testWp_saveJson(group: String, number: Int, param: String): Future[Either[AppError, String]] = 
  //   val data = """{
  //         "titel": "Profil von Robert127",
  //         "content": {
  //           "person": {
  //             "name": "Robert124",
  //             "alter": 35,
  //             "beruf": "Top Entwickler"
  //           }
  //         }
  //        }"""
  //   ajaxPost[String]("/wp-json/tourney/v1/save-json/ttc/2026_02_01/127_person", List(), data,
  //                   Map("Content-Type"->"application/json", "X-WP-NONCE"->Global.nonce),
  //                   Global.homeUrl).map {
  //     case Left(err) => addOutput(s"ERROR: ${err}");        Left(err)
  //     case Right(res) => addOutput(s"RESULT: res->${res}"); Right(s"FINISHED: ${group}-Test:${number} param:${param}")
  //  }
   
  def testWp_saveJson(group: String, number: Int, param: String): Future[Either[AppError, String]] = 
    val people = People2("Robert2", 63)
    ajaxPost2[String, People2]("/wp-json/tourney/v1/set-meta/ftp", List(("key","basic")), people,
                    Map("Content-Type"->"application/json", "X-WP-NONCE"->Global.nonce),
                    Global.homeUrl).map {
      case Left(err) => addOutput(s"ERROR: ${err}");        Left(err)
      case Right(res) => addOutput(s"RESULT: res->${res}"); Right(s"FINISHED: ${group}-Test:${number} param:${param}")
    }

  def testWp_readJson(group: String, number: Int, param: String): Future[Either[AppError, String]] = 

    ajaxGet[People2]("/wp-json/tourney/v1/get-meta/ftp", List(("key","basic")), 
                    Map("Content-Type"->"application/json", "X-WP-NONCE"->Global.nonce),
                    Global.homeUrl).map {
      case Left(err) => addOutput(s"ERROR: ${err}");        Left(err)
      case Right(res) => addOutput(s"RESULT: res->${res}"); Right(s"FINISHED: ${group}-Test:${number} param:${param}")
    }    
