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
                withContext(Dispatchers.IO) {
                    val filename = context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                        if (it.moveToFirst()) it.getString(0) else ""
                    }.orEmpty()
                    val input = context.contentResolver.openInputStream(uri) ?: error("فایل باز نشد.")
                    input.use {
                        if (isCertificate) {
                            val bytes = TaxpayerSigningCertificate.read(it,filename)
                            TaxpayerSigningCertificate.parse(bytes)
                            certificate = bytes
                        } else privatePem = TaxpayerKeyImport.read(it,filename)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "ورود فایل انجام نشد." }
            finally { busy = false }
        }
    }
    val importKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { import(it,false) }
    val importCertificate = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { import(it,true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("اتصال مالیاتی مؤدی",style=MaterialTheme.typography.headlineSmall)
        Text("مودی: $name")
        Text("شناسه حافظه: ${memoryId.ifBlank { "ثبت نشده" }}")
        Text("کلید و گواهی فقط برای همین مؤدی در حساب فعلی ذخیره می‌شوند.")
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
        OutlinedButton(enabled=!busy && memoryReady,onClick={importKey.launch(arrayOf("*/*"))},modifier=Modifier.fillMaxWidth()) {
            Text(if(privatePem.isBlank()) "انتخاب کلید خصوصی همین مؤدی" else "کلید خصوصی انتخاب شد؛ تغییر فایل")
        }
        OutlinedButton(enabled=!busy && memoryReady,onClick={importCertificate.launch(arrayOf("*/*"))},modifier=Modifier.fillMaxWidth()) {
            Text(if(certificate == null) "انتخاب گواهی CER / CRT / PEM" else "گواهی انتخاب شد؛ تغییر فایل")
        }
        Button(enabled=!busy && memoryReady && privatePem.isNotBlank() && certificate != null,onClick={confirmation="save"},modifier=Modifier.fillMaxWidth()) {
            Text("ثبت امن کلید و گواهی برای این مؤدی")
        }
        OutlinedButton(enabled=!busy && profile != null && privatePem.isBlank() && certificate == null,
            onClick={confirmation="probe"},modifier=Modifier.fillMaxWidth()) { Text("بررسی اتصال رسمی بدون ارسال صورتحساب") }
        OutlinedButton(enabled=!busy,onClick={confirmation="remove"},modifier=Modifier.fillMaxWidth()) { Text("حذف اطلاعات اتصال این مؤدی") }
    }
    if (confirmation.isNotBlank()) AlertDialog(
        onDismissRequest={if(!busy)confirmation=""},
        title={Text(when(confirmation){"save"->"ثبت اطلاعات اتصال";"probe"->"بررسی اتصال رسمی";else->"حذف اطلاعات اتصال"})},
        text={Text(if(confirmation=="probe") "شناسه حافظه و گواهی عمومی همین مؤدی برای احراز هویت به سرور رسمی ارسال می‌شود. هیچ صورتحساب یا درخواست مصرف اعتبار ارسال نمی‌شود."
            else "مودی: $name\nحافظه: $memoryId\nفقط اطلاعات اتصال همین مؤدی در حساب فعلی تغییر می‌کند؛ سوابق فاکتورها باقی می‌مانند.")},
        confirmButton={TextButton(enabled=!busy,onClick={
            val action=confirmation; busy=true; error=""
            scope.launch {
                try {
                    if(action=="probe") {
                        MoadianConnectionProbe(repository,OfficialMoadianPublicTransport()).check(companyId)
                        onNotice("اطلاعات سرور رسمی دریافت شد؛ این نتیجه به معنی ارسال یا پذیرش صورتحساب نیست.")
                    } else {
                        if(action=="save") profile=repository.save(companyId,privatePem,requireNotNull(certificate),System.currentTimeMillis())
                        else { repository.remove(companyId);profile=null }
                        privatePem="";certificate=null;onNotice("اطلاعات اتصال همین مؤدی به‌روزرسانی شد.")
                    }
                    confirmation=""
                } catch(e:CancellationException){throw e}
                catch(e:Exception){error=if(action=="probe") "بررسی اتصال ناموفق بود؛ گواهی، شناسه حافظه و اتصال اینترنت را بررسی کنید." else e.message?:"تغییر اطلاعات اتصال انجام نشد.";confirmation=""}
                finally{busy=false}
            }
        }){Text("تأیید")}},
        dismissButton={TextButton(enabled=!busy,onClick={confirmation=""}){Text("انصراف")}}
    )
}
