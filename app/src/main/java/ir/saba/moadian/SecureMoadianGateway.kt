package ir.saba.moadian

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** No endpoint defaults: caller must provide a vetted official URL and token. */
data class GatewayConfig(val baseUrl: String) {
    init {
        require(baseUrl.startsWith("https://"))
        require(URL(baseUrl).host.equals("tp.tax.gov.ir", ignoreCase = true))
        require(!URL(baseUrl).userInfo.orEmpty().isNotEmpty())
    }
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
            val data = stream?.use { it.readBytes() } ?: byteArrayOf()
            if (status !in 200..299) throw IllegalStateException("Moadian HTTP status $status")
            return data
        } finally { c.disconnect() }
    }
}
