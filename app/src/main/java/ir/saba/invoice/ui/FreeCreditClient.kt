package ir.saba.invoice.ui

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object FreeCreditClient {
    private const val ENDPOINT =
        "https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/sura-free-credit"

    suspend fun redeem(context: Context, token: String, promo: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(token.isNotBlank()) { "ابتدا وارد حساب شوید." }
                val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: throw IllegalStateException("شناسه دستگاه قابل دریافت نیست.")
                val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.doOutput = true
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    val request = JSONObject().put("code", promo).put("deviceId", id).toString()
                    connection.outputStream.use { it.write(request.toByteArray(Charsets.UTF_8)) }
                    val response = connection.responseCode
                    if (response == 409) throw IllegalStateException("این هدیه قبلاً با این حساب یا گوشی دریافت شده است.")
                    if (response == 400) throw IllegalArgumentException("کد هدیه معتبر نیست.")
                    if (response !in 200..299) throw IllegalStateException("تأیید کد از سرور انجام نشد (خطا: $response).")
                    val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val granted = json.optInt("granted", 0)
                    require(granted == 3) { "پاسخ سرور معتبر نیست." }
                    granted
                } finally { connection.disconnect() }
            }
        }
}
