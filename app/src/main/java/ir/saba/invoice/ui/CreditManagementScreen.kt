package ir.saba.invoice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    var amount by rememberSaveable(taxpayerId) { mutableStateOf("1") }
    var pendingRequestId by rememberSaveable(taxpayerId) { mutableStateOf<String?>(null) }
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
                    val pending = pendingRequestId
                    if (pending != null) {
                        runCatching { CreditTransferClient.receipt(accessToken, taxpayerId, pending) }
                            .onSuccess { receipt ->
                                if (receipt.optString("status") == "transferred" &&
                                    receipt.optJSONObject("receipt")?.optLong("credits") == amount.toLongOrNull()) {
                                    pendingRequestId = null
                                    message = "انتقال قبلی با رسید معتبر در سرور تأیید شد."
                                } else {
                                    message = "رسید انتقال قبلی تأیید نشد؛ فقط همان شناسه را تکرار کنید."
                                }
                            }
                            .onFailure { message = "استعلام رسید ناموفق بود؛ درخواست قبلی را با همان شناسه تکرار کنید." }
                    }
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
            onValueChange = { if (pendingRequestId == null && it.length <= 9 && it.all(Char::isDigit)) amount = it },
            enabled = !busy && pendingRequestId == null,
            label = { Text("تعداد اعتبار برای انتقال") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text("انتقال از کیف پول مالک به مودی انتخاب‌شده انجام می‌شود.")
        if (pendingRequestId != null) Text("یک درخواست انتقال در انتظار تعیین نتیجه است. برای جلوگیری از برداشت تکراری، مقدار آن قفل شده است.", color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                val n = count ?: return@Button
                val requestId = pendingRequestId ?: UUID.randomUUID().toString().also { pendingRequestId = it }
                busy = true
                message = ""
                scope.launch {
                    runCatching { CreditTransferClient.request(accessToken, taxpayerId, n, requestId) }
                        .onSuccess { response ->
                            if (response.optString("status") in listOf("transferred", "already_applied")) {
                                message = "انتقال تأیید شد؛ موجودی در حال به‌روزرسانی است."
                                pendingRequestId = null
                                reload++
                            } else {
                                message = "پاسخ انتقال نامشخص است؛ درخواست قبلی را دوباره بررسی کنید."
                            }
                        }
                        .onFailure { message = "وضعیت انتقال نامشخص است: ${it.message.orEmpty()}. پیش از تلاش مجدد مانده را بررسی کنید." }
                    busy = false
                }
            },
            enabled = !busy && accessToken.isNotBlank() && count != null &&
                count in 1L..100000000L && ownerBalance != null &&
                (pendingRequestId != null || count <= (ownerBalance ?: 0L)),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "در حال انتقال…" else "تأیید انتقال اعتبار") }
        OutlinedButton(onClick = { reload++ }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("به‌روزرسانی موجودی") }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        OutlinedButton(onClick = onPurchase, modifier = Modifier.fillMaxWidth()) { Text("رفتن به خرید اعتبار") }
        Text("قیمت پایه هر اعتبار: ۱۰۰٬۰۰۰ ریال + ۱۰٪ ارزش افزوده؛ مبلغ نهایی از سرور استعلام می‌شود.")
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
    }
}
