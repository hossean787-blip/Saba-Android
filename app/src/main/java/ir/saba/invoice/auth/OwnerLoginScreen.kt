package ir.saba.invoice.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

@Composable
fun OwnerLoginScreen(
    onPasswordLogin: (identifier: String, password: String) -> Unit,
    onSmsLogin: (mobile: String) -> Unit,
    onRegister: () -> Unit,
    busy: Boolean = false,
    error: String? = null,
    biometricEnabled: Boolean = false,
    onBiometricLogin: () -> Unit = {}
) {
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords must never enter Activity saved-instance state.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val valid = RegistrationValidator.validEmail(email) && password.isNotBlank() && password.length <= 128
    fun submit() {
        if (!busy && valid) { onPasswordLogin(email.trim(), password); password = ""; visible = false }
    }
    AuthLayout("ورود به سورا", "با ایمیل و رمز عبور خود وارد حساب شوید.") {
        OutlinedTextField(
            value = email, onValueChange = { email = it.take(254) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth(), label = { Text("ایمیل") },
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            singleLine = true, isError = email.isNotEmpty() && !RegistrationValidator.validEmail(email),
            supportingText = if (email.isNotEmpty() && !RegistrationValidator.validEmail(email))
                ({ Text("نشانی ایمیل معتبر وارد کنید.") }) else null
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it.take(128) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth(), label = { Text("رمز عبور ثابت") },
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { visible = !visible }, enabled = !busy) {
                Text(if (visible) "پنهان" else "نمایش")
            } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }), singleLine = true
        )
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { submit() }, enabled = !busy && valid, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("ورود با رمز ثابت")
        }
        if (biometricEnabled) {
            OutlinedButton(
                onClick = onBiometricLogin, enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("ورود با اثر انگشت") }
        }
        HorizontalDivider()
        OutlinedButton(onClick = onRegister, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("ایجاد حساب کاربری")
        }
        Text(
            if (biometricEnabled) "برای ورود می‌توانید از اثر انگشت یا رمز ثابت استفاده کنید."
            else "برای حفظ امنیت، پس از بسته‌شدن کامل برنامه دوباره وارد شوید.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
