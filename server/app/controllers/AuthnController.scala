package controllers

import play.api.mvc.*
import play.api.libs.json.*
import services.WebAuthnService
import java.util.UUID
import java.util.Optional
import javax.inject.*
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.data._
import repositories.PasskeyRepo
import scala.jdk.CollectionConverters.*


@Singleton
class AuthnController @Inject() (
  cc: ControllerComponents,
  relyingParty: RelyingParty,
  passkeyRepo: PasskeyRepo
) extends AbstractController(cc):

  
  def startRegistration(userId: UUID) = Action {
    val userIdentity =
      UserIdentity.builder()
        .name(userId.toString)
        .displayName("User " + userId)
        .id(new ByteArray(userId.toString.getBytes))
        .build()
  
    val options =
      relyingParty.startRegistration(
        StartRegistrationOptions.builder()
          .user(userIdentity)
          .build()
      )
  
    Ok(Json.toJson(options))
  }

// Registrierungs-Modell
// 	1.	Benutzer gibt E-Mail ein
// 	2.	Backend erstellt User-Datensatz
// 	3.	Passkey wird erzeugt & gespeichert
// 	4.	Benutzer ist registriert

  // Step 1: create user
  def createUser = Action(parse.json) { req =>
    val email = (req.body \ "email").as[String]
    val userId = UUID.randomUUID()
    userRepo.insert(userId, email)
    Ok(Json.obj("userId" -> userId.toString))
  } 

  // Step 2: start passkey registration
  def startPasskeyRegistration(userId: UUID) = Action {
    val user = userRepo.find(userId)

    val options = webAuthn.startRegistration(user.id, user.email)
    cache.put(user.id, options.challenge)

    Ok(Json.parse(options.toCredentialsCreateJson))
  }
    // Step 2: Client-side code to call this endpoint
    // navigator.credentials.create({
    //   publicKey: optionsFromServer
    // })

  // Step 3: finish passkey registration  
  def finishPasskeyRegistration(userId: UUID) = Action(parse.json) { req =>
    val result = webAuthn.finishRegistration(
      userId,
      req.body,
      cache.get(userId)
    )
    passkeyRepo.save(
      userId,
      result.getKeyId.getId,
      result.getPublicKeyCose,
      result.getSignatureCount
    )
    Ok
  }


class WebAuthnService(
  relyingParty: RelyingParty,
  passkeyRepo: PasskeyRepo
):

  def startLogin(userId: UUID): PublicKeyCredentialRequestOptions =
    PublicKeyCredentialRequestOptions.builder()
      .challenge(new ByteArray(Random.nextBytes(32)))
      .rpId("localhost")
      .allowCredentials(
        passkeyRepo
          .findByUser(userId)
          .map(pk =>
            PublicKeyCredentialDescriptor.builder()
              .id(new ByteArray(pk.credentialId))
              .`type`(PublicKeyCredentialType.PUBLIC_KEY)
              .build()
          )
          .asJava
      )
      .build()

  def finishLogin(
    request: PublicKeyCredentialRequestOptions,
    responseJson: String
  ): AssertionResult =
    relyingParty.finishAssertion(
      FinishAssertionOptions.builder()
        .request(request)
        .response(
          PublicKeyCredential.parseAssertionResponseJson(responseJson)
        )
        .build()
    )  

// 🏗️ Saubere technische Lösung

// 🔐 Prinzip
// 	•	Bei unbekannter E-Mail:
// 	•	Fake Challenge erzeugen
// 	•	Kein User Lookup
// 	•	Kein Fehler
// 	•	Login bricht stumm ab

//   def startLogin = Action(parse.json) { req =>
//     val email = (req.body \ "email").as[String]

//     userRepo.findByEmail(email) match
//         case Some(user) =>
//         val options = webAuthn.startLogin(user.id)
//         cache.put(s"login-${user.id}", options, 2.minutes)
//         Ok(Json.parse(options.toCredentialsGetJson))

//         case None =>
//         // Fake Challenge erzeugen
//         val fakeOptions =
//             PublicKeyCredentialRequestOptions.builder()
//             .challenge(new ByteArray(Random.nextBytes(32)))
//             .rpId("example.com")
//             .build()

//         Ok(Json.parse(fakeOptions.toCredentialsGetJson))
//   }



// 🔐 Extra-Härtung (empfohlen)

// ✔ Rate-Limit pro IP
// ✔ Delay (200–400 ms) bei Fehlversuchen
// ✔ Kein Autocomplete bei E-Mail-Feld

// def finishLogin = Action(parse.json) { req =>
//   val userIdOpt =
//     (req.body \ "userId").asOpt[String].map(UUID.fromString)

//   userIdOpt.flatMap { userId =>
//     cache.get[PublicKeyCredentialRequestOptions](s"login-$userId")
//   } match
//     case Some(request) =>
//       val result = webAuthn.finishLogin(request, credentialJson)
//       if result.isSuccess then
//         Ok.withSession("user" -> userId.toString)
//       else Unauthorized

//     case None =>
//       // Fake Login → immer Unauthorized
//       Unauthorized
// }


// Empfohlener Registrierungs-Flow
// 1️⃣ Benutzer startet Registrierung

// User gibt E-Mail ein
// → „Wir senden Ihnen einen Bestätigungslink“

// 2️⃣ Backend: Pending User anlegen
// CREATE TABLE pending_users (
//   token UUID PRIMARY KEY,
//   email VARCHAR(255),
//   expires_at TIMESTAMP
// );

// val token = UUID.randomUUID()
// pendingRepo.insert(token, email)
// sendMail(email, s"https://app.com/verify/$token")
// ✔ Noch kein echter User
// ✔ Noch kein Passkey

// 3️⃣ User klickt E-Mail-Link
// def verifyEmail(token: UUID) = Action {
//   pendingRepo.find(token) match
//     case None => Gone
//     case Some(pending) =>
//       val userId = userRepo.create(pending.email)
//       Redirect(s"/register/passkey?user=$userId")
// }

// 4️⃣ Jetzt erst Passkey registrieren
// def startPasskeyRegistration(userId: UUID) = Action {
//   val options = webAuthn.startRegistration(userId)
//   Ok(Json.parse(options.toCredentialsCreateJson))
// }

// ✔ E-Mail ist verifiziert
// ✔ Passkey bindet sich an echten User
