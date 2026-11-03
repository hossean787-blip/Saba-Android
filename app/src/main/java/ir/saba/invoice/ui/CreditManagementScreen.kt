package ir.saba.invoice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ir.saba.invoice.security.AccountScope
import ir.saba.invoice.security.AccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

@Composable
fun CreditManagementScreen(
    db: ir.saba.invoice.data.SabaDatabase,
    ownerId: String,
    taxpayerName: String,
    taxpayerId: Long,
    accessToken: String,
    onBack: () -> Unit,
    onPurchase: () -> Unit
) {
    val states = rememberSaveableStateHolder()
    states.SaveableStateProvider("${ir.saba.invoice.security.AccountScope.of(ownerId).storageKey}:$taxpayerId") {
        key(ownerId,taxpayerId) { CreditManagementContent(db,ownerId,taxpayerName,taxpayerId,accessToken,onBack,onPurchase) }
    }
}

@Composable
private fun CreditManagementContent(
    db: ir.saba.invoice.data.SabaDatabase,
    ownerId: String,
    taxpayerName: String,
    taxpayerId: Long,
    accessToken: String,
    onBack: () -> Unit,
    onPurchase: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val account = remember(ownerId) { AccountScope.of(ownerId) }
    val journal = remember(db,account,taxpayerId) { CreditTransferJournal(db,account,taxpayerId) }
    var ownerBalance by remember(taxpayerId) { mutableStateOf<Long?>(null) }
    var taxpayerBalance by remember(taxpayerId) { mutableStateOf<Long?>(null) }
    var amount by rememberSaveable(taxpayerId) { mutableStateOf("1") }
    var pendingRequestId by rememberSaveable(taxpayerId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(taxpayerId, accessToken, reload) {
        ownerBalance=null;taxpayerBalance=null
        try {
            val restored = withContext(Dispatchers.IO) {
                journal.pending() ?: pendingRequestId?.let { journal.begin(requireNotNull(amount.toLongOrNull()),it) }
            }
            if (restored != null) { pendingRequestId=restored.requestId;amount=restored.amount.toString() }
            AccountSession.requireOwner(accessToken,account)
            val response = CreditTransferClient.request(accessToken,taxpayerId)
            val owner = CreditServerResponse.balance(response,"ownerBalance")
            val taxpayer = CreditServerResponse.balance(response,"taxpayerBalance")
            ownerBalance=owner;taxpayerBalance=taxpayer
            if (restored != null) {
                AccountSession.requireOwner(accessToken,account)
                val receipt = CreditTransferClient.receipt(accessToken,taxpayerId,restored.requestId)
                if (withContext(Dispatchers.IO) { journal.complete(restored,receipt) }) {
                    pendingRequestId=null
                    message="انتقال قبلی با رسید همان درخواست در سرور تأیید شد."
                } else message="رسید انتقال قبلی تأیید نشد؛ فقط همان شناسه را تکرار کنید."
            }
        } catch(e:CancellationException) { throw e }
        catch(_:Exception) { message="دریافت مانده یا رسید ناموفق بود؛ درخواست قبلی محفوظ است. نشست و اتصال را بررسی کنید." }
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
                busy = true
                message = ""
                scope.launch {
                    try {
                        AccountSession.requireOwner(accessToken,account)
                        val claim = withContext(Dispatchers.IO) { journal.begin(n,pendingRequestId) }
                        pendingRequestId=claim.requestId
                        amount=claim.amount.toString()
                        CreditTransferClient.request(accessToken,taxpayerId,claim.amount,claim.requestId)
                        val receipt = CreditTransferClient.receipt(accessToken,taxpayerId,claim.requestId)
                        if (withContext(Dispatchers.IO) { journal.complete(claim,receipt) }) {
                            message="انتقال با رسید همان درخواست تأیید شد."
                            pendingRequestId=null
                            reload++
                        } else message="رسید معتبر همان درخواست دریافت نشد؛ شناسهٔ قبلی محفوظ است."
                    } catch(e:CancellationException) { throw e }
                    catch(_:Exception) { message="نتیجه انتقال قطعی نیست؛ درخواست محفوظ را با همان شناسه پیگیری کنید." }
                    finally { busy=false }
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
