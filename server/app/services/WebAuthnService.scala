package services

import com.yubico.webauthn._
import com.yubico.webauthn.data._
import java.util.Optional
import scala.util.Random

class WebAuthnService(
  relyingParty: RelyingParty,
  passkeyRepo: PasskeyRepo
):

  private val rp = RelyingParty.builder()
    .identity(
      RelyingPartyIdentity.builder()
        .id("localhost")
        .name("Play Passkey Demo")
        .build()
    )
    .credentialRepository(new InMemoryCredentialRepo)
    .build()

  def startRegistration(userId: String): PublicKeyCredentialCreationOptions =
    PublicKeyCredentialCreationOptions.builder()
      .rp(rp.getIdentity)
      .user(
        UserIdentity.builder()
          .name(userId)
          .displayName(userId)
          .id(userId.getBytes)
          .build()
      )
      .challenge(new ByteArray(Random.nextBytes(32)))
      .build()



  def startLogin = Action(parse.json) { req =>
    val email = (req.body \ "email").as[String]

    userRepo.findByEmail(email) match
        case None => Unauthorized
        case Some(user) =>
        val options = webAuthn.startLogin(user.id)

        cache.put(
            s"login-challenge-${user.id}",
            options,
            2.minutes
        )

        Ok(Json.parse(options.toCredentialsGetJson))
  }

  def finishLogin = Action(parse.json) { req =>
    val userId = UUID.fromString((req.body \ "userId").as[String])
    val response = (req.body \ "credential").toString()
  
    val request =
      cache.get[PublicKeyCredentialRequestOptions](
        s"login-challenge-$userId"
      ).getOrElse(return Unauthorized)
  
    val result = webAuthn.finishLogin(request, response)
  
    if result.isSuccess then
      Ok.withSession("user" -> userId.toString)
    else
      Unauthorized
  }  