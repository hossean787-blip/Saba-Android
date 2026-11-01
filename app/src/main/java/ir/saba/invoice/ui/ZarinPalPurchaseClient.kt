package ir.saba.invoice.ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object ZarinPalPurchaseClient {
    suspend fun create(token:String, quantity:Long):JSONObject = withContext(Dispatchers.IO) {
        require(token.isNotBlank() && quantity in 1L..999999L)
        val conn=URL("https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/sura-zarinpal/create").openConnection() as HttpURLConnection
        try {
            conn.requestMethod="POST"
            conn.doOutput=true
            conn.instanceFollowRedirects=false
            conn.connectTimeout=15000
            conn.readTimeout=20000
            conn.setRequestProperty("Authorization","Bearer $token")
            conn.setRequestProperty("Content-Type","application/json")
            conn.outputStream.use { it.write(JSONObject().put("quantity",quantity).toString().toByteArray(Charsets.UTF_8)) }
            val status=conn.responseCode
            val body=(if(status in 200..299)conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
            val response=JSONObject(body)
            if(status !in 200..299) error(if(response.optString("error")=="merchant_not_configured") "درگاه هنوز در سرور فعال نشده است." else "ساخت سفارش پرداخت ناموفق بود.")
            require(response.optString("paymentUrl").startsWith("https://www.zarinpal.com/pg/StartPay/")) { "نشانی درگاه معتبر نیست." }
            response
        } finally { conn.disconnect() }
    }
}