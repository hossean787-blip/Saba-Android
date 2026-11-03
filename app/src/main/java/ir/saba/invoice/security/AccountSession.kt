package ir.saba.invoice.security

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.util.Base64

/** Checks lifetime/routing of a token already obtained from the trusted authentication gateway.
 * Does not replace server JWT signature validation or revocation checks.
 */
class AccountSession private constructor(val account: AccountScope, val expiresAt: Long) {
    override fun toString() = "AccountSession([redacted])"
    companion object {
        fun active(token: String, now: Long = System.currentTimeMillis()): AccountSession {
            try {
                val account = AccountScope.fromAuthenticatedToken(token)
                val claims = JSONObject(String(Base64.getUrlDecoder().decode(token.split('.',limit=4)[1]),Charsets.UTF_8))
                val expiry = claims.get("exp")
                require(expiry is Int || expiry is Long)
                val millis = Math.multiplyExact((expiry as Number).toLong(),1000L)
                require(now >= 0 && millis > now)
                return AccountSession(account,millis)
            } catch (_: Exception) { throw IllegalArgumentException("نشست معتبر نیست یا منقضی شده است؛ دوباره وارد شوید.") }
        }
        fun requireOwner(token: String, account: AccountScope, now: Long = System.currentTimeMillis()) {
            require(active(token,now).account == account) { "نشست متعلق به حساب انتخاب‌شده نیست؛ دوباره وارد شوید." }
        }
    }
}

/** Pure coroutine lifetime monitor, also rechecked immediately on Activity resume by the UI. */
internal suspend fun watchAccountSession(token: String, clock: () -> Long = System::currentTimeMillis, onAccount: (AccountScope?) -> Unit) {
    while (currentCoroutineContext().isActive) {
        val now = clock()
        val session = runCatching { AccountSession.active(token,now) }.getOrNull()
        onAccount(session?.account)
        if (session == null) return
        delay(minOf(session.expiresAt - now,60_000L))
    }
}
