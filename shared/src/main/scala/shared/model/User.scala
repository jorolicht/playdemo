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

  def isTurnierAdmin: Boolean = roles.exists(r => List("administrator", "editor", "turnier_admin", "tourney_master", "tourney_admin", "author", "subscriber").contains(r))


object User:
  implicit val rw: RW[User] = macroRW
  def apply(id: (String, Int), email: String, entryTime:Long) = 
    new User(id, "", email, "", "", "", "", "", "", Nil, "", false, entryTime, false)

case class Purchase(
  date: String,  // Format yyyymmddhhmm
  count: Int,    // number of tourneys
  price: Double  // purchase price
)

object Purchase:
  implicit val rw: RW[Purchase] = macroRW

case class UserProfile(
  available: Int = 0,
  executed:  Int = 0,
  history:   List[Purchase] = Nil
)

object UserProfile:
  implicit val rw: RW[UserProfile] = macroRW

case class UserInfo(
  username: String,
  user_id: Int,
  email: String,
  club: String,
  user_profile: Option[UserProfile] = None,
  allowed_tourneys: Option[Int] = None,
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

case class AdminUserInfo(
  user_id: Int,
  username: String,
  email: String,
  club: String = "",
  roles: List[String] = Nil,
  user_profile: UserProfile = UserProfile(),
  allowed_tourneys: Int = 0
)

object AdminUserInfo:
  implicit val rw: RW[AdminUserInfo] = macroRW

case class UnmatchedPurchase(
  email: String,
  count: Int,
  price: Double,
  date: String,
  product_name: String = ""
)

object UnmatchedPurchase:
  implicit val rw: RW[UnmatchedPurchase] = macroRW

case class AssignPayload(
  target_user_id: Int,
  email: String,
  index: Int
)

object AssignPayload:
  implicit val rw: RW[AssignPayload] = macroRW