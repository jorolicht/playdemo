package shared.model

import shared.basic.Pickle.{ReadWriter => RW, macroRW, *}

case class User(id:               (String, Int),  // (UUID (e.g. from google), wordpress user id)
                var username:     String = "",
                var email:        String, 
                var firstname:    String="", 
                var lastname:     String="",
                var password:     String="",
                var org:          String = "",
                var picUrl:       String = "", 
                var description:  String = "",
                var roles:        List[String] = Nil,
                var locale:       String = "", 
                var verified:     Boolean = false,
                var entryTime:    Long = 0L,
                var hasPasskey:   Boolean = false):

  def verifyInfo = write[((String, Int), String, Long)]((id, email, entryTime))
  def name = if firstname != "" || lastname != "" then s"$firstname $lastname" else if username != "" then username else email

  def isTurnierAdmin: Boolean = roles.exists(r => List("administrator", "editor", "turnier_admin").contains(r))


object User:
  implicit val rw: RW[User] = macroRW
  def apply(id: (String, Int), email: String, entryTime:Long) = 
    new User(id, "", email, "", "", "", "", "", "", Nil, "", false, entryTime, false)

case class UserInfo(
  username: String,
  user_id: Int,
  email: String,
  club: String,
  firstname: String = "",
  lastname: String = "",
  description: String = "",
  avatar_url: String = "",
  roles: List[String] = Nil,
  has_passkey: Boolean = false,
  time: String
)

object UserInfo:
  implicit val rw: RW[UserInfo] = macroRW