package shared.model

import upickle.default._
import upickle.default.{ReadWriter as RW, macroRW}

import shared.Routines._
import shared.model.AppError

case class Contact(
  var lastname:  String,
  var firstname: String,
  var phone:     String,
  var email:     String
):
  def getName(fmt: Int = 0): String =
    fmt match
      case 0 =>
        if firstname.nonEmpty && lastname.nonEmpty then
          s"$firstname $lastname"
        else
          firstname + lastname
      case 1 =>
        if firstname.nonEmpty && lastname.nonEmpty then
          s"$lastname, $firstname"
        else
          firstname + lastname
      case _ =>
        if firstname.nonEmpty && lastname.nonEmpty then
          s"$lastname, $firstname"
        else
          firstname + lastname

object Contact:
  // Custom ReadWriter: speichert als JSON-Array
  given RW[Contact] = readwriter[ujson.Arr].bimap[Contact](
    // Serialize: Contact -> JSON Array
    contact => ujson.Arr(contact.lastname, contact.firstname, contact.phone, contact.email),
    // Deserialize: JSON Array -> Contact
    json =>
      val arr = json.arr
      Contact(arr(0).str, arr(1).str, arr(2).str, arr(3).str)
  )




