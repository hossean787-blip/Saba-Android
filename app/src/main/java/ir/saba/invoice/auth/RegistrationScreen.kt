package ir.saba.invoice.auth

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Production UI shell. No account is created until a trusted authentication backend is connected.
 * The SMS provider's credentials must never be embedded in this Android application.
 */
@Composable
fun RegistrationScreen(
    onSubmit: (RegistrationInput) -> Unit,
    onSignIn: () -> Unit,
    submitting: Boolean = false,
    serverError: String? = null
) {
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var nationalId by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeatPassword by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val input = RegistrationInput(first.trim(), last.trim(), nationalId, mobile, email.trim(), password, repeatPassword)
    val errors = if (attempted) RegistrationValidator.errors(input) else emptySet()
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF5F5FD)).verticalScroll(rememberScrollState()).imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF252C8D), Color(0xFF4936B7), Color(0xFF8277DD)))
            ).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("صبا", style = MaterialTheme.typography.displayMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text("صدور صورتحساب و اتصال به سامانه مؤدیان", color = Color.White)
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .background(Color.White, RoundedCornerShape(26.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Text("ثبت‌نام مالک جدید", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("اطلاعات حساب • تأیید موبایل • تکمیل", color = Color(0xFF4936B7))
        Text("برای شروع، مشخصات خود را تکمیل کنید.", style = MaterialTheme.typography.bodyMedium)
        @Composable fun field(label: String, value: String, change: (String) -> Unit, key: String, type: KeyboardType = KeyboardType.Text, secret: Boolean = false) {
            OutlinedTextField(
                value = value, onValueChange = change, modifier = Modifier.fillMaxWidth(),
                label = { Text(label) }, singleLine = true, isError = key in errors,
                keyboardOptions = KeyboardOptions(keyboardType = type),
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                supportingText = if (key in errors) ({ Text("مقدار واردشده معتبر نیست.") }) else null
            )
        }
        field("نام", first, { first = it }, "firstName")
        field("نام خانوادگی", last, { last = it }, "lastName")
        field("کد ملی (۱۰ رقم)", nationalId, { nationalId = it.take(10) }, "nationalId", KeyboardType.Number)
        field("شماره موبایل", mobile, { mobile = it.take(11) }, "mobile", KeyboardType.Phone)
        field("ایمیل (اجباری)", email, { email = it }, "email", KeyboardType.Email)
        field("رمز عبور ثابت (حداقل ۸ نویسه)", password, { password = it }, "password", KeyboardType.Password, true)
        field("تکرار رمز عبور", repeatPassword, { repeatPassword = it }, "passwordConfirmation", KeyboardType.Password, true)
        Text("با ادامه، شماره موبایل از طریق کد شش‌رقمی با اعتبار دو دقیقه تأیید خواهد شد.",
            style = MaterialTheme.typography.bodySmall)
        if (serverError != null) Text(serverError, color = MaterialTheme.colorScheme.error)
        Button(
            enabled = !submitting,
            onClick = {
                attempted = true
                if (RegistrationValidator.errors(input).isEmpty()) onSubmit(input)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (submitting) "در حال بررسی..." else "ادامه و دریافت کد تأیید") }
        OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
            Text("بازگشت به صفحه ورود")
        }
        Text("اطلاعات شما فقط پس از تأیید امن سرور ثبت خواهد شد.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF268C7C))
        }
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
