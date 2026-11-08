package ir.saba.moadian

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** No endpoint defaults: accept only the exact official HTTPS root and port. */
data class GatewayConfig(val baseUrl: String) {
    init {
        val uri = URI(baseUrl)
        require(uri.scheme == "https" && uri.host.equals("tp.tax.gov.ir", ignoreCase = true))
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(uri.port == -1 || uri.port == 443)
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
    }
}

/** Bound untrusted replies to avoid a malformed server response exhausting device memory. */
internal const val MAX_MOADIAN_RESPONSE_BYTES = 8 * 1024 * 1024
internal fun readBoundedMoadianResponse(stream: InputStream): ByteArray {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (true) {
        val count = stream.read(chunk)
        if (count < 0) break
        if (count == 0) continue
        check(out.size() <= MAX_MOADIAN_RESPONSE_BYTES - count) { "Moadian HTTP response exceeds size limit" }
        out.write(chunk, 0, count)
    }
    return out.toByteArray()
}
interface MoadianHttpClient {
    suspend fun post(path: String, bearerToken: String, body: ByteArray): ByteArray
}
class JdkMoadianHttpClient(private val config: GatewayConfig) : MoadianHttpClient {
    override suspend fun post(path: String, bearerToken: String, body: ByteArray): ByteArray {
        require(path.startsWith("/") && !path.startsWith("//") && !path.contains(".."))
        require(bearerToken.isNotBlank() && '\n' !in bearerToken && '\r' !in bearerToken)
        val base = URL(config.baseUrl)
        val url = URL(base, path)
        require(url.protocol == "https" && url.host.equals(base.host, ignoreCase = true))
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 25000
        c.instanceFollowRedirects = false
        c.requestMethod = "POST"
        c.setRequestProperty("Authorization", "Bearer $bearerToken")
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.doOutput = true
        try {
            c.outputStream.use { it.write(body) }
            val status = c.responseCode
            val stream = if (status in 200..299) c.inputStream else c.errorStream
            val data = stream?.use { readBoundedMoadianResponse(it) } ?: byteArrayOf()
            if (status !in 200..299) throw IllegalStateException("Moadian HTTP status $status")
            return data
        } finally { c.disconnect() }
    }
}
