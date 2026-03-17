package addon

import java.util.UUID
import cats.data.EitherT
import cats.syntax.all._  
import scala.util.control.NonFatal
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import shared.model.User
import shared.basic.*
import base.*
import services.*

val authMode: AuthMode = AuthMode.AppPassword
val wpUserName: String = "robert"
val wpAppPassword: String = "mx5dkwkidnTmNavhDnSeVmqK"

def getTokenInfo(using cw: ComWrapper): Future[Either[AppError, String]] =
  JwtService.getToken.map {
    case Right(token) =>
      printTokenInfo(token)
      Right("Token info printed to console")
    case Left(err) =>
      Left(err)
  } 
  
def printTokenInfo(token: String): Unit =
  try
      val parts = token.split("\\.")

      if parts.length < 2 then
        println("JWT Debug: invalid token format")
        return

      val headerJson = new String(java.util.Base64.getUrlDecoder.decode(parts(0)), "UTF-8")
      val payloadJson = new String(java.util.Base64.getUrlDecoder.decode(parts(1)), "UTF-8")

      println("---- JWT DEBUG ----")
      println(s"header : $headerJson")
      println(s"payload: $payloadJson")

      JwtService.decodePayload(token).foreach { p =>
        val now = System.currentTimeMillis() / 1000
        val ttl = p.exp - now

        println(s"user_id : ${p.user_id}")
        println(s"issued  : ${p.iat}")
        println(s"expires : ${p.exp}")
        println(s"ttl(sec): $ttl")
      }

      println("-------------------")

  catch
      case NonFatal(e) =>  println(s"JWT Debug failed: ${e.getMessage}")    



