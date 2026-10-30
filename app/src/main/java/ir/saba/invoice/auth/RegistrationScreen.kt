package ir.saba.invoice.auth

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Email/password registration; no unused national-ID or mobile collection. */
@Composable
fun RegistrationScreen(
    onSubmit: (RegistrationInput) -> Unit,
    onSignIn: () -> Unit,
    submitting: Boolean = false,
    serverError: String? = null
) {
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val input = RegistrationInput(first.trim(), last.trim(), "", "", email.trim(), password, repeatPassword)
    var revealed by rememberSaveable { mutableIntStateOf(1) }
    val allErrors = RegistrationValidator.emailPasswordErrors(input)
    val errors = allErrors.filter { key ->
        attempted || when (key) {
            "firstName" -> revealed > 1
            "lastName" -> revealed > 2
            "email" -> email.isNotEmpty()
            "password" -> password.isNotEmpty()
            "passwordConfirmation" -> repeatPassword.isNotEmpty()
            else -> false
        }
    }.toSet()
    val currentValue = when (revealed) {
        1 -> first
        2 -> last
        3 -> email
        4 -> password
        else -> repeatPassword
    }
    val requiredKeys = listOf("firstName", "lastName", "email", "password", "passwordConfirmation")
    LaunchedEffect(currentValue, revealed, allErrors, submitting) {
        if (!submitting && revealed < 5 && requiredKeys.take(revealed).none { it in allErrors }) {
            delay(1000)
            revealed += 1
        }
    }
    val messages = mapOf(
        "firstName" to "نام را وارد کنید (حداکثر ۱۰۰ نویسه).",
        "lastName" to "نام خانوادگی را وارد کنید (حداکثر ۱۰۰ نویسه).",
        "email" to "نشانی ایمیل معتبر وارد کنید.",
        "password" to "رمز عبور باید بین ۸ تا ۱۲۸ نویسه و غیرخالی باشد.",
        "passwordConfirmation" to "تکرار رمز عبور مطابقت ندارد."
    )
    AuthLayout("ایجاد حساب کاربری", "حساب خود را با ایمیل و رمز عبور بسازید.") {
        @Composable fun field(label: String, value: String, change: (String) -> Unit, key: String,
            type: KeyboardType = KeyboardType.Text, secret: Boolean = false) {
            var visible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = value, onValueChange = change, enabled = !submitting,
                modifier = Modifier.fillMaxWidth().testTag(key), label = { Text(label) },
                singleLine = true, isError = key in errors,
                textStyle = LocalTextStyle.current.copy(textDirection =
                    if (secret || type == KeyboardType.Email) androidx.compose.ui.text.style.TextDirection.Ltr
                    else androidx.compose.ui.text.style.TextDirection.ContentOrRtl),
                keyboardOptions = KeyboardOptions(keyboardType = type),
                visualTransformation = if (secret && !visible) PasswordVisualTransformation()
                    else androidx.compose.ui.text.input.VisualTransformation.None,
                trailingIcon = if (secret) ({ TextButton(onClick = { visible = !visible }, enabled = !submitting) {
                    Text(if (visible) "پنهان" else "نمایش")
                } }) else null,
                supportingText = if (key in errors) ({ Text(messages.getValue(key)) }) else null
            )
        }
        field("نام", first, { first = it.take(100) }, "firstName")
        AnimatedVisibility(visible = revealed >= 2, enter = fadeIn() + expandVertically()) {
            field("نام خانوادگی", last, { last = it.take(100) }, "lastName")
        }
        AnimatedVisibility(visible = revealed >= 3, enter = fadeIn() + expandVertically()) {
            field("ایمیل", email, { email = it.take(254) }, "email", KeyboardType.Email)
        }
        AnimatedVisibility(visible = revealed >= 4, enter = fadeIn() + expandVertically()) {
            field("رمز عبور ثابت", password, { password = it.take(128) }, "password", KeyboardType.Password, true)
        }
        AnimatedVisibility(visible = revealed >= 5, enter = fadeIn() + expandVertically()) {
            field("تکرار رمز عبور", repeatPassword, { repeatPassword = it.take(128) }, "passwordConfirmation", KeyboardType.Password, true)
        }
        if (revealed >= 4) Text("حداقل ۸ نویسه؛ رمز عبور را در اختیار دیگران نگذارید.", style = MaterialTheme.typography.bodySmall)
        if (revealed >= 5 && repeatPassword.isNotEmpty() &&
            password == repeatPassword && "password" !in allErrors
        ) {
            Text(
                "دو رمز عبور مطابقت دارند.",
                modifier = Modifier.testTag("passwordsMatch"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (serverError != null) {
            Text(serverError, modifier = Modifier.testTag("registrationServerError"),
                color = MaterialTheme.colorScheme.error)
        }
        Button(
            enabled = !submitting && revealed == 5 && allErrors.isEmpty(),
            onClick = {
                attempted = true
                if (RegistrationValidator.emailPasswordErrors(input).isEmpty()) {
                    // Retain non-saved password fields after a rejected server request.
                    // Successful registration removes this screen from composition.
                    onSubmit(input)
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("ساخت حساب کاربری")
        }
        OutlinedButton(onClick = onSignIn, enabled = !submitting, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("بازگشت به صفحه ورود")
        }
        Text("پس از ثبت‌نام، در صورت نیاز پیوند تأیید ایمیل برای شما ارسال می‌شود.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OtpVerificationScreen(
    maskedMobile: String,
    remainingSeconds: Int,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit,
    busy: Boolean = false,
    error: String? = null
) {
    var code by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("تأیید شماره همراه", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("کد شش‌رقمی ارسال‌شده به $maskedMobile را وارد کنید.")
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = RegistrationValidator.normalizeDigits(it).filter(Char::isDigit).take(6) },
            label = { Text("کد تأیید") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text("زمان باقی‌مانده: %02d:%02d".format(remainingSeconds.coerceAtLeast(0) / 60, remainingSeconds.coerceAtLeast(0) % 60))
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onVerify(code) }, enabled = !busy && remainingSeconds > 0 && OtpPolicy.validFormat(code),
            modifier = Modifier.fillMaxWidth()) { Text("تأیید و تکمیل ثبت‌نام") }
        TextButton(onClick = onResend, enabled = !busy && remainingSeconds <= 0) { Text("ارسال مجدد کد") }
        TextButton(onClick = onCancel) { Text("لغو و بازگشت") }
    }
}
