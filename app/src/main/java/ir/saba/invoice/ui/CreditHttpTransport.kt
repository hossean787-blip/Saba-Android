package ir.saba.invoice.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal enum class CreditEndpoint(val function: String) { PAID("sura-credit-transfer"), GIFT("sura-free-credit") }

internal class CreditHttpException(val status: Int) : IllegalArgumentException("اعتبار از سرور تأیید نشد (کد $status).")

/** Fixed authenticated origin, no redirects/cache or transparent streaming POST replay. */
internal class CreditHttpTransport(
    private val endpoint: CreditEndpoint,
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    suspend fun post(token: String, body: JSONObject, path: String = ""): JSONObject = withContext(Dispatchers.IO) {
        require(token.length in 1..32768 && token.none { it.isWhitespace() || it.isISOControl() }) { "نشست ورود معتبر نیست." }
        require(path == "" || (endpoint == CreditEndpoint.GIFT && path in setOf("/status","/consume")))
        val url = URL("https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/${endpoint.function}$path")
        val connection = openConnection(url)
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Authorization","Bearer $token")
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            if (status !in 200..299) throw CreditHttpException(status)
            val raw = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(output.size()+n <= 65536) { "پاسخ اعتبار بیش از حد بزرگ است." }
                    output.write(buffer,0,n)
                }
                output.toString("UTF-8")
            }
            JSONObject(raw).also { require(!it.has("error")) { "سرور عملیات اعتبار را تأیید نکرد." } }
        } finally { connection.disconnect() }
    }
}
