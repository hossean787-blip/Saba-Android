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
interface OwnerAuthService {
    suspend fun registerWithPassword(input: RegistrationInput): AuthGatewayClient.Session?
    suspend fun loginWithPassword(identifier: String, password: String): AuthGatewayClient.Session
    suspend fun logout(accessToken: String)
}

class AuthGatewayClient(
    private val baseUrl: String,
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) : OwnerAuthService {
    init {
        require(URL(baseUrl).let { it.protocol == "https" && it.host.isNotBlank() && it.userInfo == null }) { "Authentication requires HTTPS" }
    }

    data class Challenge(val id: String, val maskedMobile: String, val expiresInSeconds: Int)
    data class Session(val accessToken: String, val refreshToken: String) {
        init { require(accessToken.isNotBlank() && refreshToken.isNotBlank()) }
        override fun toString() = "Session([redacted])"
    }

    override suspend fun registerWithPassword(input: RegistrationInput): Session? {
        require(RegistrationValidator.emailPasswordErrors(input).isEmpty())
        val body = JSONObject()
            .put("firstName", input.firstName.trim())
            .put("lastName", input.lastName.trim())
            .put("email", input.email.trim())
            .put("password", input.password)
        val result = post("/v1/auth/registration/start", body)
        return when {
            result.has("accessToken") -> session(result)
            result.optString("status") == "email_confirmation_required" -> null
            else -> throw java.io.IOException("Invalid authentication response")
        }
    }

    suspend fun startRegistration(input: RegistrationInput): Challenge {
        val body = JSONObject()
            .put("firstName", input.firstName)
            .put("lastName", input.lastName)
            .put("nationalId", RegistrationValidator.normalizeDigits(input.nationalId))
            .put("mobile", RegistrationValidator.normalizeDigits(input.mobile))
            .put("email", input.email.trim())
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

    override suspend fun loginWithPassword(identifier: String, password: String): Session =
        session(post("/v1/auth/login/password", JSONObject()
            .put("identifier", identifier.trim())
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

    override suspend fun logout(accessToken: String) {
        require(accessToken.isNotBlank())
        post("/v1/auth/logout", JSONObject().put("accessToken", accessToken))
    }

    private fun session(json: JSONObject) = Session(
        json.getString("accessToken"), json.getString("refreshToken"))

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val connection = openConnection(URL(baseUrl.trimEnd('/') + path))
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw AuthGatewayException(status, isRegistration = path == "/v1/auth/registration/start")
            }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}

class AuthGatewayException(val httpStatus: Int, val isRegistration: Boolean = false) : Exception(
    when {
        isRegistration && httpStatus == 400 ->
            "سرور اطلاعات ثبت‌نام را نپذیرفت؛ شرایط رمز عبور و اعتبار ایمیل را بررسی کنید. این خطا به‌معنای متفاوت بودن دو رمز نیست."
        isRegistration && httpStatus == 409 ->
            "این حساب ممکن است قبلاً ثبت شده باشد. ورود یا بازیابی حساب را بررسی کنید."
        isRegistration && httpStatus == 422 ->
            "سرور شرایط ثبت‌نام را تأیید نکرد؛ ایمیل و ضوابط رمز عبور را بررسی کنید."
        httpStatus == 400 -> "اطلاعات واردشده معتبر نیست."
        httpStatus == 401 || httpStatus == 403 ->
            "ایمیل یا رمز عبور صحیح نیست؛ تأیید ایمیل را نیز بررسی کنید."
        httpStatus == 408 || httpStatus == 429 ->
            "درخواست‌های زیادی ارسال شده است؛ کمی بعد تلاش کنید."
        httpStatus == 503 -> "سرویس احراز هویت هنوز آماده نیست."
        else -> "ارتباط با سرویس احراز هویت ناموفق بود."
    }
)
