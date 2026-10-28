package ir.saba.moadian

import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

/** Generic RS256 compact-JWS verifier; not an API response authentication policy. */
object JwsVerifier {
    fun verify(jws: String, key: PublicKey): Boolean {
        if (!key.algorithm.equals("RSA", ignoreCase = true)) return false
        val parts = jws.split('.')
        if (parts.size != 3 || parts.any { it.isEmpty() }) return false
        return try {
            val decoder = Base64.getUrlDecoder()
            val header = String(decoder.decode(parts[0]), StandardCharsets.UTF_8)
            if (header != """{"alg":"RS256"}""") return false
            val signature = decoder.decode(parts[2])
            Signature.getInstance("SHA256withRSA").run {
                initVerify(key)
                update((parts[0] + "." + parts[1]).toByteArray(StandardCharsets.US_ASCII))
                verify(signature)
            }
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: java.security.SignatureException) {
            false
        }
    }
}
