package ir.saba.invoice.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object CreditQuoteClient {
    suspend fun quote(accessToken: String, quantity: Long): JSONObject =
        withContext(Dispatchers.IO) {
            require(accessToken.isNotBlank()) { "نشست کاربری معتبر نیست." }
            require(quantity in 1..999999)
            val connection = URL("https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/sura-credit-quote").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                connection.doOutput = true
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.outputStream.use { it.write(JSONObject().put("quantity", quantity).toString().toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode !in 200..299) error("price_unavailable")
                val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                check(response.optString("currency") == "IRR" && response.optLong("unit_price_rial") > 0) { "invalid_price" }
                response
            } finally {
                connection.disconnect()
            }
        }
}
