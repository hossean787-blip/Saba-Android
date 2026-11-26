package ir.saba.invoice.security

import android.annotation.TargetApi
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.os.SystemClock
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/** Observation and explicitly requested read-only protected balance pilot. */
internal object AppAttestationClient {
    private const val BASE = "https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/sura-app-integrity"
    private fun encode(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private val lock=Mutex()
    private val state=IntegrityObservationState()
    private data class HttpResult(val code:Int,val body:JSONObject)
    suspend fun observe(token: String): IntegrityReport = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || token.isBlank())
            return@withContext IntegrityReport(IntegrityOutcome.UNSUPPORTED,System.currentTimeMillis())
        val session=encode(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)))
        lock.withLock {
            state.recent(session,SystemClock.elapsedRealtime())?.let { return@withLock it }
            val outcome=try { perform(token) } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { IntegrityOutcome.UNAVAILABLE }
            state.record(session,outcome,SystemClock.elapsedRealtime(),System.currentTimeMillis())
        }
    }
    @TargetApi(Build.VERSION_CODES.N)
    private fun perform(token: String): IntegrityOutcome {
        val challenge=post("/challenge",token,JSONObject())
        if(challenge.code!=200) return integrityHttpOutcome(challenge.code,"")
        val result=post("/verify",token,proof(challenge.body.getString("nonce")))
        return integrityHttpOutcome(result.code,result.body.optString("status"))
    }

    /** Each protected request uses its own challenge; never reuse the observation cache. */
    suspend fun protectedBalance(token:String,taxpayerId:Long):ProtectedBalanceReport = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || token.isBlank())
            return@withContext ProtectedBalanceReport(ProtectedBalanceOutcome.UNSUPPORTED)
        require(taxpayerId in 1..9_007_199_254_740_991L)
        lock.withLock {
            try {
                val challenge=post("/protected-challenge",token,JSONObject().put("taxpayerId",taxpayerId))
                if(challenge.code!=200) return@withLock protectedBalanceResult(challenge.code,"",null,challenge.body.optString("error"))
                require(challenge.body.optString("mode")=="enforce" &&
                    challenge.body.optString("operation")=="invoice_balance")
                val result=post("/protected-balance",token,proof(challenge.body.getString("nonce")).put("taxpayerId",taxpayerId))
                protectedBalanceResult(result.code,result.body.optString("mode"),result.body.opt("remaining"),result.body.optString("error"))
            } catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(_:Exception){ProtectedBalanceReport(ProtectedBalanceOutcome.UNAVAILABLE)}
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun proof(raw:String):JSONObject {
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
            return JSONObject().put("nonce",raw).put("chain",certs).put("signature",encode(sig))
        } finally { runCatching { store.deleteEntry(alias) } }
    }
    private fun post(path: String, token: String, body: JSONObject): HttpResult {
        require(path in setOf("/challenge","/verify","/protected-challenge","/protected-balance"))
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
            val code=c.responseCode
            val stream=if(code in 200..299) c.inputStream else c.errorStream
            if(stream==null) return HttpResult(code,JSONObject())
            val text = stream.use { input ->
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
            return HttpResult(code,runCatching {JSONObject(text)}.getOrElse {JSONObject()})
        } finally { c.disconnect() }
    }
}
