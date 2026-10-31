package ir.saba.invoice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Purchase and allocation UI. Server-verified balances and payment APIs are mandatory
 * before enabling financial operations. No local values are represented as money.
 */
@Composable
fun CreditManagementScreen(
    taxpayerName: String,
    onBack: () -> Unit,
    paymentServiceReady: Boolean = false
) {
    var tab by remember { mutableIntStateOf(0) }
    var pack by remember { mutableIntStateOf(100) }
    var amount by remember { mutableStateOf("10") }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("اعتبار صورتحساب سورا", style = MaterialTheme.typography.headlineSmall)
        Text("هر ۱ اعتبار = امکان ایجاد ۱ صورتحساب جدید")
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مودی انتخاب‌شده: $taxpayerName")
                Text("موجودی تأییدشده: در انتظار اتصال به سرور مالی")
                Text("اعتبار کیف پول مالک: در انتظار اتصال به سرور مالی")
            }
        }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("خرید اعتبار") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("انتقال اعتبار") })
        }
        if (tab == 0) {
            Text("بسته‌های اعتباری", style = MaterialTheme.typography.titleMedium)
            Text("قیمت بسته‌ها پس از دریافت از سرور فروش نمایش داده می‌شود.")
            listOf(50, 100, 250, 500).forEach { count ->
                FilterChip(
                    selected = pack == count,
                    onClick = { pack = count },
                    label = { Text("$count اعتبار") }
                )
            }
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("پرداخت امن (در انتظار اتصال و تأیید درگاه)")
            }
        } else {
            Text("انتقال از کیف پول مالک به مودی انتخاب‌شده", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = amount,
                onValueChange = { if (it.length <= 9 && it.all(Char::isDigit)) amount = it },
                label = { Text("تعداد اعتبار") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("انتقال فقط پس از دریافت مانده معتبر و ثبت تراکنش اتمیک در سرور مجاز است.")
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("تأیید انتقال (در انتظار سرویس مالی)")
            }
        }
        if (!paymentServiceReady) {
            Text(
                "این صفحه پیش‌نمایش عملیاتی بدون پرداخت است؛ هیچ وجهی دریافت و هیچ اعتباری جابه‌جا نمی‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
    }
}
