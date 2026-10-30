package ir.saba.invoice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Accessible exclusively through the top-left hamburger menu. */
@Composable
fun AppSettingsScreen(
    fontScale: Float, busy: Boolean,
    onFontScale: (Float) -> Unit, onCommitFontScale: () -> Unit,
    onResetFont: () -> Unit,
    biometricEnabled: Boolean, onDisableBiometrics: () -> Unit,
    onBackToAccount: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("تنظیمات برنامه", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold)
        ElevatedCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("تنظیمات نمایش", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("اندازه متن برنامه: ${(fontScale * 100).toInt()}٪")
                Slider(value = fontScale, onValueChange = onFontScale,
                    enabled = !busy, valueRange = 0.85f..1.35f,
                    onValueChangeFinished = onCommitFontScale)
                OutlinedButton(onClick = onResetFont, enabled = !busy) { Text("اندازه پیش‌فرض") }
            }
        }
        ElevatedCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ورود با اثر انگشت", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(if (biometricEnabled)
                    "فعال است؛ اطلاعات ورود به‌صورت رمزنگاری‌شده در همین دستگاه نگهداری می‌شوند."
                    else "غیرفعال است؛ در ورود بعدی با رمز، امکان فعال‌سازی ارائه می‌شود.")
                if (biometricEnabled)
                    OutlinedButton(onClick = onDisableBiometrics, enabled = !busy) {
                        Text("غیرفعال‌کردن ورود با اثر انگشت")
                    }
            }
        }
        ElevatedCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("اتصال سامانه مؤدیان", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("ارسال واقعی فعال نیست. ثبت محلی صورتحساب از ارسال مالیاتی مستقل است.")
            }
        }
        OutlinedButton(onClick = onBackToAccount, modifier = Modifier.fillMaxWidth()) {
            Text("بازگشت به حساب کاربری")
        }
    }
}
