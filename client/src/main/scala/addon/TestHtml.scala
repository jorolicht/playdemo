package addon

import upickle.default._

import base.*
import pages.*
import shared.basic.*
import shared.model.*
import shared.*

import services.Authentication

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import upickle.default.{ReadWriter => RW, macroRW}

import cviews.pages.html.Welcome

object TestHtml extends BasePage with Authentication with JsWrapper:
  def name = PageNameTyp("TestHtml")
  def render(param: String = ""): Boolean = true

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match 
      case 1 => testHtml_welcome(group, number, param)

      case _ => 
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknonw test number")))

 
  // http://localhost:9000/usecase/Console?param=test_--group_html_--number_1_--param_TestString
  def testHtml_welcome(group: String, number: Int, param: String): Future[Either[AppError, String]] = 
    val NAME = "testHtml_welcome"
    //setMain(s"""<div class='d-flex mt-5 justify-content-center'><h5>${param}</h5></div>""")
    setMain(Welcome("Robert", "Joe"))

    Future(Right(s"FINISHED ${NAME}: ${group}-Test:${number} param:${param}"))

    