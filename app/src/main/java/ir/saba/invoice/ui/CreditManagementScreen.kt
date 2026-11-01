package ir.saba.invoice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun CreditManagementScreen(
    taxpayerName: String,
    taxpayerId: Long,
    accessToken: String,
    onBack: () -> Unit,
    onPurchase: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var ownerBalance by remember(taxpayerId) { mutableStateOf<Long?>(null) }
    var taxpayerBalance by remember(taxpayerId) { mutableStateOf<Long?>(null) }
    var amount by remember(taxpayerId) { mutableStateOf("1") }
    var pendingRequestId by remember(taxpayerId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(taxpayerId, accessToken, reload) {
        ownerBalance = null
        taxpayerBalance = null
        runCatching { CreditTransferClient.request(accessToken, taxpayerId) }
            .onSuccess {
                if (it.has("ownerBalance") && it.has("taxpayerBalance")) {
                    ownerBalance = it.getLong("ownerBalance").takeIf { n -> n >= 0 }
                    taxpayerBalance = it.getLong("taxpayerBalance").takeIf { n -> n >= 0 }
                } else message = "مانده معتبر از سرور دریافت نشد."
            }
            .onFailure { message = "دریافت موجودی ناموفق بود: ${it.message.orEmpty()}" }
    }
    val count = amount.toLongOrNull()
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("انتقال اعتبار صورتحساب", style = MaterialTheme.typography.headlineSmall)
        Text("هر ۱ اعتبار = ایجاد ۱ صورتحساب")
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مودی: $taxpayerName")
                Text("موجودی مالک: ${ownerBalance?.toString() ?: "در حال استعلام"} اعتبار")
                Text("موجودی مودی: ${taxpayerBalance?.toString() ?: "در حال استعلام"} اعتبار")
            }
        }
        OutlinedTextField(
            value = amount,
            onValueChange = { if (it.length <= 9 && it.all(Char::isDigit)) { amount = it; pendingRequestId = null } },
            label = { Text("تعداد اعتبار برای انتقال") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text("انتقال از کیف پول مالک به مودی انتخاب‌شده انجام می‌شود.")
        Button(
            onClick = {
                val n = count ?: return@Button
                val requestId = pendingRequestId ?: UUID.randomUUID().toString().also { pendingRequestId = it }
                busy = true
                message = ""
                scope.launch {
                    runCatching { CreditTransferClient.request(accessToken, taxpayerId, n, requestId) }
                        .onSuccess {
                            message = "نتیجه درخواست انتقال دریافت شد؛ مانده را دوباره بررسی کنید."
                            reload++
                        }
                        .onFailure { message = "وضعیت انتقال نامشخص است: ${it.message.orEmpty()}. پیش از تلاش مجدد مانده را بررسی کنید." }
                    busy = false
                }
            },
            enabled = !busy && accessToken.isNotBlank() && count != null &&
                count in 1..100000000 && ownerBalance != null && count <= (ownerBalance ?: 0L),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "در حال انتقال…" else "تأیید انتقال اعتبار") }
        OutlinedButton(onClick = { reload++ }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("به‌روزرسانی موجودی") }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        OutlinedButton(onClick = onPurchase, modifier = Modifier.fillMaxWidth()) { Text("رفتن به خرید اعتبار") }
        Text("قیمت هر اعتبار: ۱۰۰٬۰۰۰ ریال؛ مبلغ پرداخت قطعی از سرور دریافت می‌شود.")
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
    }
}
