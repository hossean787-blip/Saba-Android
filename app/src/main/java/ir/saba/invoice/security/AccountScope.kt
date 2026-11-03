package ir.saba.invoice.security

import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

/** Storage routing for an already server-authenticated session, not local JWT signature verification. */
class AccountScope private constructor(val ownerId: String) {
    val storageKey: String get() = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(Locale.ROOT,it.toInt() and 255) }
    val databaseName: String get() = "sura_account_${storageKey}.db"
    override fun equals(other: Any?) = other is AccountScope && ownerId == other.ownerId
    override fun hashCode() = ownerId.hashCode()
    override fun toString() = "AccountScope([redacted])"

    companion object {
        fun of(ownerId: String): AccountScope {
            require(ownerId.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))) { "شناسه حساب معتبر نیست؛ دوباره وارد شوید." }
            return AccountScope(ownerId.lowercase(Locale.ROOT))
        }
        fun fromAuthenticatedToken(token: String): AccountScope {
            try {
                val parts = token.split('.')
                require(parts.size == 3 && parts.all { it.isNotBlank() } && token.length <= 32768)
                return of(JSONObject(String(Base64.getUrlDecoder().decode(parts[1]),Charsets.UTF_8)).getString("sub"))
            } catch (_: Exception) { throw IllegalArgumentException("نشست فاقد شناسه حساب معتبر است؛ دوباره وارد شوید.") }
        }
    }
}

data class TaxpayerIdentity(val account: AccountScope, val companyId: Long, val fiscalMemoryId: String) {
    init { require(companyId > 0 && fiscalMemoryId.matches(Regex("[A-Z0-9]{6}"))) }
    internal fun aad(): ByteArray = "SURA:account:${account.ownerId}:taxpayer:$companyId:memory:$fiscalMemoryId:v2".toByteArray(Charsets.UTF_8)
}
