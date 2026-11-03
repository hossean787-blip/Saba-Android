package ir.saba.invoice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.*
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LegacyRecoveryScreen(db: SabaDatabase, account: AccountScope, onChanged: suspend () -> Unit) {
    key(account.ownerId) { LegacyRecoveryContent(db,account,onChanged) }
}
@Composable
private fun LegacyRecoveryContent(db: SabaDatabase, account: AccountScope, onChanged: suspend () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var companies by remember { mutableStateOf<List<Company>>(emptyList()) }
    var importer by remember { mutableStateOf<LegacyAccountImporter?>(null) }
    var selected by remember { mutableStateOf<Company?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    LaunchedEffect(account) {
        try {
            if (context.getDatabasePath("saba_local.db").exists()) {
                val service = LegacyAccountImporter(SabaDatabase.getInstance(context),db,account)
                companies = service.eligible().filter { db.legacyImportReceiptDao().byCompany(it.id) == null }
                importer = service
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { message="سوابق قدیمی قابل خواندن نیست؛ فایل اصلی حفظ شده است." }
        finally { busy=false }
    }
    val companyRows = companies
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("بازیابی سوابق قدیمی",style=MaterialTheme.typography.titleLarge)
            Text("فقط سوابق با شناسه مالک معتبر و مطابق همین حساب نمایش داده می‌شوند. سوابق بدون مالک معتبر و متعلق به دیگران منتقل نمی‌شوند. فایل اصلی حذف نمی‌شود؛ کلید امضا باید دوباره ثبت شود و موجودی اعتبار از سرور دریافت می‌شود.")
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(message.isNotBlank()) Text(message)
            if(!busy && companies.isEmpty()) Text("سابقهٔ منتقل‌نشده با مالکیت معتبر این حساب پیدا نشد. سوابق فاقد مالک معتبر نیازمند بررسی پشتیبانی هستند.")
        }
        items(companyRows,key={it.id}) { company ->
            OutlinedButton(enabled=!busy,onClick={selected=company}) { Text("بازیابی ${company.name}") }
        }
    }
    selected?.let { company -> AlertDialog(onDismissRequest={selected=null},title={Text("بازیابی سوابق ${company.name}")},
        text={Text("مشتریان، فاکتورها، ردیف‌ها و سابقهٔ مصرف بدون مصرف اعتبار تازه منتقل می‌شوند. وجود تداخل، کل انتقال را متوقف می‌کند؛ اطلاعات موجود بازنویسی نمی‌شوند.")},
        confirmButton={TextButton(onClick={
            selected=null;busy=true
            scope.launch {
                try {
                    checkNotNull(importer).importCompany(company.id)
                    companies=companies.filterNot { it.id==company.id };onChanged();message="بازیابی کامل شد؛ فایل اصلی محفوظ است."
                } catch(e:CancellationException){throw e}
                catch(_:Exception){message="انتقال انجام نشد؛ مالکیت یا تداخل شناسه‌ها نیازمند بررسی است. هیچ سابقه‌ای بازنویسی نشد."}
                finally{busy=false}
            }
        }){Text("تأیید بازیابی")}},dismissButton={TextButton(onClick={selected=null}){Text("انصراف")}}) }
}
