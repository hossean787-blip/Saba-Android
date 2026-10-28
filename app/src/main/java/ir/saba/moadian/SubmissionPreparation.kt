package ir.saba.moadian

import java.security.MessageDigest
import java.util.UUID

/**
 * Local request preparation only. It is NOT a Moadian API envelope.
 * The caller persists the request ID and hash before invoking a transport.
 */
data class PreparedSubmission(
    val requestId: String,
    val payloadSha256: String,
    val payload: ByteArray
)

object SubmissionPreparation {
    fun sha256Hex(payload: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(payload)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun prepare(payload: ByteArray, requestId: String = UUID.randomUUID().toString()): PreparedSubmission {
        require(payload.isNotEmpty()) { "Invoice payload cannot be empty" }
        require(requestId.isNotBlank()) { "Request ID cannot be blank" }
        return PreparedSubmission(requestId, sha256Hex(payload), payload.copyOf())
    }

    fun verifyUnchanged(prepared: PreparedSubmission, payload: ByteArray): Boolean =
        MessageDigest.isEqual(
            MessageDigest.getInstance("SHA-256").digest(prepared.payload),
            MessageDigest.getInstance("SHA-256").digest(payload)
        ) && prepared.payloadSha256 == sha256Hex(prepared.payload)
}
