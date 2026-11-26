package ir.saba.invoice.security

import android.annotation.TargetApi
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/** Observe only: never blocks existing services. */
internal object AppAttestationClient {
    private const val BASE = "https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/sura-app-integrity"
    private fun encode(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    suspend fun observe(token: String): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || token.isBlank()) return@withContext false
        try { perform(token) } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { false }
    }
    @TargetApi(Build.VERSION_CODES.N)
    private fun perform(token: String): Boolean {
        val raw = post("/challenge", token, JSONObject()).getString("nonce")
        val nonce = Base64.decode(raw, Base64.NO_WRAP)
        require(nonce.size == 32 && encode(nonce) == raw)
        val alias = "sura.integrity.ephemeral." + UUID.randomUUID()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            generator.initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(nonce).build())
            val key = generator.generateKeyPair()
            val chain = requireNotNull(store.getCertificateChain(alias))
            require(chain.size in 2..8)
            val sig = Signature.getInstance("SHA256withECDSA").apply {
                initSign(key.private)
                update(nonce)
            }.sign()
            val certs = JSONArray()
            chain.forEach { certs.put(encode(it.encoded)) }
            return post("/verify", token, JSONObject().put("nonce", raw).put("chain", certs)
                .put("signature", encode(sig))).optString("status") == "verified"
        } finally { runCatching { store.deleteEntry(alias) } }
    }
    private fun post(path: String, token: String, body: JSONObject): JSONObject {
        require(path == "/challenge" || path == "/verify")
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            require(bytes.size <= 96000)
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            if (c.responseCode !in 200..299) return JSONObject()
            val text = c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(2048)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= 8192)
                    out.write(buffer, 0, n)
                }
                out.toString("UTF-8")
            }
            return JSONObject(text)
        } finally { c.disconnect() }
    }
}