object TestAuth extends Authentication with ComWrapper with JsWrapper:

  def exec(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    number match 
      case 1  => testBasic_sha256(group, number, param)
      case 2  => testBasic_checkPasswordFormat(group, number, param)
      case 3  => testBasic_regUser(group, number, param)
      case 4  => testBasic_login(group, number, param)
      case 5  => testBasic_verifyUser(group, number, param)
      case 6  => testBasic_getVerifyLink(group, number, param)
      case 7  => testBasic_getUserInfo(group, number, param)
      case 8  => testBasic_setUserPassword(group, number, param)
      case 9  => testBasic_checkUserAuth(group, number, param)
      case 10 => testBasic_getJwt(group, number, param)
      case _ => 
        addOutput(s"FAILED: ${group}-Test:${number} param:${param} unknown test number")
        Future(Left(AppError("unknonw test number")))
        
  // http://localhost:9000/usecase/Console?param=test_--group_auth_--number_1_--param_simple
  def testBasic_sha256(group: String, number: Int, param: String): Future[Either[AppError, String]] =   
    sha256(param).map { 
      case Left(error)  =>
        addOutput(s"${param} Error: ${error}") 
        Left(error)
      case Right(hash) =>  
        addOutput(s"${param} hash: ${hash}")
        Right(s"FINISHED: ${group}-Test:${number} param:${param}")
    }

  // http://localhost:9000/usecase/Console?param=test_--group_auth_--number_2_--param_simple  
  def testBasic_checkPasswordFormat(group: String, number: Int, param: String): Future[Either[AppError, String]] =                   
    if (isPasswordFormatValid(param)) then
      addOutput(s"password: ${param} valid")  
    else
      addOutput(s"password: ${param} invalid")
    Future(Right(s"FINISHED: ${group}-Test:${number} param:${param}"))

  // http://localhost:9000/usecase/Console?param=test_--group_auth_--number_3_--param_ro.licht@xx.com  
  def testBasic_regUser(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val email = param
    val password = "Abc1234567"

    (for
      pw   <- EitherT[Future,AppError,String](sha256(password))
      usr  <- EitherT[Future,AppError,User](regUser(User(newUuidString, email, "Robert", "Lichtenegger"), pw))
    yield usr).value.map {
      case Left(err)   => addOutput(s"ERROR: ${err}");         Left(err)
      case Right(user) => addOutput(s"RESULT: user->${user}"); Right(s"FINISHED: ${group}-Test:${number} param:${param}")
    }

  // http://localhost:9000/usecase/Console?param=test_--group_auth_--number_4_--param_email;password  
  def testBasic_login(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val NAME = "testBasic_login"
    val (email,password) = param.toTuple(";")

    basicLogin(email, password).map {
      case Left(err)  => addOutput(s"ERROR: ${err}");       Left(err)
      case Right(res) => addOutput(s"RESULT: user:${res}"); Right(s"FINISHED: ${NAME} ${group}-Test:${number} param:${param} result:${res}")      
    }


  // http://localhost:9000/usecase/Console?param=test_--group_auth_--number_5_--param_6;true  
  def testBasic_verifyUser(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    import shared.model.User
    val NAME = "testBasic_verifyUser"

    val id    = (param.split(";")(0)).toLong
    val value = (param.split(";")(1)).toBoolean

    ajaxPost[String, Int]("/test/auth/setUserVerify", List(("id", id.toString),("value", value.toString)), "").map {
      case Left(err)  => addOutput(s"ERROR: ${err}");     Left(err)
      case Right(res) => addOutput(s"RESULT: res->${res}"); Right(s"FINISHED: ${NAME} ${group}-Test:${number} param:${param}")   
    }

  // http://localhost:9000/usecase/Console?param=test_--group_auth_--number_6_--param_id;10  
  def testBasic_getVerifyLink(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    var email = ""
    var id    = 0L
    val iTyp = param.split(";")(0)
    val value  = param.split(";")(1)

    if (iTyp=="email") email = value 
    else if (iTyp=="id") id = value.toLongOption.getOrElse(0L)
    
    ajaxGet[String]("/test/auth/getUserVerifyLink", List(("email", email),("id", id.toString))).map {
      case Left(err) => addOutput(s"ERROR: ${err}");        Left(err)
      case Right(res) => addOutput(s"RESULT: res->${res}"); Right(s"FINISHED: ${group}-Test:${number} param:${param}")
    }


  // http://localhost:9000/usecase/Console?param=test_--group_auth_--number_7_--param_id;10  
  def testBasic_getUserInfo(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    var email = ""
    var id    = 0L
    val iTyp = param.split(";")(0)
    val value  = param.split(";")(1)

    if (iTyp=="email") email = value 
    else if (iTyp=="id") id = value.toLongOption.getOrElse(0L)
    
    addOutput(s"PARAMETER: email->${email} id->${id}")
    ajaxGet[User]("/test/auth/getUserInfo", List(("email", email),("id",id.toString))).map {
      case Left(err)   => addOutput(s"ERROR: ${err}");     Left(err)
      case Right(user) => addOutput(s"RESULT: user->${user}"); Right(s"FINISHED: ${group}-Test:${number} param:${param}")
    }


  // http://localhost:9000/usecase/Console?param=test_--group_auth_--number_8_--param_ro.licht@xx.com;Abc123  
  def testBasic_setUserPassword(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val NAME = "testBasic_setUserPassword"

    val (email,password) = param.toTuple(";")
    setUserPassword(email, password).map {
      case Left(err) => addOutput(s"ERROR: ${err}");     Left(err)
      case Right(ok) => addOutput(s"RESULT: ok->${ok}"); Right(s"FINISHED: ${NAME} ${group}-Test:${number} param:${param}")
    }

  // http://localhost:9000/usecase/Console?param=test_--group_auth_--number_9_--param_xx  
  def testBasic_checkUserAuth(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val NAME = "testBasic_checkUserAuth"

    val (email,password) = param.toTuple(";")
    ajaxGet[String]("/test/auth/checkUserAuth", List()).map {
      case Left(err)   => addOutput(s"ERROR: ${err}");       Left(err)
      case Right(res)  => addOutput(s"RESULT: res->${res}"); Right(s"FINISHED ${NAME}: ${group}-Test:${number} param:${param}")
    }

  // http://localhost:9000/usecase/Console?param=test_--group_auth_--number_10_--param_xx  
  def testBasic_getJwt(group: String, number: Int, param: String): Future[Either[AppError, String]] =
    val NAME = "testBasic_getJwt"

    Global.authMode = AuthMode.AppPassword
    Global.wpUserName = wpUserName
    Global.wpAppPassword = wpAppPassword

    object DefaultComWrapper extends ComWrapper
    given ComWrapper = DefaultComWrapper

    getTokenInfo.map {
      case Left(err)   => addOutput(s"ERROR: ${err}");       Left(err)
      case Right(res)  => addOutput(s"RESULT: res->${res}"); Right(s"FINISHED ${NAME}: ${group}-Test:${number} param:${param}")
    } 

   

