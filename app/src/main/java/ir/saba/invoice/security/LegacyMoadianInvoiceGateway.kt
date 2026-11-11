package ir.saba.invoice.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/** No-certificate taxpayer self-TSP protocol (separate from certificate-based v2). */
class LegacyPreparedInvoice(val envelope: String, val headers: Map<String, String>) {
    override fun toString() = "LegacyPreparedInvoice([redacted])"
}
data class LegacyInvoiceAcknowledgement(val uid: String, val referenceNumber: String?)
data class LegacyInvoiceInquiry(val uid: String, val taxId: String?, val referenceNumber: String?, val status: String, val errorCodes: List<String>)

/** Injection seam: preflight finishes before the durable SENDING claim and HTTP invoice POST. */
interface LegacyInvoiceGateway {
    suspend fun prepare(companyId: Long, fiscalId: String, uid: String, payload: String): LegacyPreparedInvoice
    suspend fun send(prepared: LegacyPreparedInvoice, expectedUid: String): LegacyInvoiceAcknowledgement
    suspend fun inquire(companyId: Long, fiscalId: String, uid: String, taxId: String): LegacyInvoiceInquiry
}

internal interface LegacyInvoiceTransport {
    suspend fun post(operation: String, envelope: JSONObject, headers: Map<String, String>): JSONObject
}

/** Fixed tax authority hostname; only known self-TSP operations, HTTPS and no redirects. */
internal class OfficialLegacyInvoiceTransport : LegacyInvoiceTransport {
    override suspend fun post(operation: String, envelope: JSONObject, headers: Map<String, String>): JSONObject =
        withContext(Dispatchers.IO) {
            require(operation in setOf("GET_TOKEN", "GET_SERVER_INFORMATION", "INVOICE.V01", "INQUIRY_BY_UID"))
            val path = if (operation == "INVOICE.V01") "async/normal-enqueue" else "sync/" + operation
            val url = URL("https://tp.tax.gov.ir/req/api/self-tsp/" + path)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = 15000
                connection.readTimeout = 25000
                connection.doOutput = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                headers.forEach { (key, value) ->
                    require(key in setOf("requestTraceId", "timestamp", "Authorization"))
                    require(value.isNotBlank() && value.none { it == '\r' || it == '\n' })
                    connection.setRequestProperty(key, value)
                }
                val bytes = envelope.toString().toByteArray(Charsets.UTF_8)
                require(bytes.size <= 4_000_000) { "حجم درخواست بیش از حد مجاز است." }
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
                val status = connection.responseCode
                require(status in 200..299) { "پاسخ سامانه مؤدیان ناموفق بود (HTTP " + status + ")." }
                connection.inputStream.use { stream ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(out.size() + count <= 2_000_000) { "پاسخ سامانه بیش از حد بزرگ است." }
                        out.write(buffer, 0, count)
                    }
                    JSONObject(out.toString("UTF-8"))
                }
            } finally { connection.disconnect() }
        }
}

/** Legacy canonical payload: stable lexicographic field order, # escaping, E-prefixed array slots. */
internal object LegacyMoadianCanonical {
    fun normalize(root: JSONObject, headers: Map<String, String> = emptyMap()): String {
        val entries = sortedMapOf<String, String?>()
        fun visit(prefix: String, value: Any?) {
            when (value) {
                is JSONObject -> value.keys().forEach { key ->
                    visit(if (prefix.isEmpty()) key else prefix + "." + key, value.opt(key))
                }
                is JSONArray -> for (i in 0 until value.length())
                    visit(if (prefix.isEmpty()) "E" + i else prefix + ".E" + i, value.opt(i))
                null, JSONObject.NULL -> entries[prefix] = null
                else -> entries[prefix] = value.toString()
            }
        }
        visit("", root)
        headers.forEach { (key, value) ->
            entries[key] = if (key == "Authorization") value.removePrefix("Bearer ") else value
        }
        return entries.values.joinToString("#") { v ->
            if (v.isNullOrEmpty()) "#" else v.replace("#", "##")
        }
    }
}

