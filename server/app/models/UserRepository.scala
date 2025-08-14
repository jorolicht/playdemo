package models

import java.util.function.IntConsumer
import javax.inject.Inject
import scala.util.{ Failure, Success }
import scala.concurrent.Future
import anorm._
import anorm.SqlParser.{ get, str }
import anorm.SqlParser.scalar
import play.api.db.DBApi

import shared.model._
import shared._


implicit def bool2int(b:Boolean):Int = if (b) 1 else 0

@javax.inject.Singleton
class UserRepository @Inject()(dbapi: DBApi)(implicit ec: DatabaseExecutionContext) {

  private val db = dbapi.database("default")

  /**
   * Parse a User from a ResultSet
   */
  private val simple = {
    get[Long]("id") ~ get[String]("email") ~ get[String]("firstname") ~ 
    get[String]("lastname") ~ get[String]("picUrl") ~ get[String]("locale") ~ 
    get[Int]("verified") ~ get[String]("password") ~ get[Long]("request") map {
      case id ~ email ~ firstname ~ lastname  ~ picUrl ~ locale ~ verified ~ password ~ request 
        => User(firstname, lastname, email, password, picUrl, locale, (verified == 1), request, id)
    }
  }


  // -- Queries

  /**
   * Retrieve a user from the email.
   */
  def findByEmail(email: String): Future[Option[User]] = Future {
    db.withConnection { implicit connection =>
      SQL"select * from user where email = $email".as(simple.singleOpt)
    }
  }(ec)

  /**
   * Insert a new user.
   *
   * @param user the user values.
   */
  def insert(user: User): FuEiErr[User] = Future {
    val verified = if user.verified then 1 else 0
    try
      (db.withConnection { implicit connection =>
        SQL"""insert user (email, firstname, lastname, picUrl, locale, verified, password, request)
              values(${user.email},${user.firstname},${user.lastname},${user.picUrl},${user.locale},${verified},${user.password},${user.request})"""
        .executeInsert()
      }) match 
        case Some(uId) => Right(user.copy(id=uId))
        case None      => Left(AppError("err00008.db.user.insert", "no id")) 
    catch { case e: Exception => Left(AppError("err00008.db.user.insert", e.getMessage)) }
  }(ec)

  /**
   * set user email verified
   *
   * @param user id
   */
  def setEmailVerified(id: Long): FuEiErr[Boolean] = Future {
    try
      val result = db.withConnection { implicit connection =>
        SQL"""UPDATE user SET verified=1 WHERE user.id=$id""".executeUpdate()
      } 
      Right(result==1) 
    catch { case e: Exception => Left(AppError("err00008.db.user.insert", e.getMessage)) }
  }(ec)  

  /**
   * set user password
   *
   * @param user id
   */
  def setPassword(email: String, password: String): FuEiErr[Int] = Future {
    try
      val result = db.withConnection { implicit connection =>
        SQL"""UPDATE user SET password=$password WHERE user.email=$email""".executeUpdate()
      } 
      Right(result) 
    catch { case e: Exception => Left(AppError("err00007.db.user.update", e.getMessage)) }
  }(ec)  

  /**
   * (un)set user verified
   *
   * @param users id
   */
  def setVerified(id: Long, value: Boolean): FuEiErr[Int] = Future {
    try
      val result = db.withConnection { implicit connection =>
        SQL"""UPDATE user SET verified=${value:Int} WHERE user.id=$id"""".executeUpdate()
      } 
      Right(result)
    catch { case e: Exception => Left(AppError("err00007.db.user.update", e.getMessage)) }
  }(ec)  

  /**
   * verify a user .
   *
   * @param    users email 
   * @password users password encrypted
   */
  def verify(email: String, password: String): FuEiErr[User] = Future {
    try
      (db.withConnection { implicit connection =>
        SQL"select * from user where email = $email".as(simple.singleOpt)
      }) match 
        case Some(usr) => if (usr.password == password) Right(usr) else Left(AppError("err00015.login.invalid"))
        case None      => Left(AppError("err00015.login.invalid"))
    catch { case e: Exception => Left(AppError("err00011.db.user.select", e.getMessage)) }
  }(ec)

  /**
   * get a user by user id or email address
   *
   * @param email - user email
   * @param id    - user id
   */
  def getUser(email: String="", id: Long=0L): FuEiErr[User] = Future {
    try
      (db.withConnection { implicit connection =>
        if (id==0) SQL"""select * from user where email=$email""".as(simple.singleOpt)
        else       SQL"""SELECT * FROM user WHERE id = $id""".as(simple.singleOpt)     
      }) match {
        case Some(user) => Right(user)
        case None       => Left(AppError("err00010.db.user.read", s"${email}${id}"))
      } 
    catch { case e: Exception => Left(AppError("err00011.db.user.select", e.getMessage)) }
  }(ec)    

}