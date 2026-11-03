package ir.saba.invoice.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Only fixed v2 paths; test doubles replace this interface, never the production origin. */
interface OfficialTaxTransport {
    suspend fun request(method: String, path: String, token: String? = null, body: String? = null): String
}
class OfficialMoadianTransport : OfficialTaxTransport {
    override suspend fun request(method: String, path: String, token: String?, body: String?): String = withContext(Dispatchers.IO) {
        require(method == "GET" || (method == "POST" && path == "invoice"))
        require(path == "invoice" || path == "nonce?timeToLive=30" || path == "server-information" || path.startsWith("inquiry-by-uid?"))
        require(!path.contains('#') && !path.contains('\r') && !path.contains('\n') && path.length <= 4096)
        require(token == null || (token.isNotBlank() && token.none { it == '\r' || it == '\n' }))
        val connection = URL("https://tp.tax.gov.ir/requestsmanager/api/v2/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 15000
            connection.readTimeout = 25000
            connection.setRequestProperty("Accept","application/json")
            if (token != null) connection.setRequestProperty("Authorization","Bearer $token")
            if (body != null) {
                require(method == "POST" && body.length <= 4_000_000)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            require(status in 200..299) { "پاسخ سامانه مالیاتی ناموفق بود (کد $status)." }
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 2_000_000) { "پاسخ سامانه بیش از حد بزرگ است." }
                    output.write(buffer,0,count)
                }
                output.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
}