/** Encrypts old-protocol data with AES-256-GCM and wraps a hex AES key using RSA-OAEP-256. */
internal object LegacyMoadianEncryption {
    fun wrap(invoice: JSONObject, key: OrganizationKey, rng: SecureRandom = SecureRandom()): Triple<String, String, String> {
        require(key.purpose == 1 && key.key.modulus.bitLength() >= 2048)
        val aesKey = ByteArray(32).also(rng::nextBytes)
        val iv = ByteArray(16).also(rng::nextBytes)
        try {
            val plain = invoice.toString().toByteArray(Charsets.UTF_8)
            require(plain.size in 1..2_000_000)
            // The self-TSP legacy SDK XORs invoice bytes with the AES key before GCM.
            val xored = plain.copyOf()
            for (i in xored.indices) xored[i] = (xored[i].toInt() xor aesKey[i % aesKey.size].toInt()).toByte()
            val aes = Cipher.getInstance("AES/GCM/NoPadding")
            aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
            val cipherTextAndTag = aes.doFinal(xored)
            xored.fill(0)
            val hexKey = aesKey.joinToString("") { "%02x".format(java.util.Locale.ROOT, it.toInt() and 255) }
            val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            rsa.init(Cipher.ENCRYPT_MODE, key.key,
                OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
            return Triple(
                Base64.getEncoder().encodeToString(cipherTextAndTag),
                Base64.getEncoder().encodeToString(rsa.doFinal(hexKey.toByteArray(Charsets.US_ASCII))),
                iv.joinToString("") { "%02x".format(java.util.Locale.ROOT, it.toInt() and 255) }
            )
        } finally {
            aesKey.fill(0)
            iv.fill(0)
        }
    }
}

internal class LegacyMoadianInvoiceGateway(
    private val credentials: TaxpayerCredentialRepository,
    private val transport: LegacyInvoiceTransport = OfficialLegacyInvoiceTransport(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val rng: SecureRandom = SecureRandom()
) : LegacyInvoiceGateway {
    private fun headers(token: String? = null): Map<String, String> =
        linkedMapOf("requestTraceId" to newId(), "timestamp" to clock().toString()).also {
            require(UUID.fromString(it.getValue("requestTraceId")).toString() == it.getValue("requestTraceId"))
            if (token != null) it["Authorization"] = "Bearer " + token
        }

    private fun packet(type: String, data: Any?, fiscal: String, uid: String? = null): JSONObject =
        JSONObject().put("uid", uid ?: JSONObject.NULL).put("packetType", type)
            .put("retry", false).put("data", data ?: JSONObject.NULL)
            .put("encryptionKeyId", "").put("symmetricKey", "").put("iv", "")
            .put("fiscalId", fiscal).put("dataSignature", "")

    private suspend fun sign(companyId: Long, fiscal: String, content: String): String =
        credentials.signPendingInvoiceContent(companyId, fiscal, content.toByteArray(Charsets.UTF_8))

    private fun result(response: JSONObject): Any {
        val value = response.opt("result") ?: error("پاسخ سامانه حاوی نتیجه نیست.")
        require(value != JSONObject.NULL)
        if (value is JSONObject) require(
            listOf("errorCode", "errorDetail").all {
                !value.has(it) || value.isNull(it) || value.optString(it).isBlank()
            } && (!response.has("errorCode") || response.isNull("errorCode") ||
                response.optString("errorCode").isBlank())
        ) { "سامانه مؤدیان درخواست را رد کرد." }
        return value
    }

    private suspend fun sync(companyId: Long, fiscal: String, operation: String, data: Any?, token: String? = null): JSONObject {
        val hs = headers(token)
        val body = packet(operation, data, if (operation == "GET_TOKEN" || operation == "GET_SERVER_INFORMATION") "" else fiscal)
        val signature = sign(companyId, fiscal, LegacyMoadianCanonical.normalize(body, hs))
        return transport.post(operation, JSONObject().put("time", 1).put("packet", body)
            .put("signature", signature).put("signatureKeyId", JSONObject.NULL), hs)
    }

    private suspend fun token(companyId: Long, fiscal: String): String {
        val response = sync(companyId, fiscal, "GET_TOKEN", JSONObject().put("username", fiscal))
        val info = result(response) as? JSONObject ?: error("پاسخ توکن معتبر نیست.")
        val value = info.optJSONObject("data")?.optString("token").orEmpty()
        require(value.isNotBlank() && value.length <= 16000 && value.none { it == '\r' || it == '\n' }) {
            "توکن اتصال مؤدیان معتبر نیست."
        }
        return value
    }

    override suspend fun prepare(companyId: Long, fiscalId: String, uid: String, payload: String): LegacyPreparedInvoice {
        require(fiscalId.matches(Regex("[A-Z0-9]{6}")))
        require(UUID.fromString(uid).toString() == uid)
        val invoice = JSONObject(payload)
        val header = invoice.getJSONObject("header")
        require(header.getInt("inty") == 1 && header.getInt("inp") == 1 && header.getInt("ins") == 1) {
            "ارسال بدون گواهی فعلاً فقط صورتحساب اصلی نوع یک، الگوی فروش یک را پوشش می‌دهد."
        }
        require(invoice.getJSONArray("body").length() > 0 && invoice.getJSONArray("payments").length() == 0)
        require(!invoice.has("__suraProtocol"))
        val auth = token(companyId, fiscalId)
        val meta = result(sync(companyId, fiscalId, "GET_SERVER_INFORMATION", null)) as? JSONObject
            ?: error("اطلاعات کلید سازمان معتبر نیست.")
        val keys = MoadianSignedReceipt.keys(meta.getJSONObject("data").toString())
        val key = keys.firstOrNull { it.purpose == 1 } ?: error("کلید رمزگذاری سازمان دریافت نشد.")
        val cipher = LegacyMoadianEncryption.wrap(invoice, key, rng)
        val row = packet("INVOICE.V01", cipher.first, fiscalId, uid)
            .put("dataSignature", sign(companyId, fiscalId, LegacyMoadianCanonical.normalize(invoice)))
            .put("encryptionKeyId", key.id).put("symmetricKey", cipher.second).put("iv", cipher.third)
        val hs = headers(auth)
        // The legacy SDK signs the batch-shaped normalized packet plus authenticated headers.
        val batch = JSONObject().put("packets", JSONArray().put(JSONArray().put(row)))
        val signature = sign(companyId, fiscalId, LegacyMoadianCanonical.normalize(batch, hs))
        val envelope = JSONObject().put("packets", JSONArray().put(row))
            .put("signature", signature).put("signatureKeyId", JSONObject.NULL)
        require(envelope.toString().length <= 4_000_000)
        return LegacyPreparedInvoice(envelope.toString(), hs)
    }

    override suspend fun send(prepared: LegacyPreparedInvoice, expectedUid: String): LegacyInvoiceAcknowledgement {
        // Request envelope and signed timestamp must not change between preparation and POST.
        val response = transport.post("INVOICE.V01", JSONObject(prepared.envelope),
            prepared.headers)
        val rows = result(response) as? JSONArray ?: error("رسید ارسال معتبر نیست.")
        require(rows.length() == 1)
        val row = rows.getJSONObject(0)
        require(row.getString("uid") == expectedUid) { "شناسه رسید با درخواست اصلی یکسان نیست." }
        val reference = row.optString("referenceNumber").takeUnless { it.isBlank() || it == "null" }
        require(reference == null || (reference.length <= 200 && reference.none(Char::isISOControl)))
        return LegacyInvoiceAcknowledgement(expectedUid, reference)
    }

    override suspend fun inquire(companyId: Long, fiscalId: String, uid: String, taxId: String): LegacyInvoiceInquiry {
        val response = sync(companyId, fiscalId, "INQUIRY_BY_UID",
            JSONArray().put(JSONObject().put("uid", uid).put("fiscalId", fiscalId)),
            token(companyId, fiscalId))
        val raw = result(response)
        val list = (raw as? JSONObject)?.optJSONArray("data") ?: (raw as? JSONArray)
            ?: error("ساختار پاسخ استعلام معتبر نیست.")
        require(list.length() == 1)
        val row = list.getJSONObject(0)
        require(row.getString("uid") == uid && row.optString("fiscalId", fiscalId) == fiscalId)
        val returnedTaxId = row.optString("taxId").takeIf { it.isNotBlank() && it != "null" }
        require(returnedTaxId == null || returnedTaxId == taxId)
        val ref = row.optString("referenceNumber").takeIf { it.isNotBlank() && it != "null" }
        require(ref == null || ref.length <= 200 && ref.none(Char::isISOControl))
        val codes = row.optJSONObject("data")?.optJSONArray("error")
        val errors = if (codes == null) emptyList() else (0 until minOf(codes.length(), 20))
            .mapNotNull { codes.optJSONObject(it)?.optString("code")?.takeIf { c -> c.matches(Regex("[A-Za-z0-9_-]{1,40}")) } }
        return LegacyInvoiceInquiry(uid, returnedTaxId, ref, row.getString("status"), errors)
    }
}
