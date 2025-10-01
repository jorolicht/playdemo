package shared.model

import upickle.default._
import upickle.default.{ReadWriter as RW, macroRW}
import shared.Routines.getMDStr
import shared.model.AppError

case class Address(
  description: String,
  country:     String,
  zip:         String,
  city:        String,
  street:      String
)

object Address:
  // Custom ReadWriter: speichert als JSON-Array
  given RW[Address] = readwriter[ujson.Arr].bimap[Address](
    // Serialize: Address -> JSON Array
    addr => ujson.Arr(addr.description, addr.country, addr.zip, addr.city, addr.street),
    // Deserialize: JSON Array -> Adres
    json =>
      val arr = json.arr
      Address(arr(0).str, arr(1).str, arr(2).str, arr(3).str, arr(4).str)
  )
