package ir.saba.invoice.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    savedAccountEmails: List<String> = emptyList(),
    autofillCredentials: StoredLoginCredentials? = null,
    onAutofillConsumed: () -> Unit = {},
    onBiometricLogin: (String) -> Unit = {}
) {
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords remain transient and are never placed in saved-instance state.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var selectedSavedAccount by rememberSaveable { mutableStateOf("") }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(savedAccountEmails, autofillCredentials) {
        if (selectedSavedAccount !in savedAccountEmails) {
            selectedSavedAccount = savedAccountEmails.firstOrNull().orEmpty()
        }
        autofillCredentials?.let {
            selectedSavedAccount = it.email
            email = it.email
            password = it.password
            visible = false
            onAutofillConsumed()
        } ?: run {
            if (email.isBlank()) {
                email = selectedSavedAccount.takeUnless {
                    it == BiometricLoginVault.LEGACY_ACCOUNT_ID
                }.orEmpty()
            }
        }
    }

    val valid = RegistrationValidator.validEmail(email) && password.isNotBlank() && password.length <= 128
    fun submit() {
        if (!busy && valid) {
            onPasswordLogin(email.trim(), password)
            password = ""
            visible = false
        }
    }

    AuthLayout("ورود به سورا", "با ایمیل و رمز عبور خود وارد حساب شوید.") {
        OutlinedTextField(
            value = email, onValueChange = { email = it.take(254) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("loginEmailField"), label = { Text("ایمیل") },
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            singleLine = true, isError = email.isNotEmpty() && !RegistrationValidator.validEmail(email),
            supportingText = if (email.isNotEmpty() && !RegistrationValidator.validEmail(email))
                ({ Text("نشانی ایمیل معتبر وارد کنید.") }) else null
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it.take(128) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("loginPasswordField"), label = { Text("رمز عبور ثابت") },
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
        if (biometricEnabled && savedAccountEmails.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("حساب ذخیره‌شده برای ورود", style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(onClick = { accountMenuExpanded = true }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(when (selectedSavedAccount) {
                            BiometricLoginVault.LEGACY_ACCOUNT_ID -> "حساب قبلی ذخیره‌شده"
                            "" -> "انتخاب حساب کاربری"
                            else -> selectedSavedAccount
                        }, maxLines = 1)
                    }
                    DropdownMenu(expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false }) {
                        savedAccountEmails.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(if (account == BiometricLoginVault.LEGACY_ACCOUNT_ID)
                                    "حساب قبلی ذخیره‌شده" else account) },
                                onClick = {
                                    selectedSavedAccount = account
                                    email = account.takeUnless {
                                        it == BiometricLoginVault.LEGACY_ACCOUNT_ID
                                    }.orEmpty()
                                    password = ""
                                    accountMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { if (selectedSavedAccount.isNotBlank()) onBiometricLogin(selectedSavedAccount) },
                    enabled = !busy && selectedSavedAccount.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("بازکردن رمز با اثر انگشت") }
                Text("حساب را انتخاب کنید؛ پس از تأیید اثر انگشت، نام کاربری و رمز همان حساب خودکار تکمیل می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
