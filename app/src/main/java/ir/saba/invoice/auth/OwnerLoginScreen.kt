package ir.saba.invoice.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Login UI only. Backend must authenticate before allowing dashboard access. */
@Composable
fun OwnerLoginScreen(
    onPasswordLogin: (identifier: String, password: String) -> Unit,
    onSmsLogin: (mobile: String) -> Unit,
    onRegister: () -> Unit,
    busy: Boolean = false,
    error: String? = null
) {
    val method = LoginMethod.PASSWORD
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val normalized = RegistrationValidator.normalizeDigits(identifier)
    val validIdentifier = if (method == LoginMethod.SMS_OTP) {
        RegistrationValidator.validMobile(normalized)
    } else {
        RegistrationValidator.validEmail(identifier)
    }
    val violet = Color(0xFF4936B7)
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5FD))
            .verticalScroll(rememberScrollState()).imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF252C8D), violet, Color(0xFF8277DD))))
                .padding(horizontal = 24.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("صبا", style = MaterialTheme.typography.displayMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text("صدور صورتحساب و اتصال به سامانه مؤدیان", color = Color.White)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .background(Color.White, RoundedCornerShape(26.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        Text("ورود به صبا", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("مدیریت هوشمند صورتحساب و اطلاعات مالیاتی", color = Color.Gray)
        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = if (method == LoginMethod.PASSWORD) it.trim().take(254) else RegistrationValidator.normalizeDigits(it).filter(Char::isDigit).take(11) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (method == LoginMethod.PASSWORD) "ایمیل" else "شماره موبایل") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true
        )
        if (method == LoginMethod.PASSWORD) {
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("رمز عبور ثابت") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                if (method == LoginMethod.PASSWORD) onPasswordLogin(identifier.trim(), password)
                else onSmsLogin(normalized)
            },
            enabled = !busy && validIdentifier && (method == LoginMethod.SMS_OTP || password.isNotEmpty()),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (busy) "در حال بررسی..." else if (method == LoginMethod.PASSWORD) "ورود با رمز ثابت" else "دریافت کد تأیید") }
        HorizontalDivider()
        OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)) { Text("ثبت‌نام مالک جدید") }
        Text("ورود امن با ایمیل و رمز عبور", color = Color(0xFF268C7C), style = MaterialTheme.typography.bodySmall)
        }
    }
}
