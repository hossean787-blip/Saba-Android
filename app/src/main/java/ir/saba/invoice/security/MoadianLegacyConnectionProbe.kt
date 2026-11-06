package ir.saba.invoice.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Read-only legacy self-TSP connection check; never sends or authorizes an invoice. */
internal interface LegacyMoadianTransport {
    suspend fun post(operation: String, envelope: JSONObject, headers: Map<String, String>): JSONObject
}

internal class OfficialLegacyMoadianTransport : LegacyMoadianTransport {
    override suspend fun post(
        operation: String, envelope: JSONObject, headers: Map<String, String>
    ): JSONObject = withContext(Dispatchers.IO) {
        require(operation in setOf("GET_TOKEN", "GET_FISCAL_INFORMATION"))
        val url = URL("https://tp.tax.gov.ir/req/api/self-tsp/sync/$operation")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (name, value) ->
                require(name in setOf("requestTraceId", "timestamp", "Authorization"))
                require(value.isNotBlank() && value.none { it == '\n' || it == '\r' })
                connection.setRequestProperty(name, value)
            }
            val data = envelope.toString().toByteArray(Charsets.UTF_8)
            require(data.size <= 24000)
            connection.setFixedLengthStreamingMode(data.size)
            connection.outputStream.use { it.write(data) }
            val status = connection.responseCode
            require(status in 200..299) {
                "سامانه اتصال بدون گواهی را تأیید نکرد (HTTP $status)."
            }
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 128000) { "پاسخ سرور بیش از حد مجاز است." }
                    output.write(buffer, 0, count)
                }
                JSONObject(output.toString("UTF-8"))
            }
        } finally { connection.disconnect() }
    }
}

/** Authenticates with the legacy no-certificate API and reads only taxpayer memory metadata.
 * Success is specific to this legacy API and does NOT certify v2 invoice submission capability. */
internal class MoadianLegacyConnectionProbe(
    private val repository: TaxpayerCredentialRepository,
    private val transport: LegacyMoadianTransport = OfficialLegacyMoadianTransport(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val nextId: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun check(companyId: Long, fiscalId: String) {
        require(fiscalId.matches(Regex("[A-Z0-9]{6}")))
        val tokenResult = signedRead(companyId, fiscalId, "GET_TOKEN",
            JSONObject().put("username", fiscalId), null)
        val tokenData = successfulData(tokenResult) as? JSONObject
            ?: error("سامانه توکن قابل‌استفاده صادر نکرد.")
        val token = tokenData.optString("token")
        require(token.isNotBlank() && token.length <= 16000 &&
            token.none { it == '\n' || it == '\r' }) {
            "پاسخ احراز هویت شامل توکن معتبر نیست."
        }
        val memoryResult = signedRead(companyId, fiscalId,
            "GET_FISCAL_INFORMATION", null, token)
        val data = successfulData(memoryResult) as? JSONObject
            ?: error("پاسخ استعلام حافظه مالیاتی معتبر نیست.")
        require(
            listOf("fiscalStatus", "economicCode", "nameTrade", "saleThreshold")
                .any { field -> data.has(field) && !data.isNull(field) }
        ) { "اطلاعات حافظه مالیاتی از سرور دریافت نشد." }
        if (data.has("fiscalId") && !data.isNull("fiscalId"))
            require(data.getString("fiscalId") == fiscalId) {
                "شناسه حافظه در پاسخ سرور با مؤدی انتخاب‌شده تطابق ندارد."
            }
        // Never store or print the bearer token, sensitive response, key or signature.
    }

    private suspend fun signedRead(
        companyId: Long, fiscalId: String, operation: String, data: JSONObject?, token: String?
    ): JSONObject {
        val headers = linkedMapOf(
            "requestTraceId" to nextId(),
            "timestamp" to clock().toString()
        )
        require(headers["requestTraceId"]!!.matches(Regex("[a-fA-F0-9-]{36}")))
        if (token != null) headers["Authorization"] = "Bearer $token"
        val packet = JSONObject()
            .put("uid", JSONObject.NULL)
            .put("packetType", operation)
            .put("retry", "false")
            .put("data", data ?: JSONObject.NULL)
            .put("encryptionKeyId", "")
            .put("symmetricKey", "")
            .put("iv", "")
            .put("fiscalId", if (operation == "GET_FISCAL_INFORMATION") fiscalId else "")
            .put("dataSignature", "")
        val signature = repository.signPendingConnectionChallenge(
            companyId, fiscalId, canonical(packet, headers).toByteArray(Charsets.UTF_8))
        val envelope = JSONObject()
            .put("time", 1)
            .put("packet", packet)
            .put("signature", signature)
            .put("signatureKeyId", JSONObject.NULL)
        return transport.post(operation, envelope, headers)
    }

    private fun successfulData(response: JSONObject): Any {
        val result = response.optJSONObject("result")
            ?: error("پاسخ معتبر از سامانه دریافت نشد.")
        require(result.optString("errorCode").isBlank() &&
            result.optString("errorDetail").isBlank()) { "سامانه درخواست را رد کرد." }
        val data = result.opt("data")
        require(data != null && data != JSONObject.NULL) { "اطلاعات مورد انتظار از سامانه دریافت نشد." }
        return data
    }

    /** Legacy SDK canonical text: flatten packet and headers, sort keys, escape hash separators. */
    private fun canonical(packet: JSONObject, headers: Map<String, String>): String {
        val fields = sortedMapOf<String, String?>()
        fun flatten(prefix: String, value: Any?) {
            when (value) {
                is JSONObject -> value.keys().forEach { key ->
                    flatten(if (prefix.isEmpty()) key else "$prefix.$key", value.opt(key))
                }
                is JSONArray -> for (i in 0 until value.length()) flatten("$prefix.E$i", value.opt(i))
                null, JSONObject.NULL -> fields[prefix] = null
                else -> fields[prefix] = value.toString()
            }
        }
        flatten("", packet)
        headers.forEach { (name, value) ->
            fields[name] = if (name == "Authorization") value.removePrefix("Bearer ") else value
        }
        return fields.values.joinToString("#") {
            if (it.isNullOrEmpty()) "#" else it.replace("#", "##")
        }
    }
}
