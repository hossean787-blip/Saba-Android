package ir.saba.invoice.auth

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Session survives Activity recreation in memory only. No password/token enters saved state,
 * preferences, logs or backups. A new process requires a new login.
 */
class OwnerAuthViewModel(private val gateway: OwnerAuthService) : ViewModel() {
    var session by mutableStateOf<AuthGatewayClient.Session?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var page by mutableStateOf("login")
        private set
    var message by mutableStateOf<String?>(null)
        private set

    // Verified-session profile snapshot. Never cached on disk or derived from
    // taxpayer/company data (which belongs to a separate identity).
    var ownerEmail by mutableStateOf<String?>(null)
        private set
    var ownerName by mutableStateOf<String?>(null)
        private set

    // Transient, never SavedState or disk; exposed only after the server accepts
    // a regular password login. Removed on consent decision or logout.
    var biometricEnrollment by mutableStateOf<StoredLoginCredentials?>(null)
        private set

    fun discardBiometricEnrollment() { biometricEnrollment = null }

    fun navigate(destination: String) {
        if (!busy) { page = destination; message = null }
    }

    fun login(
        email: String, password: String,
        fromBiometric: Boolean = false,
        onInvalidBiometric: () -> Unit = {}
    ) {
        if (busy) return
        if (!RegistrationValidator.validEmail(email) || password.isBlank() || password.length > 128) {
            message = "ایمیل و رمز عبور معتبر وارد کنید."
            return
        }
        biometricEnrollment = null
        submit {
            val verifiedSession = try {
                gateway.loginWithPassword(email.trim(), password)
            } catch (e: AuthGatewayException) {
                if (fromBiometric && e.httpStatus in listOf(401, 403)) onInvalidBiometric()
                throw e
            }
            biometricEnrollment = if (fromBiometric) null
                else StoredLoginCredentials(email.trim(), password)
            ownerEmail = email.trim()
            ownerName = null // The gateway does not return a trusted profile name.
            session = verifiedSession
        }
    }

    fun register(input: RegistrationInput) {
        if (busy) return
        if (RegistrationValidator.emailPasswordErrors(input).isNotEmpty()) {
            message = "اطلاعات ثبت‌نام را اصلاح کنید."
            return
        }
        submit {
            val registered = gateway.registerWithPassword(input)
            session = registered
            if (registered != null) {
                ownerEmail = input.email.trim()
                ownerName = listOf(input.firstName.trim(), input.lastName.trim())
                    .filter { it.isNotBlank() }.joinToString(" ")
            }
            if (registered == null) {
                page = "login"
                message = "برای تکمیل ثبت‌نام، پیوند تأیید را در ایمیل خود بررسی کنید و سپس وارد شوید."
            }
        }
    }

    fun logout() {
        if (busy) return
        val previous = session ?: return
        biometricEnrollment = null
        ownerEmail = null
        ownerName = null
        session = null // Lock access before making a network request, including offline failures.
        page = "login"
        submit {
            try {
                gateway.logout(previous.accessToken)
                message = "از حساب خارج شدید."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                message = "از برنامه خارج شدید؛ لغو نشست سرور تأیید نشد. اتصال اینترنت را بررسی کنید."
            }
        }
    }

    private fun submit(action: suspend () -> Unit) {
        busy = true
        message = null
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: AuthGatewayException) { message = e.message }
            catch (e: Exception) { message = "ارتباط برقرار نشد؛ اتصال اینترنت را بررسی و دوباره تلاش کنید." }
            finally { busy = false }
        }
    }
}
