package ir.saba.moadian

/**
 * Interface only: implement with AndroidKeyStore or authorized hardware-backed provider.
 * Never log, embed, or upload private key material. Signature algorithm must be selected
 * using the current official API specification before real submission.
 */
interface SigningPort {
    suspend fun sign(payload: ByteArray): ByteArray
}

/** Network calls are disabled until an official endpoint and schema have been verified. */
interface MoadianTransportPort {
    suspend fun submit(signedPayload: ByteArray, requestId: String): String
    suspend fun checkStatus(requestId: String): String
}
