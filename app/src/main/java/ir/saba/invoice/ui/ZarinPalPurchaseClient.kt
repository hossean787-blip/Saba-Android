package ir.saba.invoice.ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object ZarinPalPurchaseClient {
    internal fun parsePendingOrder(response: JSONObject): String? {
        val value = response.opt("orderId")
        if (value == null || value == JSONObject.NULL) return null
        require(value is String && value.matches(
            Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
        )) { "شناسه سفارش بازیابی‌شده معتبر نیست." }
        return value
    }

    suspend fun pending(token: String): String? = withContext(Dispatchers.IO) {
        require(token.isNotBlank())
        val conn = URL("https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/sura-zarinpal/pending")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) error("بازیابی سفارش پرداخت از سرور انجام نشد.")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parsePendingOrder(JSONObject(body))
        } finally { conn.disconnect() }
    }

    /** Only the server's error category is shown, never its raw gateway response. */
    internal fun createFailureMessage(status: Int, response: JSONObject): String =
        when (response.optString("error")) {
            "merchant_not_configured" -> "شناسه پذیرنده در سرور سورا تنظیم نشده است."
            "gateway_network_unavailable" -> "ارتباط سرور سورا با زرین‌پال برقرار نشد. مسیر شبکه درگاه باید بررسی شود."
            "gateway_http_error" -> {
                val code = response.optInt("httpStatus", 0).takeIf { it in 400..599 }
                "درخواست توسط سرور زرین‌پال پذیرفته نشد" +
                    (if (code != null) " (HTTP $code)." else ".") +
                    " وضعیت سرویس و پذیرنده را بررسی کنید."
            }
            "gateway_unavailable", "gateway_rejected" ->
                "درگاه زرین‌پال سفارش را نپذیرفت؛ وضعیت فعال‌سازی پذیرنده را بررسی کنید."
            "order_service_unavailable" ->
                "سرور ثبت سفارش موقتاً در دسترس نیست. سفارش جدیدی پرداخت نکنید."
            "unauthorized" -> "نشست ورود منقضی شده است. دوباره وارد حساب شوید."
            "price_unavailable" -> "قیمت اعتبار از سرور دریافت نشد."
            "payment_service_unavailable" -> "ساخت سفارش در سرور انجام نشد؛ اتصال درگاه را بررسی کنید."
            else -> "ساخت سفارش پرداخت ناموفق بود (HTTP $status)."
        }

    suspend fun status(token: String, orderId: String): JSONObject = withContext(Dispatchers.IO) {
        require(token.isNotBlank() && orderId.matches(Regex("[0-9a-fA-F-]{36}")))
        val conn = URL("https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/sura-zarinpal/status").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(JSONObject().put("orderId", orderId).toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val raw = (if (status in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader().use { it.readText() }
            if (status !in 200..299) error("استعلام وضعیت پرداخت ناموفق بود.")
            JSONObject(raw)
        } finally { conn.disconnect() }
    }
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
            val body=(if(status in 200..299)conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response=runCatching { JSONObject(body) }.getOrDefault(JSONObject())
            if(status !in 200..299) error(createFailureMessage(status, response))
            require(response.optString("paymentUrl").startsWith("https://www.zarinpal.com/pg/StartPay/")) { "نشانی درگاه معتبر نیست." }
            response
        } finally { conn.disconnect() }
    }
}