package ir.saba.invoice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.SabaDatabase
import ir.saba.invoice.data.MoadianDetails
import ir.saba.invoice.security.TaxpayerKeyGenerator
import ir.saba.invoice.security.TaxpayerKeyVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Fiscal credentials are managed separately from taxpayer identity registration. */
@Composable
fun TaxpayerKeysScreen(
    db: SabaDatabase,
    companyId: Long?,
    taxpayerName: String?,
    onNeedTaxpayer: () -> Unit,
    onNotice: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault = remember { TaxpayerKeyVault() }
    var busy by remember(companyId) { mutableStateOf(false) }
    var encrypted by remember(companyId) { mutableStateOf<String?>(null) }
    var publicPem by remember(companyId) { mutableStateOf("") }
    var pendingPrivatePem by remember(companyId) { mutableStateOf("") }
    var exported by remember(companyId) { mutableStateOf(false) }
    var confirmed by remember(companyId) { mutableStateOf(false) }
    var loaded by remember(companyId) { mutableStateOf(false) }
    var loadError by remember(companyId) { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-pem-file")) { uri ->
        if (uri != null && publicPem.isNotBlank()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(publicPem.toByteArray(Charsets.US_ASCII))
                } ?: error("No output")
                exported = true
                onNotice("فایل عمومی ذخیره شد؛ فقط همین فایل را در کارپوشه بارگذاری کنید.")
            } catch (_: Exception) { onNotice("ذخیره کلید عمومی انجام نشد.") }
        }
    }
    LaunchedEffect(companyId) {
        if (companyId == null) return@LaunchedEffect
        try {
            encrypted = db.moadianDetailsDao().byCompany(companyId)?.encryptedPrivateKey
            loadError = false
        } catch (_: Exception) { loadError = true }
        loaded = true
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("کلیدهای مالیاتی", style = MaterialTheme.typography.headlineSmall)
        if (companyId == null) {
            Text("ابتدا مؤدی را ثبت یا انتخاب کنید.")
            Button(onClick = onNeedTaxpayer) { Text("انتخاب مؤدی") }
        } else {
            Text("مؤدی: ${taxpayerName.orEmpty()}")
            Text("کلید خصوصی فقط به‌صورت رمزنگاری‌شده در این دستگاه نگهداری می‌شود. شناسه یکتا را سازمان امور مالیاتی صادر می‌کند.")
            if (!loaded) CircularProgressIndicator()
            if (loadError) Text("خواندن وضعیت کلید ممکن نشد؛ برای حفظ امنیت، تولید کلید غیرفعال است.", color = MaterialTheme.colorScheme.error)
            if (loaded && !loadError && encrypted == null) {
                Button(enabled = !busy && pendingPrivatePem.isBlank(), onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val pair = withContext(Dispatchers.Default) { TaxpayerKeyGenerator.generate() }
                            pendingPrivatePem = pair.privatePem
                            publicPem = pair.publicPem
                            confirmed = false
                            exported = false
                        } catch (_: Exception) { onNotice("تولید کلید انجام نشد.") }
                        finally { busy = false }
                    }
                }) { Text("تولید جفت کلید اختصاصی RSA") }
            } else if (encrypted != null) {
                Text("کلید خصوصی رمزنگاری‌شده برای این مؤدی ثبت شده است.")
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val bytes = withContext(Dispatchers.Default) { vault.decrypt(companyId, encrypted!!) }
                            try { publicPem = TaxpayerKeyGenerator.publicPemFromPrivatePkcs8(bytes) }
                            finally { bytes.fill(0) }
                        } catch (_: Exception) { onNotice("بازیابی کلید عمومی ممکن نشد.") }
                        finally { busy = false }
                    }
                }) { Text("بازیابی کلید عمومی این مؤدی") }
            }
            if (publicPem.isNotBlank()) {
                OutlinedButton(enabled = !busy, onClick = { export.launch("sura-taxpayer-${companyId}-public.pem") }) {
                    Text("دریافت فایل کلید عمومی")
                }
            }
            if (pendingPrivatePem.isNotBlank()) {
                Row {
                    Checkbox(checked = confirmed, enabled = exported && !busy, onCheckedChange = { confirmed = it })
                    Text("فایل عمومی را ذخیره کرده‌ام و می‌دانم حذف برنامه ممکن است کلید خصوصی را غیرقابل‌بازیابی کند.")
                }
                Button(enabled = !busy && exported && confirmed, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val envelope = withContext(Dispatchers.Default) { vault.encrypt(companyId, pendingPrivatePem) }
                            val prior = db.moadianDetailsDao().byCompany(companyId) ?: error("Taxpayer details missing")
                            check(prior.encryptedPrivateKey == null) { "Key already exists" }
                            db.moadianDetailsDao().upsert(prior.copy(encryptedPrivateKey = envelope, keyCheckStatus = "NEEDS_MATCH"))
                            encrypted = envelope
                            pendingPrivatePem = ""
                            onNotice("کلید خصوصی مؤدی با رمزنگاری ذخیره شد.")
                        } catch (_: Exception) { onNotice("ذخیره کلید انجام نشد؛ دوباره تلاش کنید.") }
                        finally { busy = false }
                    }
                }) { Text("ذخیره امن کلید خصوصی") }
            }
            Text("پس از دریافت فایل عمومی، آن را در کارپوشه مالیاتی ثبت و شناسه یکتای صادرشده را در بخش ویرایش مؤدی وارد کنید.")
            TextButton(onClick = {
                try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://my.tax.gov.ir"))) }
                catch (_: Exception) { onNotice("مرورگر باز نشد.") }
            }) { Text("ورود به کارپوشه رسمی مالیاتی") }
        }
    }
}
