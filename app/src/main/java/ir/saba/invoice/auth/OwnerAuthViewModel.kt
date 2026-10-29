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

    fun navigate(destination: String) {
        if (!busy) { page = destination; message = null }
    }

    fun login(email: String, password: String) {
        if (busy) return
        if (!RegistrationValidator.validEmail(email) || password.isBlank() || password.length > 128) {
            message = "ایمیل و رمز عبور معتبر وارد کنید."
            return
        }
        submit {
            session = gateway.loginWithPassword(email.trim(), password)
        }
    }

    fun register(input: RegistrationInput) {
        if (busy) return
        if (RegistrationValidator.emailPasswordErrors(input).isNotEmpty()) {
            message = "اطلاعات ثبت‌نام را اصلاح کنید."
            return
        }
        submit {
            session = gateway.registerWithPassword(input)
            if (session == null) {
                page = "login"
                message = "برای تکمیل ثبت‌نام، پیوند تأیید را در ایمیل خود بررسی کنید و سپس وارد شوید."
            }
        }
    }

    fun logout() {
        if (busy) return
        val previous = session ?: return
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
