package ir.saba.invoice.ui

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal object FreeCreditClient {
    private val http = CreditHttpTransport(CreditEndpoint.GIFT)

    // Existing server anti-abuse contract: one promotional redemption per account/device.
    @SuppressLint("HardwareIds")
    suspend fun redeem(context: Context, token: String, promo: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val id = Settings.Secure.getString(context.contentResolver,Settings.Secure.ANDROID_ID)
                ?: error("شناسه دستگاه قابل دریافت نیست.")
            val response = http.post(token,JSONObject().put("code",promo).put("deviceId",id))
            val granted = CreditServerResponse.balance(response,"granted",Int.MAX_VALUE.toLong()).toInt()
            require(granted == 3) { "پاسخ هدیه معتبر نیست." }
            Result.success(granted)
        } catch(e:CancellationException) { throw e }
        catch(e:CreditHttpException) {
            Result.failure(IllegalStateException(when(e.status) {
                409 -> "این هدیه قبلاً با این حساب یا گوشی دریافت شده است."
                400 -> "کد هدیه معتبر نیست."
                else -> "تأیید هدیه از سرور انجام نشد (کد ${e.status})."
            }))
        } catch(e:Exception) { Result.failure(e) }
    }
    suspend fun remaining(token: String): Int = CreditServerResponse.balance(
        http.post(token,JSONObject(),"/status"),"remaining",Int.MAX_VALUE.toLong()).toInt()

    suspend fun consume(token: String, invoiceKey: String): Int = CreditServerResponse.consumed(
        http.post(token,JSONObject().put("invoiceKey",invoiceKey),"/consume"),Int.MAX_VALUE.toLong()).toInt()
}
