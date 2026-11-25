package ir.saba.invoice.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.saba.invoice.security.TaxpayerKeyGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Only in-memory workshop state. Never put private PEM in SavedStateHandle or preferences. */
class TaxKeyWorkshopState : ViewModel() {
    var keys by mutableStateOf<TaxpayerKeyGenerator.Generated?>(null)
        internal set
    var privateSaved by mutableStateOf(false)
        internal set
    var publicSaved by mutableStateOf(false)
        internal set
    var confirmed by mutableStateOf(false)
        internal set
    fun clear() { keys=null; privateSaved=false; publicSaved=false; confirmed=false }
    override fun onCleared() { clear(); super.onCleared() }
}
private fun Context.workshopActivity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> if(baseContext===this) null else baseContext.workshopActivity()
    else -> null
}

/** Standalone key workshop. Does not modify the taxpayer signing vault or upload keys. */
@Composable
fun TaxpayerKeysScreen(onNotice: (String) -> Unit, ownerId:String = "standalone") {
    val context = LocalContext.current
    val activity = context.workshopActivity()
    val scope = rememberCoroutineScope()
    val state:TaxKeyWorkshopState = viewModel(key="tax-key-workshop:$ownerId")
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    // Rotation and document picker may recreate the activity. Keep the same in-memory pair;
    // normal navigation/logout clears it. Process death requires generating a fresh pair.
    DisposableEffect(state,activity) {
        onDispose { if(activity?.isChangingConfigurations != true) state.clear() }
    }
    fun savePem(uri:android.net.Uri?,privateKey:Boolean) {
        if(uri==null) { status="ذخیره فایل لغو شد؛ کلیدهای فعلی محفوظ‌اند."; return }
        val pair=state.keys
        if(pair==null) { error="کلید موقت در دسترس نیست؛ دوباره کلید بسازید. [KEY-SESSION]"; return }
        if(privateKey && !state.confirmed) { error="ابتدا نگهداری امن کلید خصوصی را تأیید کنید."; return }
        busy=true; error=""
        scope.launch {
            try {
                val bytes=(if(privateKey) pair.privatePem else pair.publicPem).toByteArray(Charsets.US_ASCII)
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri,"wt")?.use { it.write(bytes);it.flush() }
                            ?: error("File unavailable")
                    }
                } finally { bytes.fill(0) }
                if(state.keys!==pair) return@launch
                if(privateKey) state.privateSaved=true else state.publicSaved=true
                status=if(privateKey) "کلید خصوصی ذخیره شد؛ فایل را محرمانه نگهداری کنید." else "کلید عمومی ذخیره شد."
                onNotice(status)
            } catch(e:CancellationException) { throw e }
            catch(_:Exception) {
                error=if(privateKey) "ذخیره کلید خصوصی ناموفق بود. محل قابل نوشتن دیگری انتخاب کنید. [KEY-PRIVATE]"
                else "ذخیره کلید عمومی ناموفق بود. محل قابل نوشتن دیگری انتخاب کنید. [KEY-PUBLIC]"
            } finally { busy=false }
        }
    }
    // Generic binary MIME avoids providers rejecting the uncommon PEM MIME; extension stays .pem.
    val exportPublic=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { savePem(it,false) }
    val exportPrivate=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { savePem(it,true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("ساخت کلید مالیاتی",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("کلید عمومی و خصوصی RSA با طول ۲۰۴۸ بیت، روی همین دستگاه تولید می‌شوند.",
            style=MaterialTheme.typography.bodyMedium)
        if(busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("در حال انجام عملیات…",style=MaterialTheme.typography.bodySmall)
        }
        if(error.isNotBlank()) Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)) {
            Text(error,Modifier.fillMaxWidth().padding(12.dp),color=MaterialTheme.colorScheme.onErrorContainer)
        }
        if(status.isNotBlank()) Text(status,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)
        Button(enabled=!busy && state.keys==null,modifier=Modifier.fillMaxWidth().testTag("generateTaxpayerKeys"),
            onClick={
                busy=true;error="";status=""
                scope.launch {
                    try {
                        state.keys=withContext(Dispatchers.Default){ TaxpayerKeyGenerator.generate() }
                        state.privateSaved=false;state.publicSaved=false;state.confirmed=false
                        status="جفت کلید ساخته شد و صحت امضای آن بررسی شد."
                        onNotice(status)
                    } catch(e:CancellationException){throw e}
                    catch(_:Exception){error="ساخت یا بررسی جفت کلید RSA انجام نشد. دوباره تلاش کنید. [KEY-GEN-RSA]"}
                    finally{busy=false}
                }
            }) { Text("تولید کلید عمومی و خصوصی") }
        if(state.keys!=null) {
            Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("کلیدها آماده دریافت هستند",fontWeight=FontWeight.Bold)
                    OutlinedButton(enabled=!busy,onClick={
                        error="";try { exportPublic.launch("sura-public-key.pem") }
                        catch(_:Exception){error="فایل‌گیر دستگاه باز نشد. [KEY-PICKER]"}
                    },modifier=Modifier.fillMaxWidth().testTag("exportTaxPublicKey")) {
                        Text(if(state.publicSaved) "دریافت مجدد کلید عمومی" else "دریافت فایل کلید عمومی")
                    }
                    Text("کلید خصوصی محرمانه است؛ آن را در کارپوشه یا پیام‌رسان بارگذاری نکنید.",
                        color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=state.confirmed,enabled=!busy,onCheckedChange={state.confirmed=it})
                        Text("مسئولیت نگهداری امن کلید خصوصی را می‌پذیرم.",modifier=Modifier.weight(1f))
                    }
                    OutlinedButton(enabled=state.confirmed && !busy,onClick={
                        error="";try{exportPrivate.launch("sura-private-key-SECRET.pem")}
                        catch(_:Exception){error="فایل‌گیر دستگاه باز نشد. [KEY-PICKER]"}
                    },modifier=Modifier.fillMaxWidth().testTag("exportTaxPrivateKey")) { Text("دریافت کلید خصوصی (محرمانه)") }
                    Text(if(state.privateSaved) "کلید خصوصی در محل انتخاب‌شده ذخیره شد." else "پیش از خروج، فایل خصوصی را در محل امن ذخیره کنید.",
                        style=MaterialTheme.typography.bodySmall)
                    Button(enabled=state.privateSaved && state.publicSaved && !busy,onClick={
                        state.clear();status="کلیدهای موقت صفحه پاک شدند.";error=""
                    },modifier=Modifier.fillMaxWidth()){Text("پایان و پاک‌سازی کلیدهای موقت")}
                }
            }
        }
        HorizontalDivider()
        Text("ثبت کلید عمومی و دریافت شناسه یکتا در کارپوشه رسمی انجام می‌شود. کلید خصوصی را فقط در سورا وارد کنید.",
            style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={
            try{context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://my.tax.gov.ir")))}
            catch(_:Exception){onNotice("امکان بازکردن کارپوشه وجود ندارد.")}
        }){Text("ورود به کارپوشه رسمی مالیاتی")}
    }
}
