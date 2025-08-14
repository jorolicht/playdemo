package addon

import upickle.default._

import shared.model._
import shared._
import base._
import services.Authentication

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import upickle.default.{ReadWriter => RW, macroRW}


import scala.quoted.*


case class People(name: String, age: Int)

object People:
  implicit val rw: RW[People] = macroRW     


object TestBasic extends Authentication:

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match 
      case 1 => testBasic_checkEmail(group, number, param)
      case 2 => testBasic_checkPasswordFormat(group, number, param)
      case 3 => testBasic_parseJson(group, number, param)
      case 4 => testBasic_parseString(group, number, param)
      case 5 => testBasic_varName(group, number, param)
      case 6 => testBasic_getMsg(group, number, param)
      case _ => 
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknonw test number")))
        

  def testBasic_checkEmail(group: String, number: Int, param: String): Future[Either[AppError, String]] =                      
    if (isEmailValid(param)) then
      addOutput(s"email: ${param} valid")  
    else
      addOutput(s"email: ${param} invalid")
    Future(Right(s"FINISHED: ${group}-Test:${number} param:${param}")) 


  def testBasic_checkPasswordFormat(group: String, number: Int, param: String): Future[Either[AppError, String]] =                   
    if (isPasswordFormatValid(param)) then
      addOutput(s"password: ${param} valid")  
    else
      addOutput(s"password: ${param} invalid")
    Future(Right(s"FINISHED: ${group}-Test:${number} param:${param}"))     



  // http://localhost:9555/main/Console?param=test_--group_basic_--number_3
  def testBasic_parseJson(group: String, number: Int, param: String): Future[Either[AppError, String]] = 
    import upickle.default._

    parseJson[People]("""{"name":"Robert","age":50}""") match
      case Left(err)  => addOutput(s"ERROR: ${err}"); Future(Right("ERROR")) 
      case Right(res) => addOutput(s"RESULT: ${res.toString}"); Future(Right("RESULT"))  
 
  // http://localhost:9555/main/Console?param=test_--group_basic_--number_4_--param_TestString
  def testBasic_parseString(group: String, number: Int, param: String): Future[Either[AppError, String]] = 
    
    val jsonExample = toJson[String](param)
    addOutput(s"JSON EXAMPLE: ${jsonExample}")

    parseJson[String](jsonExample) match
      case Left(err)  => addOutput(s"ERROR: ${err}"); Future(Right("ERROR")) 
      case Right(res) => addOutput(s"RESULT: ${res.toString}"); Future(Right("RESULT")) 
  
  // Scala 3 Macro creating a variable that assigns the variable's name as its value     
  // http://localhost:9555/main/Console?param=test_--group_basic_--number_5_--param_TestString
  def testBasic_varName(group: String, number: Int, param: String): Future[Either[AppError, String]] = 

    object getMethodName: 
      override def toString = this.getClass.getSimpleName //.split("\\$").last

    addOutput(s"getMethodName: ${getMethodName}")  
    Future(Right(s"FINISHED: ${group}-Test:${number} param:${param}"))  


  // Test getMsg  
  // http://localhost:9555/main/Console?param=test_--group_basic_--number_6_--param_app.date
  def testBasic_getMsg(group: String, number: Int, param: String): Future[Either[AppError, String]] = 

    ajaxGet[String]("/helper/getMsg", List(("msgCode", param))).map {
      case Left(err) => addOutput(s"ERROR: ${err}");        Left(err)
      case Right(res) => addOutput(s"RESULT: res->${res}"); Right(s"FINISHED: ${group}-Test:${number} param:${param}")
    }
   
