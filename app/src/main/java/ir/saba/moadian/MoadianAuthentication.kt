package ir.saba.moadian

/** Deliberately provider-agnostic: only verified server-issued credentials can be used. */
data class AccessToken(val value: String, val expiresAtEpochMillis: Long) {
    init { require(value.isNotBlank()) }
    fun isValid(nowEpochMillis: Long, clockSkewMillis: Long = 60_000L): Boolean =
        expiresAtEpochMillis - nowEpochMillis > clockSkewMillis
}
interface AccessTokenProvider {
    suspend fun tokenFor(fiscalMemoryId: String): AccessToken
}
class AuthenticatedMoadianGateway(
    private val client: MoadianHttpClient,
    private val tokenProvider: AccessTokenProvider
) {
    suspend fun submitFor(fiscalMemoryId: String, path: String, body: ByteArray, nowEpochMillis: Long): ByteArray {
        require(fiscalMemoryId.matches(Regex("[A-Za-z0-9]{6}")))
        require(body.isNotEmpty())
        val token = tokenProvider.tokenFor(fiscalMemoryId)
        check(token.isValid(nowEpochMillis)) { "Access token expired or near expiry" }
        return client.post(path, token.value, body)
    }
}
