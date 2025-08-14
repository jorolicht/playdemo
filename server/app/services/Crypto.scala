package services

import upickle.default._
import java.security.MessageDigest
import java.util.Arrays
import java.util.Base64
import java.time.Instant
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import play.api.mvc._

import shared.model.{AppError, User }

trait Encryption:
  private val SALT: String   = "uwkjlkavaölkilsdkjflxku_tTTFDWJ4562klHiUjjer%$jj#rt"
  private val CookieValidSec = 28800L    // 8 hours
  private val UserAuthCookie = "PLAY_AUTH"
  
  // encrypt AES
  def encrypt(key: String, value: String): String =
    val cipher: Cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, keyToSpec(key))
    new String(Base64.getEncoder.encode(cipher.doFinal(value.getBytes((StandardCharsets.UTF_8)))))

  // decrypt AES
  def decrypt(key: String, encryptedValue: String): String = 
    val cipher: Cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING")
    cipher.init(Cipher.DECRYPT_MODE, keyToSpec(key))
    new String(cipher.doFinal(Base64.getDecoder.decode(encryptedValue.getBytes(StandardCharsets.UTF_8))))


  def keyToSpec(key: String): SecretKeySpec = 
    var keyBytes: Array[Byte] = (SALT + key).getBytes("UTF-8")
    val sha: MessageDigest = MessageDigest.getInstance("SHA-1")
    keyBytes = sha.digest(keyBytes)
    keyBytes = Arrays.copyOf(keyBytes, 16)
    new SecretKeySpec(keyBytes, "AES")

  def decode64(input: String)(using decoder: Base64.Decoder): String =
    new String(decoder.decode(input.getBytes(StandardCharsets.UTF_8)))

  def encode64(input: String)(using encoder: Base64.Encoder): String =
    encoder.encodeToString(input.getBytes())

  def genUserAuthCookie(user: User)(using encoder: Base64.Encoder): Cookie =
    import java.time.Instant
    val curUnixTime: Long = Instant.now().getEpochSecond
    Cookie(UserAuthCookie, encode64(write[(User,Long)]((user,curUnixTime))))

  def getUserFromCookie[A](request: play.api.mvc.Request[A])(using decoder: Base64.Decoder): Either[AppError, User] =
    val curUnixTime: Long = Instant.now().getEpochSecond
    request.cookies.get(UserAuthCookie) match
      case None         => Left(AppError("NoCookie"))
      case Some(cookie) => 
        try
          val (usr, cookieTime) = read[(User,Long)](decode64(cookie.value))
          if ((curUnixTime - cookieTime ) < CookieValidSec) then Right(usr) else Left(AppError("CookieTimeout"))
        catch
          case e:Exception => Left(AppError("CookieDecode"))

  def genDiscardAuthCookie = DiscardingCookie(UserAuthCookie)
 
  