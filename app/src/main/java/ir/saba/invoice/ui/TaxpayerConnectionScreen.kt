package ir.saba.invoice.ui

import android.provider.OpenableColumns
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
import ir.saba.invoice.security.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Registration belongs to the selected taxpayer inside the authenticated account. No official sending. */
@Composable
fun TaxpayerConnectionScreen(
    db: SabaDatabase, account: AccountScope, companyId: Long,
    onCreateKeys: () -> Unit, onEditTaxpayer: () -> Unit, onNotice: (String) -> Unit
) {
    key(account.ownerId, companyId) { TaxpayerConnectionContent(db,account,companyId,onCreateKeys,onEditTaxpayer,onNotice) }
}

@Composable
private fun TaxpayerConnectionContent(
    db: SabaDatabase, account: AccountScope, companyId: Long,
    onCreateKeys: () -> Unit, onEditTaxpayer: () -> Unit, onNotice: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(db,account) { TaxpayerCredentialRepository(context,db,account) }
    var name by remember { mutableStateOf("") }
    var memoryId by remember { mutableStateOf("") }
    var privatePem by remember { mutableStateOf("") }
    var pendingStored by remember { mutableStateOf(false) }
    var legacyCheckSucceeded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    DisposableEffect(companyId,account) { onDispose { privatePem = "" } }
    LaunchedEffect(companyId,account) {
        try {
            val company = db.companyDao().byId(companyId) ?: error("مودی یافت نشد.")
            require(company.purchaserId == account.ownerId) { "مودی متعلق به این حساب نیست." }
            name = company.name
            memoryId = db.moadianDetailsDao().byCompany(companyId)?.fiscalMemoryId.orEmpty()
            pendingStored = repository.hasPendingPrivateKey(companyId)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = SuraErrors.transport(e) ?: e.message?.takeIf{it in setOf("مودی یافت نشد.","مودی متعلق به این حساب نیست.")} ?: "اطلاعات اتصال قابل بارگذاری نیست. [TAX-CONNECTION]" }
        finally { busy = false }
    }
    fun import(uri: android.net.Uri?) {
        if (uri == null) return
        busy = true; error = ""
        scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    // Some Android file providers do not expose DISPLAY_NAME at all.
                    val filename = runCatching {
                        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                            if (it.moveToFirst()) it.getString(0) else ""
                        }
                    }.getOrNull().orEmpty()
                    val input = context.contentResolver.openInputStream(uri) ?: error("فایل انتخاب‌شده قابل خواندن نیست.")
                    input.use { TaxpayerKeyImport.read(it,filename) }
                }
                privatePem = imported
                onNotice("فایل کلید خصوصی انتخاب شد.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error = SuraErrors.transport(e) ?: "ساختار کلید خصوصی تأیید نشد؛ فایل RSA معتبر را انتخاب کنید. [KEY-IMPORT]"
                onNotice(error)
            }
            finally { busy = false }
        }
    }
    val importKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { import(it) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("اتصال مالیاتی مؤدی",style=MaterialTheme.typography.headlineSmall)
        Text("مودی: $name")
        Text("شناسه حافظه: ${memoryId.ifBlank { "ثبت نشده" }}")
        Text("کلید خصوصی فقط برای همین مؤدی و به‌صورت رمزگذاری‌شده روی دستگاه نگهداری می‌شود.")
        Text("کلید عمومی باید در کارپوشه ثبت شده باشد. سپس با کلید خصوصی و شناسه یکتا، اتصال را بررسی کنید.",style=MaterialTheme.typography.bodySmall)
        if (pendingStored) Text("کلید خصوصی این مؤدی به‌صورت رمزگذاری‌شده ذخیره شده است.", style=MaterialTheme.typography.bodySmall)
        if (legacyCheckSucceeded) Text(
            "احراز هویت، استعلام حافظه و دریافت کلید سازمان موفق بود. این نتیجه تأیید ارسال صورتحساب نیست.",
            color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall
        )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("تطبیق محلی کلید و گواهی انجام شده است.")
            Text("اثر انگشت گواهی: ${profile!!.certificateSha256.take(16)}…",style=MaterialTheme.typography.bodySmall)
        }
        Text("تطبیق محلی، تأیید سازمان امور مالیاتی نیست. این بررسی هیچ صورتحسابی ارسال نمی‌کند.",style=MaterialTheme.typography.bodySmall)
        if (error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        val memoryReady = memoryId.matches(Regex("[A-Z0-9]{6}"))
        OutlinedButton(enabled=!busy,onClick=onEditTaxpayer,modifier=Modifier.fillMaxWidth()) { Text("ویرایش مشخصات و شناسه حافظه") }
        OutlinedButton(enabled=!busy && privatePem.isBlank(),onClick=onCreateKeys,modifier=Modifier.fillMaxWidth()) { Text("ساخت کلید عمومی و خصوصی") }
        OutlinedButton(enabled=!busy,onClick={importKey.launch(arrayOf("*/*"))},modifier=Modifier.fillMaxWidth()) {
            Text(if(privatePem.isBlank()) "انتخاب کلید خصوصی همین مؤدی" else "کلید خصوصی انتخاب شد؛ تغییر فایل")
        }
        Button(enabled=!busy && privatePem.isNotBlank(),onClick={confirmation="pending"},modifier=Modifier.fillMaxWidth()) {
            Text("ذخیره امن کلید خصوصی برای این مؤدی")
        }
        if (!memoryReady) Text("ابتدا شناسه یکتا را در مشخصات مؤدی ثبت کنید.",style=MaterialTheme.typography.bodySmall)
        Button(
            enabled=!busy && memoryReady && pendingStored,
            onClick={confirmation="legacy"},
            modifier=Modifier.fillMaxWidth()
        ) { Text("بررسی اتصال مؤدی (بدون گواهی)") }
        OutlinedButton(enabled=!busy && pendingStored,onClick={confirmation="remove"},modifier=Modifier.fillMaxWidth()) {
            Text("حذف اطلاعات اتصال این مؤدی")
        }
    }
    if (confirmation.isNotBlank()) AlertDialog(
        onDismissRequest={if(!busy)confirmation=""},
        title={Text(when(confirmation){"pending"->"ذخیره امن کلید خصوصی";"legacy"->"بررسی اتصال مستقیم";else->"حذف اطلاعات اتصال"})},
        text={Text(when(confirmation) {
            "legacy" -> "احراز هویت، استعلام حافظه و دریافت کلید رمزگذاری سازمان بررسی می‌شود؛ هیچ صورتحسابی ارسال نمی‌شود."
            "pending" -> "کلید خصوصی برای همین مؤدی روی دستگاه رمزگذاری می‌شود."
            else -> "فقط اطلاعات اتصال همین مؤدی حذف می‌شود؛ سوابق فاکتورها باقی می‌مانند."
        })},
        confirmButton={TextButton(enabled=!busy,onClick={
            val action=confirmation; busy=true; error=""
            scope.launch {
                try {
                    if(action=="legacy") {
                        legacyCheckSucceeded=false
                        MoadianLegacyConnectionProbe(repository).check(companyId,memoryId)
                        LegacyMoadianInvoiceGateway(repository).checkReadiness(companyId,memoryId)
                        legacyCheckSucceeded=true
                        onNotice("احراز هویت و استعلام حافظه از مسیر بدون گواهی موفق شد؛ ارسال صورتحساب بررسی نشده است.")
                    } else {
                        when(action) {
                            "pending" -> {
                                repository.stagePrivateKey(companyId,privatePem)
                                pendingStored=true
                                legacyCheckSucceeded=false
                                privatePem=""
                                onNotice("کلید خصوصی این مؤدی به‌صورت امن ذخیره شد؛ هنوز اتصال رسمی فعال نیست.")
                            }
                            else -> {
                                repository.remove(companyId)
                                privatePem="";pendingStored=false;legacyCheckSucceeded=false
                                onNotice("اطلاعات اتصال این مؤدی حذف شد.")
                            }
                        }
                    }
                    confirmation=""
                } catch(e:CancellationException){throw e}
                catch(e:Exception){error=SuraErrors.transport(e) ?: when(action){
                    "legacy" -> if (e is LegacyPreflightFailure) e.message.orEmpty() else "اتصال مستقیم تأیید نشد؛ کلید خصوصی، شناسه یکتا و ثبت کلید عمومی در کارپوشه را بررسی کنید."
                    else -> e.message?:"تغییر اطلاعات اتصال انجام نشد."
                };confirmation=""}
                finally{busy=false}
            }
        }){Text("تأیید")}},
        dismissButton={TextButton(enabled=!busy,onClick={confirmation=""}){Text("انصراف")}}
    )
}
