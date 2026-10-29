package ir.saba.invoice.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTPS-only transport for Saba's central authentication gateway.
 * Never place service-role keys or SMS provider credentials in the APK.
 * The gateway must enforce OTP expiration, throttling and identity uniqueness.
 */
class AuthGatewayClient(private val baseUrl: String) {
    init {
        require(baseUrl.startsWith("https://")) { "Authentication requires HTTPS" }
    }

    data class Challenge(val id: String, val maskedMobile: String, val expiresInSeconds: Int)
    data class Session(val accessToken: String, val refreshToken: String)

    suspend fun registerWithPassword(input: RegistrationInput): Session? {
        val body = JSONObject()
            .put("firstName", input.firstName)
            .put("lastName", input.lastName)
            .put("email", input.email)
            .put("password", input.password)
        val result = post("/v1/auth/registration/start", body)
        return if (result.has("accessToken")) session(result) else null
    }

    suspend fun startRegistration(input: RegistrationInput): Challenge {
        val body = JSONObject()
            .put("firstName", input.firstName)
            .put("lastName", input.lastName)
            .put("nationalId", RegistrationValidator.normalizeDigits(input.nationalId))
            .put("mobile", RegistrationValidator.normalizeDigits(input.mobile))
            .put("email", input.email)
            .put("password", input.password)
        val response = post("/v1/auth/registration/start", body)
        return Challenge(
            response.getString("challengeId"),
            response.getString("maskedMobile"),
            response.optInt("expiresInSeconds", 120)
        )
    }

    suspend fun verifyRegistration(challengeId: String, code: String): Session =
        session(post("/v1/auth/registration/verify", JSONObject()
            .put("challengeId", challengeId)
            .put("code", RegistrationValidator.normalizeDigits(code))))

    suspend fun loginWithPassword(identifier: String, password: String): Session =
        session(post("/v1/auth/login/password", JSONObject()
            .put("identifier", RegistrationValidator.normalizeDigits(identifier))
            .put("password", password)))

    suspend fun startSmsLogin(mobile: String): Challenge {
        val response = post("/v1/auth/login/otp/start", JSONObject()
            .put("mobile", RegistrationValidator.normalizeDigits(mobile)))
        return Challenge(response.getString("challengeId"), response.getString("maskedMobile"),
            response.optInt("expiresInSeconds", 120))
    }

    suspend fun verifySmsLogin(challengeId: String, code: String): Session =
        session(post("/v1/auth/login/otp/verify", JSONObject()
            .put("challengeId", challengeId)
            .put("code", RegistrationValidator.normalizeDigits(code))))

    suspend fun logout(refreshToken: String) {
        post("/v1/auth/logout", JSONObject().put("refreshToken", refreshToken))
    }

    private fun session(json: JSONObject) = Session(
        json.getString("accessToken"), json.getString("refreshToken"))

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw AuthGatewayException(status)
            }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}

class AuthGatewayException(val httpStatus: Int) : Exception(
    when (httpStatus) {
        400 -> "اطلاعات واردشده معتبر نیست."
        401 -> "اطلاعات ورود یا کد تأیید صحیح نیست."
        408, 429 -> "درخواست‌های زیادی ارسال شده است؛ کمی بعد تلاش کنید."
        503 -> "سرویس احراز هویت هنوز آماده نیست."
        else -> "ارتباط با سرویس احراز هویت ناموفق بود."
    }
)
