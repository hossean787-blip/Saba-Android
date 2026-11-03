package ir.saba.invoice.security

import org.json.JSONArray
import org.json.JSONObject
import java.security.Signature
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/** Nonces are ephemeral, one-use and bound to one account/taxpayer/memory. Never log or persist them. */
class TaxpayerNonce(val identity: TaxpayerIdentity, private val value: String, val expiresAtEpochMillis: Long) {
    private val used = AtomicBoolean(false)
    init { require(value.isNotBlank() && value.length <= 2048 && value.none { it == '\r' || it == '\n' }) }
    internal fun consume(expected: TaxpayerIdentity, now: Long): String {
        require(identity == expected) { "چالش متعلق به مؤدی یا حساب دیگری است." }
        require(now < expiresAtEpochMillis && expiresAtEpochMillis - now <= 200_000L) { "چالش منقضی شده است؛ چالش جدید بگیرید." }
        check(used.compareAndSet(false,true)) { "چالش قبلاً استفاده شده است؛ چالش جدید لازم است." }
        return value
    }
    override fun toString() = "TaxpayerNonce([redacted])"
}

/** Certificate-based v2 authorization JWS profile from supplied SDK 2.0.33/RC_TICS.
 * Does not fetch nonces, transmit tokens or claim official acceptance.
 */
class MoadianV2Signer(private val credentials: TaxpayerCredentialRepository) {
    private val consumed = java.util.concurrent.ConcurrentHashMap<String,Long>()
    suspend fun authorization(companyId: Long, challenge: TaxpayerNonce, nowEpochMillis: Long): String {
        val material = credentials.material(companyId,nowEpochMillis) ?: error("کلید و گواهی این مؤدی ثبت نشده است.")
        val nonce = challenge.consume(material.identity,nowEpochMillis)
        consumed.entries.forEach { if (it.value <= nowEpochMillis) consumed.remove(it.key,it.value) }
        check(consumed.size < 1024) { "تعداد چالش‌های فعال بیش از حد مجاز است." }
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256")
            .digest(material.identity.aad() + nonce.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(java.util.Locale.ROOT,it.toInt() and 255) }
        check(consumed.putIfAbsent(fingerprint,challenge.expiresAtEpochMillis) == null) { "این چالش قبلاً امضا شده است." }
        val payload = JSONObject().put("nonce",nonce).put("clientId",material.identity.fiscalMemoryId)
        return sign(material, payload.toString(), nowEpochMillis)
    }

    suspend fun invoice(companyId: Long, expected: TaxpayerIdentity, payload: String, nowEpochMillis: Long): String {
        val material = credentials.material(companyId,nowEpochMillis) ?: error("کلید و گواهی این مؤدی ثبت نشده است.")
        require(material.identity == expected)
        require(payload.length in 1..2_000_000)
        return sign(material,payload,nowEpochMillis)
    }

    private fun sign(material: TaxpayerSigningMaterial, payload: String, nowEpochMillis: Long): String {
        val header = JSONObject().put("alg","RS256")
            .put("x5c",JSONArray().put(Base64.getEncoder().encodeToString(material.certificate.encoded)))
            .put("sigT",Instant.ofEpochMilli(nowEpochMillis).truncatedTo(ChronoUnit.SECONDS).toString())
            .put("crit",JSONArray().put("sigT")).put("typ","jose").put("cty","text/plain")
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val input = encoder.encodeToString(header.toString().toByteArray(Charsets.UTF_8)) + "." +
            encoder.encodeToString(payload.toByteArray(Charsets.UTF_8))
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(material.key); update(input.toByteArray(Charsets.US_ASCII)); sign()
        }
        return input + "." + encoder.encodeToString(signature)
    }
}
