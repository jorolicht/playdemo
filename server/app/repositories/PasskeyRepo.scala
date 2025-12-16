package repositories

import scala.collection.mutable
import java.util.UUID
import com.yubico.webauthn.data.*

trait PasskeyRepo:

  /** Liefert alle Credentials eines Users */
  def getCredentialIdsForUser(userId: UUID): Seq[PublicKeyCredentialDescriptor]

  /** Prüft, ob ein Credential existiert */
  def credentialExists(credentialId: ByteArray): Boolean

  /** Speichert ein neues Credential */
  def saveCredential(
    userId: UUID,
    credentialId: ByteArray,
    publicKeyCose: ByteArray,
    signatureCount: Long
  ): Unit


@Singleton
class InMemoryPasskeyRepo extends PasskeyRepo:

  private val store =
    mutable.Map[UUID, mutable.Buffer[RegisteredCredential]]()

  def getCredentialIdsForUser(userId: UUID) =
    store.getOrElse(userId, Nil).map(_.getCredentialId)

  def credentialExists(id: ByteArray) =
    store.values.flatten.exists(_.getCredentialId == id)

  def saveCredential(
    userId: UUID,
    credentialId: ByteArray,
    publicKeyCose: ByteArray,
    signatureCount: Long
  ): Unit =
    val cred =
      RegisteredCredential.builder()
        .credentialId(credentialId)
        .publicKeyCose(publicKeyCose)
        .signatureCount(signatureCount)
        .build()

    store.getOrElseUpdate(userId, mutable.Buffer()) += cred  