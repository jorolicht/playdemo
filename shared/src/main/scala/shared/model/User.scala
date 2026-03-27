package shared.model

import shared.basic.Pickle.{ReadWriter => RW, macroRW, *}

case class User(id: String,
                var email: String, 
                var firstname: String="", 
                var lastname: String="",
                var password: String="",
                var picUrl: String = "", 
                var locale: String = "", 
                var verified: Boolean = false,
                var entryTime: Long = 0L):

  def verifyInfo = write[(String,String,Long)]((id,email,entryTime))


object User:
  implicit val rw: RW[User] = macroRW
  def apply(id: String, email: String, entryTime:Long) = 
    new User(id, email, "", "", "", "", "", false, entryTime)

case class UserInfo(
  username: String,
  user_id: Int,
  email: String,
  club: String,
  time: String
)

object UserInfo:
  given Reader[UserInfo] = macroR