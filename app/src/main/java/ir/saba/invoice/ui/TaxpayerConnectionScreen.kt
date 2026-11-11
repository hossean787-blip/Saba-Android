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
    var certificate by remember { mutableStateOf<ByteArray?>(null) }
    var profile by remember { mutableStateOf<TaxpayerCredentialProfile?>(null) }
    var pendingStored by remember { mutableStateOf(false) }
    var legacyCheckSucceeded by remember { mutableStateOf(false) }
    var showCertificateOptions by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    DisposableEffect(companyId,account) { onDispose { privatePem = ""; certificate = null } }
    LaunchedEffect(companyId,account) {
        try {
            val company = db.companyDao().byId(companyId) ?: error("مودی یافت نشد.")
            require(company.purchaserId == account.ownerId) { "مودی متعلق به این حساب نیست." }
            name = company.name
            memoryId = db.moadianDetailsDao().byCompany(companyId)?.fiscalMemoryId.orEmpty()
            pendingStored = repository.hasPendingPrivateKey(companyId)
            if (memoryId.matches(Regex("[A-Z0-9]{6}"))) profile = repository.profile(companyId,System.currentTimeMillis())
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "اطلاعات اتصال قابل بارگذاری نیست." }
        finally { busy = false }
    }
    fun import(uri: android.net.Uri?, isCertificate: Boolean) {
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
                    input.use {
                        if (isCertificate) {
                            val bytes = TaxpayerSigningCertificate.read(it,filename)
                            TaxpayerSigningCertificate.parse(bytes)
                            bytes to null
                        } else null to TaxpayerKeyImport.read(it,filename)
                    }
                }
                if (isCertificate) {
                    certificate = requireNotNull(imported.first)
                    onNotice("فایل گواهی انتخاب و بررسی شد؛ اکنون «ثبت امن کلید و گواهی» را بزنید.")
                } else {
                    privatePem = requireNotNull(imported.second)
                    onNotice("فایل کلید خصوصی انتخاب شد.")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error = e.message ?: "ورود فایل انجام نشد."
                onNotice(error)
            }
            finally { busy = false }
        }
    }
    val importKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { import(it,false) }
    val importCertificate = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { import(it,true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("اتصال مالیاتی مؤدی",style=MaterialTheme.typography.headlineSmall)
        Text("مودی: $name")
        Text("شناسه حافظه: ${memoryId.ifBlank { "ثبت نشده" }}")
        Text("کلید خصوصی فقط برای همین مؤدی و به‌صورت رمزگذاری‌شده روی دستگاه نگهداری می‌شود.")
        Text("کلید عمومی باید در کارپوشه ثبت شده باشد. سپس با کلید خصوصی و شناسه یکتا، اتصال را بررسی کنید.",style=MaterialTheme.typography.bodySmall)
        if (pendingStored) Text("کلید خصوصی این مؤدی به‌صورت رمزگذاری‌شده ذخیره شده است.", style=MaterialTheme.typography.bodySmall)
        if (legacyCheckSucceeded) Text(
            "احراز هویت و استعلام حافظه از مسیر بدون گواهی موفق بود. این نتیجه تأیید ارسال صورتحساب در API نسخه ۲ نیست.",
            color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall
        )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (profile != null) {
            Text("تطبیق محلی کلید و گواهی انجام شده است.")
            Text("اثر انگشت گواهی: ${profile!!.certificateSha256.take(16)}…",style=MaterialTheme.typography.bodySmall)
        }
        Text("تطبیق محلی، تأیید سازمان امور مالیاتی نیست. این بررسی هیچ صورتحسابی ارسال نمی‌کند.",style=MaterialTheme.typography.bodySmall)
        if (error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        val memoryReady = memoryId.matches(Regex("[A-Z0-9]{6}"))
        OutlinedButton(enabled=!busy,onClick=onEditTaxpayer,modifier=Modifier.fillMaxWidth()) { Text("ویرایش مشخصات و شناسه حافظه") }
        OutlinedButton(enabled=!busy && privatePem.isBlank() && certificate == null,onClick=onCreateKeys,modifier=Modifier.fillMaxWidth()) { Text("ساخت کلید و درخواست گواهی CSR") }
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
        TextButton(enabled=!busy,onClick={showCertificateOptions=!showCertificateOptions},modifier=Modifier.fillMaxWidth()) {
            Text(if(showCertificateOptions) "بستن تنظیمات گواهی نسخه ۲" else "تنظیمات پیشرفته اتصال با گواهی")
        }
        if(showCertificateOptions) {
            OutlinedButton(enabled=!busy,onClick={importCertificate.launch(arrayOf("*/*"))},modifier=Modifier.fillMaxWidth()) {
                Text(if(certificate == null) "انتخاب گواهی CER / CRT / PEM" else "گواهی انتخاب شد؛ تغییر فایل")
            }
            Button(enabled=!busy && memoryReady && (privatePem.isNotBlank() || pendingStored) && certificate != null,onClick={confirmation="save"},modifier=Modifier.fillMaxWidth()) {
                Text("ثبت امن کلید و گواهی برای این مؤدی")
            }
            OutlinedButton(enabled=!busy && profile != null && privatePem.isBlank() && certificate == null,
                onClick={confirmation="probe"},modifier=Modifier.fillMaxWidth()) { Text("بررسی اتصال با گواهی نسخه ۲") }
            OutlinedButton(enabled=!busy,onClick={confirmation="remove"},modifier=Modifier.fillMaxWidth()) {
                Text("حذف اطلاعات اتصال این مؤدی")
            }
        }
    }
    if (confirmation.isNotBlank()) AlertDialog(
        onDismissRequest={if(!busy)confirmation=""},
        title={Text(when(confirmation){"save"->"ثبت اطلاعات اتصال";"pending"->"ذخیره رمزگذاری‌شده کلید خصوصی";"probe"->"بررسی اتصال رسمی";"legacy"->"بررسی اتصال بدون گواهی";else->"حذف اطلاعات اتصال"})},
        text={Text(if(confirmation=="probe") "شناسه حافظه و گواهی عمومی همین مؤدی برای احراز هویت به سرور رسمی ارسال می‌شود. هیچ صورتحساب یا درخواست مصرف اعتبار ارسال نمی‌شود."
            else if(confirmation=="legacy") "سورا با کلید خصوصی ذخیره‌شده و شناسه یکتای همین مؤدی فقط درخواست احراز هویت و استعلام اطلاعات حافظه به مسیر قدیمی بدون گواهی می‌فرستد. هیچ صورتحسابی ارسال نمی‌شود و موفقیت آن تضمین سازگاری با نسخه ۲ نیست."
            else if(confirmation=="pending") "کلید خصوصی برای همین مؤدی روی دستگاه رمزگذاری می‌شود. بررسی اتصال بدون گواهی به شناسه یکتای معتبر نیاز دارد؛ ارسال نسخه ۲ نیز نیازمند مدارک مربوط به آن است."
            else "مودی: $name\nحافظه: $memoryId\nفقط اطلاعات اتصال همین مؤدی در حساب فعلی تغییر می‌کند؛ سوابق فاکتورها باقی می‌مانند.")},
        confirmButton={TextButton(enabled=!busy,onClick={
            val action=confirmation; busy=true; error=""
            scope.launch {
                try {
                    if(action=="probe") {
                        MoadianConnectionProbe(repository,OfficialMoadianPublicTransport()).check(companyId)
                        onNotice("اطلاعات سرور رسمی دریافت شد؛ این نتیجه به معنی ارسال یا پذیرش صورتحساب نیست.")
                    } else if(action=="legacy") {
                        legacyCheckSucceeded=false
                        MoadianLegacyConnectionProbe(repository).check(companyId,memoryId)
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
                            "save" -> {
                                profile = if(privatePem.isNotBlank())
                                    repository.save(companyId,privatePem,requireNotNull(certificate),System.currentTimeMillis())
                                else repository.saveWithPendingPrivateKey(companyId,requireNotNull(certificate),System.currentTimeMillis())
                                privatePem="";certificate=null;pendingStored=false
                                onNotice("کلید و گواهی همین مؤدی تطبیق و به‌صورت امن ثبت شد.")
                            }
                            else -> {
                                repository.remove(companyId)
                                privatePem="";certificate=null;profile=null;pendingStored=false;legacyCheckSucceeded=false
                                onNotice("اطلاعات اتصال این مؤدی حذف شد.")
                            }
                        }
                    }
                    confirmation=""
                } catch(e:CancellationException){throw e}
                catch(e:Exception){error=when(action){
                    "probe" -> "بررسی مسیر نسخه ۲ ناموفق بود؛ گواهی و اتصال اینترنت را بررسی کنید."
                    "legacy" -> "بررسی اتصال بدون گواهی ناموفق بود؛ کلید خصوصی، شناسه یکتا، ثبت کلید عمومی در کارپوشه و دسترسی این API را بررسی کنید."
                    else -> e.message?:"تغییر اطلاعات اتصال انجام نشد."
                };confirmation=""}
                finally{busy=false}
            }
        }){Text("تأیید")}},
        dismissButton={TextButton(enabled=!busy,onClick={confirmation=""}){Text("انصراف")}}
    )
}
