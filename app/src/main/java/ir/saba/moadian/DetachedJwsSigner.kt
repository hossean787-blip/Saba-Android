package ir.saba.moadian

import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

/**
 * Independently implemented compact JWS with attached payload, RS256 only.
 * Caller must verify official API profile (headers, certificate and request format)
 * before using this for authenticated Moadian requests.
 * Never embeds private key material in logs, app assets, or source.
 */
object DetachedJwsSigner {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val header = """{"alg":"RS256"}"""

    fun sign(payload: ByteArray, key: PrivateKey): String {
        require(key.algorithm.equals("RSA", ignoreCase = true)) { "RSA key required" }
        val protectedHeader = encoder.encodeToString(header.toByteArray(StandardCharsets.UTF_8))
        val body = encoder.encodeToString(payload)
        val signingInput = "$protectedHeader.$body"
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(key)
            update(signingInput.toByteArray(StandardCharsets.US_ASCII))
            sign()
        }
        return signingInput + "." + encoder.encodeToString(signature)
    }
}
