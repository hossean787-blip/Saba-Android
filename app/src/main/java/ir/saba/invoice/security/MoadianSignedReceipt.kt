package ir.saba.invoice.security

import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

/** Keys must come from the authenticated fixed-origin server-information response, not from JWS headers. */
data class OrganizationKey(val id: String, val key: RSAPublicKey, val purpose: Int)
object MoadianSignedReceipt {
    fun keys(metadata: String): List<OrganizationKey> {
        val root = JSONObject(metadata)
        require(root.getLong("serverTime") > 0)
        val keys = root.getJSONArray("publicKeys")
        require(keys.length() in 1..32)
        return (0 until keys.length()).map { index ->
            val entry = keys.getJSONObject(index)
            require(entry.getString("algorithm") == "RSA")
            val encoded = entry.getString("key")
            require(encoded.length <= 20000)
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded))) as RSAPublicKey
            require(key.modulus.bitLength() >= 2048)
            val id = entry.getString("id")
            require(id.isNotBlank() && id.length <= 200)
            OrganizationKey(id,key,entry.getInt("purpose"))
        }
    }
    fun verify(jws: String, keys: List<OrganizationKey>, taxId: String, fiscalId: String, reference: String, now: Long) {
        require(jws.length in 1..100_000)
        val parts = jws.split('.',limit=4)
        require(parts.size == 3 && parts.all { it.matches(Regex("[A-Za-z0-9_-]+")) })
        val decoder = Base64.getUrlDecoder()
        val header = JSONObject(String(decoder.decode(parts[0]),Charsets.UTF_8))
        require(header.getString("alg") == "RS256" && !header.has("b64"))
        val crit = header.optJSONArray("crit")
        require(!header.has("crit") || crit != null)
        if (crit != null) require((0 until crit.length()).all { crit.getString(it) == "sigT" })
        val signatureTime = Instant.parse(header.getString("sigT")).toEpochMilli()
        require(signatureTime > 0 && signatureTime <= now + 300_000)
        val candidates = if (header.has("kid")) keys.filter { it.id == header.getString("kid") } else keys
        require(candidates.any { candidate -> Signature.getInstance("SHA256withRSA").run {
            initVerify(candidate.key); update((parts[0]+"."+parts[1]).toByteArray(Charsets.US_ASCII)); verify(decoder.decode(parts[2]))
        } }) { "امضای رسید سازمان معتبر نیست." }
        val payload = JSONObject(String(decoder.decode(parts[1]),Charsets.UTF_8))
        require(payload.getString("referenceNumber") == reference && payload.getString("clientId") == fiscalId && payload.getString("taxId") == taxId) { "رسید متعلق به این صورتحساب نیست." }
        val received = Instant.parse(payload.getString("receivedDate")).toEpochMilli()
        require(received > 0 && received <= now + 300_000)
    }
}
