package ir.saba.invoice.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AccountScreen(
    taxpayerCharge: Long, ownerCharge: Long, fontScale: Float, busy: Boolean,
    onCharge: () -> Unit, onFontScale: (Float) -> Unit, onCommitFontScale: () -> Unit,
    onResetFont: () -> Unit, onLogout: () -> Unit,
    biometricEnabled: Boolean = false, onDisableBiometrics: () -> Unit = {}
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("حساب کاربری", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        ElevatedCard(onClick = onCharge, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("انتقال شارژ", style = MaterialTheme.typography.titleMedium)
                Text("انتقال از شارژ آزاد حساب مالک به مودی انتخاب‌شده")
                Text("شارژ مودی: $taxpayerCharge  |  شارژ آزاد مالک: $ownerCharge",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "بستن تنظیمات برنامه" else "تنظیمات برنامه")
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCharge, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("انتقال شارژ") }
                Text("ورود با اثر انگشت", style = MaterialTheme.typography.titleMedium)
                Text(if (biometricEnabled) "فعال است؛ رمز ورود به‌صورت رمزنگاری‌شده روی این دستگاه نگهداری می‌شود."
                     else "غیرفعال است؛ برای فعال‌سازی، یک‌بار با ایمیل و رمز وارد شوید.")
                if (biometricEnabled) {
                    OutlinedButton(onClick = onDisableBiometrics, enabled = !busy) {
                        Text("غیرفعال‌کردن ورود با اثر انگشت")
                    }
                }
                Text("تنظیمات نمایش", style = MaterialTheme.typography.titleMedium)
                Text("اندازه متن برنامه: ${(fontScale * 100).toInt()}٪؛ اندازه متن سیستم نیز رعایت می‌شود.")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("اندازه نوشته‌ها")
                        Slider(value = fontScale, onValueChange = onFontScale, enabled = !busy,
                            valueRange = 0.85f..1.35f, onValueChangeFinished = onCommitFontScale)
                    }
                }
                OutlinedButton(onClick = onResetFont, enabled = !busy) { Text("اندازه پیش‌فرض") }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("وضعیت سامانه مؤدیان", style = MaterialTheme.typography.titleSmall)
                        Text("اتصال غیرفعال است؛ هیچ صورتحسابی ارسال نمی‌شود.")
                        Text("ثبت محلی فاکتور از ارسال مالیاتی مستقل است.")
                    }
                }
            }
        }
        OutlinedButton(onClick = onLogout, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("خروج از حساب") }
    }
}
