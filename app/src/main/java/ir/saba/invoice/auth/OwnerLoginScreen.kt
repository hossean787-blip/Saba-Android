package ir.saba.invoice.auth

import androidx.compose.foundation.layout.*
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
    var method by rememberSaveable { mutableStateOf(LoginMethod.PASSWORD) }
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val normalized = RegistrationValidator.normalizeDigits(identifier)
    val validIdentifier = if (method == LoginMethod.SMS_OTP) {
        RegistrationValidator.validMobile(normalized)
    } else {
        RegistrationValidator.validMobile(normalized) || RegistrationValidator.validNationalId(normalized)
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("صبا", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text("ورود به حساب مالک", style = MaterialTheme.typography.headlineSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = method == LoginMethod.PASSWORD,
                onClick = { method = LoginMethod.PASSWORD },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("رمز عبور ثابت") }
            SegmentedButton(
                selected = method == LoginMethod.SMS_OTP,
                onClick = { method = LoginMethod.SMS_OTP },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("کد پیامکی") }
        }
        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = RegistrationValidator.normalizeDigits(it).filter(Char::isDigit).take(11) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (method == LoginMethod.PASSWORD) "موبایل یا کد ملی" else "شماره موبایل") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
                if (method == LoginMethod.PASSWORD) onPasswordLogin(normalized, password)
                else onSmsLogin(normalized)
            },
            enabled = !busy && validIdentifier && (method == LoginMethod.SMS_OTP || password.isNotEmpty()),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (busy) "در حال بررسی..." else if (method == LoginMethod.PASSWORD) "ورود با رمز ثابت" else "دریافت کد تأیید") }
        TextButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("ایجاد حساب مالک جدید") }
    }
}
