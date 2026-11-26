package ir.saba.invoice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.*
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
internal fun InvoiceExcelImportScreen(db:SabaDatabase, account:AccountScope, companyId:Long,
    token:String, gateway:InvoiceCreditGateway, onBack:()->Unit, onChanged:suspend ()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var batch by remember { mutableStateOf<ExcelInvoiceBatch?>(null) }
    var rows by remember { mutableStateOf<List<ExcelInvoicePreview>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(InvoiceCreditKind.PAID) }
    val creation=remember(db,account,token,gateway) { InvoiceCreationCoordinator(db,account) { k,id,key ->
        if(k==InvoiceCreditKind.PAID) gateway.consumePaid(token,id,key)
        else gateway.consumeGift(token,key)
        Unit
    } }
    val service=remember(db,account,companyId,creation){InvoiceExcelService(db,account,companyId,creation)}
    fun runTask(block:suspend ()->Unit) {
        if(busy)return
        busy=true; error=""; result=""
        scope.launch {
            try { block() }
            catch(e:CancellationException){throw e}
            catch(e:Exception){error=e.message ?: "عملیات اکسل انجام نشد."}
            finally {busy=false}
        }
    }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if(uri!=null)runTask {
            withContext(Dispatchers.IO){
                val bytes=service.template()
                context.contentResolver.openOutputStream(uri)?.use{it.write(bytes)} ?: error("فایل ذخیره نشد.")
            }
            result="قالب سه‌کاربرگی ذخیره شد."
        }
    }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null)runTask {
            batch=null;rows=emptyList()
            val parsed=withContext(Dispatchers.IO){
                val bytes=context.contentResolver.openInputStream(uri)?.use{input ->
                    val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true){val n=input.read(buffer);if(n<0)break
                        require(out.size()+n<=5_000_000){"حجم فایل بیش از ۵ مگابایت است."};out.write(buffer,0,n)}
                    out.toByteArray()
                } ?: error("فایل قابل خواندن نیست.")
                service.parse(bytes)
            }
            rows=service.preview(parsed);batch=parsed
            result="فایل بررسی شد؛ هنوز اعتباری کسر نشده است."
        }
    }
    fun launchPicker(action:()->Unit){try{action()}catch(e:Exception){error="فایل‌گیر در دسترس نیست: "+e.message.orEmpty()}}
    androidx.activity.compose.BackHandler(enabled=busy || confirm){ if(confirm && !busy)confirm=false }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item {
            TextButton(enabled=!busy,onClick=onBack){Text("بازگشت به صورتحساب‌ها")}
            Text("ثبت گروهی با اکسل",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("فقط صورتحساب اصلیِ مؤدی فعال • سقف ۱۰۰ صورتحساب • مبالغ به ریال")
            Text("کاربرگ اول اقلام صورتحساب، دوم مؤدی و سوم اطلاعات مشتری جدید. شماره، تاریخ، مشتری و تسویه را در همه اقلام یک صورتحساب یکسان وارد کنید.")
            Text("برای مشتری ثبت‌شده، فقط شناسه او در ردیف صورتحساب کافی است. کد واحد رسمی را وارد کنید.")
            OutlinedButton(enabled=!busy,onClick={launchPicker{save.launch("SURA_Invoice.xlsx")}},modifier=Modifier.fillMaxWidth().testTag("invoiceExcelTemplate")){Text("دریافت قالب سه‌کاربرگی")}
            Button(enabled=!busy,onClick={launchPicker{open.launch(arrayOf("*/*"))}},modifier=Modifier.fillMaxWidth().testTag("invoiceExcelOpen")){Text("بارگذاری و بررسی اکسل")}
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            if(result.isNotBlank())Text(result)
        }
        if(batch!=null){
            item{
                val count=rows.count{!it.complete}
                Text("${rows.size} صورتحساب • $count برای ثبت • ${rows.filter{it.newBuyer}.map{it.invoice.buyer}.distinct().size} مشتری جدید",fontWeight=FontWeight.Bold)
                Text("اعتبار مصرفی: یک واحد به ازای هر صورتحساب جدید؛ درخواست محفوظ با همان اعتبار قبلی ادامه می‌یابد.")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    FilterChip(selected=kind==InvoiceCreditKind.PAID,onClick={kind=InvoiceCreditKind.PAID},label={Text("اعتبار مؤدی")},enabled=!busy)
                    FilterChip(selected=kind==InvoiceCreditKind.GIFT,onClick={kind=InvoiceCreditKind.GIFT},label={Text("اعتبار هدیه")},enabled=!busy)
                }
                Button(enabled=!busy && count>0,onClick={confirm=true},modifier=Modifier.fillMaxWidth().testTag("invoiceExcelConfirm")){Text("تأیید ثبت پیش‌نویس‌ها")}
                Text("ثبت نسیه یا ترکیبی ممکن است؛ ارسال رسمی آن‌ها طبق محدودیت فعلی سورا انجام نمی‌شود.")
            }
            items(rows,key={it.invoice.number}){ p ->
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
                        Text(p.invoice.number+" • "+p.buyerName,fontWeight=FontWeight.Bold)
                        Text(p.invoice.date+" • "+p.invoice.lines.size+" قلم • "+p.invoice.total+" ریال")
                        Text(when{p.complete->"قبلاً ثبت شده";p.pending->"درخواست محفوظ؛ قابل ادامه";p.newBuyer->"مشتری جدید؛ پس از تأیید ثبت می‌شود";else->"آماده ثبت"})
                    }
                }
            }
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("تأیید ثبت گروهی")},
        text={Text("ثبت پیش‌نویس و مشتریان جدید با مصرف اعتبار انجام می‌شود. هیچ صورتحسابی به سامانه مؤدیان ارسال نمی‌شود. در توقف میان کار، موارد ثبت‌شده محفوظ‌اند و فایل بدون تغییر قابل ورود مجدد است.")},
        confirmButton={Button(onClick={
            confirm=false
            val selected=batch ?: return@Button
            val selectedKind=kind
            runTask {
                val current=service.preview(selected)
                val needed=current.count{!it.complete && !it.pending}
                if(needed>0){
                    val balance=if(selectedKind==InvoiceCreditKind.PAID)gateway.paidBalance(token,companyId) else gateway.giftBalance(token).toLong()
                    require(balance>=needed.toLong()){ "اعتبار کافی نیست؛ $needed اعتبار جدید لازم است." }
                }
                try{
                    val n=service.register(selected,selectedKind){done,total->result="$done از $total صورتحساب ثبت شد."}
                    result="$n پیش‌نویس جدید ثبت شد؛ ارسال مالیاتی انجام نشد."
                }finally{
                    rows=runCatching{service.preview(selected)}.getOrDefault(rows)
                    onChanged()
                }
            }
        }){Text("ثبت و مصرف اعتبار")}},
        dismissButton={TextButton(onClick={confirm=false}){Text("انصراف")}})
}
