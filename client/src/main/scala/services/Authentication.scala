package services



import upickle.default._
import scala.scalajs.js
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax

import cats.data.EitherT
import cats.syntax.all._ 

import base._
import shared._
import shared.model._

trait Authentication extends ComWrapper: 

  def toBytes(hex: String): Seq[Byte] = hex.sliding(2, 2).map(Integer.parseInt(_, 16).toByte).toSeq

  def isEmailValid(email: String): Boolean = 
    import scala.util.matching.Regex
    val emailMatch = """([\w\.!#$%&*+/=?^_`{|}~-]+)@([\w]+)([\.]{1}[\w]+)+""".r 
    emailMatch.pattern.matcher(email).matches

  def isPasswordFormatValid(password: String): Boolean = 
    (password.length >= 6) && password.matches(".*[a-z].*") &&
    password.matches(".*[A-Z].*") && password.matches(".*[0-9].*")

  def sha256(text: String): FuEiErr[String]  = 
    js.Dynamic.global.sha256(text.asInstanceOf[js.Any]).asInstanceOf[js.Promise[String]]
      .toFuture
      .map { res => Right(res) }
      .recover { case error => Left(AppError("", error)) }

  /** login returns a valid user or error, basic authentication
    * 
    * @param email
    * @param password
    * @return
    */ 
  def basicLogin(email: String, password: String): FuEiErr[User] =

    def doBasicLogin(authCode: String):FuEiErr[User] =
      val path = s"/auth/basicLogin"
      Ajax.get(path, headers = Map("Authorization"-> authCode)).map(_.responseText).map(user => {
        parseJson[User](user) 
      }).recover({
        // Recover from a failed error code into a successful future
        case dom.ext.AjaxException(req) => Left(parseError(req.responseText, "doBasicLogin") )
        case _: Throwable               => Left(AppError("err00003.ajax.login", "noResponse", "noStatus"))
      })

    (for 
      pwEnc <- EitherT[Future,AppError,String](sha256(password))
      usr   <- EitherT[Future,AppError,User](doBasicLogin("Basic " + utob(s"${email}:${pwEnc}")))
    yield usr).value

  /** regUser a user, returns auto generated id email have to be unique
    * 
    * @param user 
    * @return usr with auto generated user id
    */ 
  def regUser(user: User, password: String): FuEiErr[User] = 
    (for 
      pwEnc <- EitherT[Future,AppError,String](sha256(password))
      usr   <- EitherT[Future,AppError,User]( ajaxPost[User]("/auth/regUser", List(), toJson(user.copy(password=pwEnc))) )   
    yield usr).value  
    
  /** logout a user
    * 
    * @param user 
    * @return usr with auto generated user id
    */ 
  def logout(user: User): FuEiErr[Boolean] = 
    ajaxPost[Boolean]("/auth/logout", List(), toJson(user))


  /** setUserPassword - set users password
    * 
    * @param email
    * @param password
    * @return
    */ 
  def setUserPassword(email: String, password: String): FuEiErr[Int] = 
    (for 
      pwEnc <- EitherT[Future,AppError,String](sha256(password))
      res   <- EitherT[Future,AppError,Int]( ajaxPost[Int]("/auth/setUserPassword", List(("email", email)), pwEnc) )
    yield res).value

  /** setUserVerify set users verify flag
    * 
    * @param email 
    * @return result of sql statment
    */ 
  def setUserVerify(email: String, value: Boolean=true): FuEiErr[Int] = 
    ajaxPost[Int]("/auth/setUserVerify", List(("value", value.toString)), email)
