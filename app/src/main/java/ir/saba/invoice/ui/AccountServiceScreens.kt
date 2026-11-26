package ir.saba.invoice.ui

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.*
import ir.saba.invoice.security.AccountScope
import ir.saba.invoice.security.AccountSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ink=SuraPalette.Navy
private val muted=SuraPalette.Muted
private val canvas=SuraPalette.Canvas

@Composable private fun ServiceHeader(title:String,subtitle:String) {
    Column(Modifier.fillMaxWidth().background(
        Brush.linearGradient(listOf(SuraPalette.Midnight,SuraPalette.Teal)),
        RoundedCornerShape(22.dp)).padding(22.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text(subtitle,color=SuraPalette.Soft,style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable private fun ServicePanel(title:String,body:String) {
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,color=ink,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Text(body,color=muted,style=MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun AccountBackupScreen(db:SabaDatabase,account:AccountScope,accessToken:String,
    restore:Boolean,onBusy:(Boolean)->Unit,onChanged:suspend()->Unit,onNotice:(String)->Unit,
    onLegacyRecovery:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val checkpoint=remember(account) { ArchiveRestoreCheckpoint(context.applicationContext,account) }
    val archive=remember(db,account,accessToken,checkpoint) {
        AccountArchive(db,account,{AccountSession.requireOwner(accessToken,account)},
            { TaxpayerIdAllocator(context.applicationContext).use { allocator -> allocator.advancePast(it) } },
            AccountArchivePortable(context.applicationContext,account),checkpoint)
    }
    var password by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var restoredCredit by remember { mutableStateOf<ArchiveCreditSnapshot?>(null) }
    var creditNames by remember { mutableStateOf<Map<Long,String>>(emptyMap()) }
    var running by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var pending by remember(account) { mutableStateOf(false) }
    var discardPending by remember(account) { mutableStateOf(false) }
    LaunchedEffect(checkpoint) { pending=withContext(Dispatchers.IO) { checkpoint.exists() } }
    fun execute(uri:Uri?,importing:Boolean) {
        if(running || password.length !in 12..128 || (!importing && password!=repeatPassword)) return
        val secret=password.toCharArray()
        password="";repeatPassword="";confirmed=false
        running=true;onBusy(true);status="";restoredCredit=null
        scope.launch {
            try {
                val outcome=withContext(Dispatchers.IO) {
                    if(importing) {
                        val bytes=if(uri==null) checkpoint.read() else requireNotNull(context.contentResolver.openInputStream(uri)).use { AccountArchiveCrypto.read(it) }
                        val result=archive.restoreWithCredit(bytes,secret) { ids -> AccountArchiveCreditClient.read(account,accessToken,ids) }
                        val c=result.added
                        "بازیابی انجام شد: ${c.taxpayers} مؤدی، ${c.customers} مشتری و ${c.invoices} سابقه جدید. اطلاعات فعلی حفظ شد." to result.credit
                    } else {
                        val ids=db.companyDao().all().map { it.id }.toSet()
                        val credit=AccountArchiveCreditClient.read(account,accessToken,ids)
                        val bytes=archive.export(secret,credit)
                        requireNotNull(context.contentResolver.openOutputStream(requireNotNull(uri),"w")).use { it.write(bytes);it.flush() }
                        // Reopen the actual selected file, not just the in-memory archive.
                        val saved=requireNotNull(context.contentResolver.openInputStream(requireNotNull(uri))).use { AccountArchiveCrypto.read(it) }
                        check(java.security.MessageDigest.isEqual(bytes,saved)) { "فایل ذخیره‌شده با نسخه تولیدشده یکسان نیست." }
                        "فایل پشتیبان رمزگذاری‌شده ذخیره و صحت فایل بررسی شد." to credit
                    }
                }
                status=outcome.first
                restoredCredit=outcome.second
                creditNames=db.companyDao().all().associate { it.id to it.name }
                if(importing) onChanged()
                onNotice(outcome.first)
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                status=if(e is IllegalArgumentException) e.message.orEmpty().take(220)
                    else if(importing) "بازیابی انجام نشد؛ رمز، سلامت و سازگاری فایل را بررسی کنید. اطلاعات فعلی بازنویسی نشد؛ اگر مرحله فایل‌ها یا کلیدها ناقص مانده، همان فایل را دوباره وارد کنید."
                    else "ساخت فایل پشتیبان ناموفق بود؛ فضای ذخیره‌سازی و مجوز فایل را بررسی کنید."
            } finally { secret.fill(' ');pending=checkpoint.exists();running=false;onBusy(false) }
        }
    }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if(uri!=null) execute(uri,false)
    }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> picked=uri }
    Column(Modifier.fillMaxSize().background(canvas).verticalScroll(rememberScrollState())
        .padding(16.dp).testTag(if(restore) "restoreScreen" else "backupScreen"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        ServiceHeader(if(restore) "بازیابی اطلاعات" else "پشتیبان‌گیری",
            if(restore) "بازیابی امن سوابق همان حساب" else "نسخه رمزگذاری‌شده اطلاعات حساب شما")
        ServicePanel("محتوای فایل","تمام مؤدیان، مشتریان، فاکتورها و اقلام، سوابق مالی و ارسال، کلیدهای مالیاتی و گواهی، لوگوها، پیوست‌ها، شناسه‌های اختصاصی و مشخصات مالک. اعتبار ذخیره‌شده صرفاً گزارش است؛ رمز ورود، نشست و اثر انگشت منتقل نمی‌شوند.")
        if(restore) ServicePanel("شرایط بازیابی امن","فقط حساب اصلی تهیه‌کننده پشتیبان مجاز به بازیابی است؛ داشتن رمز فایل برای حساب دیگر کافی نیست. نسخه پایگاه داده باید سازگار باشد؛ اطلاعات جدیدتر حذف یا بازنویسی نمی‌شود. صورتحساب‌های مفقود به صورت سوابق فقط‌خواندنی و مؤدیان جدیدِ بازیابی‌شده غیرفعال هستند تا سریال و وضعیت مالیاتی تطبیق داده شوند. مانده اعتبار فقط از سرور استعلام می‌شود؛ بازیابی در صورت اختلال استعلام متوقف می‌شود.")
        else ServicePanel("حفاظت از نسخه پشتیبان","رمز مستقل حداقل ۱۲ نویسه انتخاب کنید. بدون رمز، بازیابی ممکن نیست. فایل را در محل امن نگه دارید. پشتیبان خودکار ابری نیست؛ سقف داده ۳۲ مگابایت و مجموع پیوست‌ها ۲۰ مگابایت است؛ فایل ناقص ذخیره نمی‌شود.")
        OutlinedTextField(value=password,onValueChange={password=it.take(128)},label={Text("رمز فایل پشتیبان")},singleLine=true,
            visualTransformation=PasswordVisualTransformation(),enabled=!running,modifier=Modifier.fillMaxWidth().testTag("archivePassword"))
        if(!restore) OutlinedTextField(value=repeatPassword,onValueChange={repeatPassword=it.take(128)},label={Text("تکرار رمز")},singleLine=true,
            visualTransformation=PasswordVisualTransformation(),enabled=!running,modifier=Modifier.fillMaxWidth())
        if(restore) {
            if(pending) {
                ServicePanel("بازیابی ناتمام","فایل رمزگذاری‌شده قبلی محفوظ است. با همان رمز و حساب، بازیابی را ادامه دهید. اطلاعات و اعتبار فعلی بازنویسی نمی‌شود.")
                Button(onClick={execute(null,true)},enabled=!running && confirmed && password.length>=12,
                    modifier=Modifier.fillMaxWidth().testTag("archiveResume")) { Text("ادامه بازیابی ناتمام") }
                TextButton(onClick={discardPending=true},enabled=!running) { Text("حذف فایل موقت بازیابی") }
            }
            if(discardPending) AlertDialog(
                onDismissRequest={discardPending=false},
                title={Text("حذف فایل موقت؟")},
                text={Text("فقط نسخه موقت رمزگذاری‌شده حذف می‌شود. داده‌های قبلاً بازیابی‌شده به حالت قبل برنمی‌گردند. فایل اصلی خود را نگه دارید.")},
                confirmButton={TextButton(onClick={
                    discardPending=false
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { checkpoint.complete() }
                            pending=checkpoint.exists()
                        } catch(e:CancellationException) { throw e }
                        catch(_:Exception) { status="حذف فایل موقت ناموفق بود." }
                    }
                }) { Text("حذف فایل موقت") }},
                dismissButton={TextButton(onClick={discardPending=false}) { Text("انصراف") }})
            OutlinedButton(onClick={open.launch(arrayOf("application/octet-stream","*/*"))},enabled=!running && !pending,modifier=Modifier.fillMaxWidth()) {
                Text(if(picked==null) "انتخاب فایل پشتیبان" else "فایل انتخاب شده؛ تغییر فایل")
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Checkbox(checked=confirmed,onCheckedChange={confirmed=it},enabled=!running)
                Text("شرایط بازیابی سوابق و عدم ارسال مجدد را مطالعه کردم.",modifier=Modifier.padding(top=12.dp))
            }
            Button(onClick={picked?.let { execute(it,true) }},enabled=!running && !pending && picked!=null && confirmed && password.length>=12,
                modifier=Modifier.fillMaxWidth().testTag("archiveRestore")) { Text("بازیابی اطلاعات") }
            TextButton(onClick=onLegacyRecovery,enabled=!running) { Text("بازیابی سوابق نسخه قدیمی سورا") }
            ArchiveHistoryBrowser(db,account,enabled=!running,refreshKey=status)
            ArchiveJournalBrowser(account,status,!running)
        } else Button(onClick={save.launch("SURA-backup-${System.currentTimeMillis()}.sura")},
            enabled=!running && password.length>=12 && password==repeatPassword,
            modifier=Modifier.fillMaxWidth().testTag("archiveExport")) { Text("ساخت و ذخیره فایل پشتیبان") }
        if(running) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(status.isNotBlank()) ServicePanel("نتیجه عملیات",status)
        restoredCredit?.let { credit ->
            ServicePanel(if(restore) "مانده واقعی استعلام‌شده" else "مانده هنگام تهیه پشتیبان",
                "کیف پول مالک: ${credit.owner} اعتبار\nاعتبار هدیه: ${credit.gift}\n"+
                credit.taxpayers.entries.sortedBy { it.key }.joinToString("\n") { (id,value) ->
                    "${creditNames[id] ?: "مودی شماره $id"}: $value اعتبار"
                }+"\nزمان استعلام: "+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(credit.checkedAt)))
        }
    }
}

@Composable
internal fun SuraServiceScreen(kind:String,onBack:()->Unit,onSupport:(()->Unit)?=null) {
    val context=LocalContext.current
    val version=remember { runCatching { context.packageManager.getPackageInfo(context.packageName,0).versionName }.getOrNull().orEmpty() }
    var category by remember { mutableStateOf("ارسال صورتحساب") }
    var message by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    val title=when(kind) {
        "support" -> "پشتیبانی";"contact" -> "ارتباط با ما";"privacy" -> "حریم خصوصی"
        "terms" -> "قوانین و شرایط استفاده";else -> "درباره ما"
    }
    Column(Modifier.fillMaxSize().background(canvas).verticalScroll(rememberScrollState()).padding(16.dp)
        .testTag("service-"+kind),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        ServiceHeader(title,"سورا | صبا گستر اراک")
        when(kind) {
            "support" -> {
                ServicePanel("راهنمای رفع مشکل","در خطای ارسال، همان شماره درخواست را استعلام کنید. برای اعتبار، ابتدا وضعیت پرداخت را بررسی کنید. کلید خصوصی، رمز و اطلاعات محرمانه مشتری را ارسال نکنید.")
                ServicePanel("ارسال گزارش","کانال رسمی دریافت تیکت هنوز معرفی نشده است. گزارش را آماده و کپی یا از طریق برنامه دلخواه به مخاطب تأییدشده خود ارسال کنید؛ این کار تیکت سروری ایجاد نمی‌کند.")
                Text("موضوع گزارش",fontWeight=FontWeight.Bold)
                listOf("ارسال صورتحساب","ورود و حساب کاربری","اعتبار و پرداخت","مشکل فنی").forEach { item ->
                    FilterChip(selected=category==item,onClick={category=item},label={Text(item)})
                }
                OutlinedTextField(value=message,onValueChange={message=it.take(2000)},label={Text("شرح مشکل بدون داده محرمانه")},
                    minLines=4,modifier=Modifier.fillMaxWidth())
                val report="گزارش پشتیبانی سورا\nنسخه: $version\nموضوع: $category\nشرح: ${message.trim()}"
                Button(enabled=message.isNotBlank(),onClick={
                    val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("گزارش سورا",report))
                    feedback="گزارش کپی شد؛ هنوز برای پشتیبانی ارسال نشده است."
                },modifier=Modifier.fillMaxWidth()) { Text("کپی گزارش") }
                OutlinedButton(enabled=message.isNotBlank(),onClick={
                    try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type="text/plain";putExtra(Intent.EXTRA_TEXT,report)
                    },"اشتراک گزارش با مخاطب تأییدشده")) }
                    catch(_:Exception) { feedback="برنامه اشتراک‌گذاری در دسترس نیست." }
                },modifier=Modifier.fillMaxWidth()) { Text("اشتراک گزارش") }
                if(feedback.isNotBlank()) Text(feedback,color=muted)
            }
            "contact" -> {
                ServicePanel("تماس با صبا گستر اراک",SuraContact.PHONE)
                Button(onClick={
                    if(!SuraContact.openDialer(context)) feedback="شماره‌گیر در دسترس نیست؛ شماره تماس را کپی کنید."
                },modifier=Modifier.fillMaxWidth()) { Text("باز کردن شماره‌گیر") }
                OutlinedButton(onClick={
                    val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("شماره تماس سورا",SuraContact.PHONE))
                    feedback="شماره تماس کپی شد."
                },modifier=Modifier.fillMaxWidth()) { Text("کپی شماره تماس") }
                if(feedback.isNotBlank()) Text(feedback,color=muted)
                ServicePanel("پشتیبانی متنی","برای ثبت و پیگیری درخواست از بخش پشتیبانی استفاده کنید.")
                onSupport?.let { open ->
                    Button(onClick=open,modifier=Modifier.fillMaxWidth()) { Text("ثبت درخواست و ارتباط با پشتیبانی") }
                }
            }
            "privacy", "terms" -> {
                ServicePanel("نسخه سند",ir.saba.invoice.auth.SuraLegal.VERSION)
                ir.saba.invoice.auth.SuraLegal.sections(kind).forEach { (heading,body) -> ServicePanel(heading,body) }
            }
            else -> {
                ServicePanel("صورتحساب سورا | SURA","محصول صبا گستر اراک برای مدیریت مؤدیان، مشتریان، صدور صورتحساب و پیگیری ارسال مالیاتی.")
                ServicePanel("نسخه برنامه",version.ifBlank { "نسخه نامشخص" }+"\nشناسه بسته: "+context.packageName)
                ServicePanel("وضعیت محصول","نسخه در حال تکمیل و آزمون است. پذیرش واقعی مالیاتی و پرداخت تجاری باید قبل از انتشار عمومی تأیید شوند.")
            }
        }
        OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()) { Text("بازگشت به خانه") }
    }
}

/** View inactive and historical taxpayers without reactivating or issuing an invoice. */
@Composable
private fun ArchiveHistoryBrowser(db:SabaDatabase,account:AccountScope,enabled:Boolean,refreshKey:String) {
    var expanded by remember { mutableStateOf(false) }
    var companies by remember { mutableStateOf<List<Company>>(emptyList()) }
    var companyId by remember { mutableStateOf<Long?>(null) }
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var invoices by remember { mutableStateOf<List<Invoice>>(emptyList()) }
    var details by remember { mutableStateOf<List<OfficialSubmission>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var customerPage by remember { mutableIntStateOf(0) }
    var selectedInvoice by remember { mutableStateOf<Long?>(null) }
    var lines by remember { mutableStateOf<List<InvoiceLine>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(expanded,refreshKey) {
        if(expanded) {
            try { companies=db.companyDao().all().filter { it.purchaserId==account.ownerId } }
            catch(e:CancellationException){throw e}
            catch(_:Exception){error="بارگذاری مؤدیان انجام نشد."}
        }
    }
    LaunchedEffect(companyId,expanded,refreshKey) {
        customers=emptyList();invoices=emptyList();details=emptyList();page=0;customerPage=0;selectedInvoice=null
        if(expanded) companyId?.let { id ->
            try {
                require(db.companyDao().byId(id)?.purchaserId==account.ownerId)
                customers=db.customerDao().forCompany(id)
                invoices=db.invoiceDao().forCompany(id)
                details=db.officialSubmissionDao().forCompany(id)
            } catch(e:CancellationException){throw e}
            catch(_:Exception){error="بارگذاری سوابق انجام نشد."}
        }
    }
    LaunchedEffect(selectedInvoice) {
        lines=emptyList()
        selectedInvoice?.let { id ->
            try {
                require(invoices.any { it.id==id })
                lines=db.invoiceLineDao().forInvoice(id)
            } catch(e:CancellationException){throw e}
            catch(_:Exception){error="بارگذاری اقلام انجام نشد."}
        }
    }
    OutlinedButton(onClick={expanded=!expanded},enabled=enabled,modifier=Modifier.fillMaxWidth()) {
        Text(if(expanded) "بستن مرور سوابق" else "مشاهده تمام سوابق مؤدیان")
    }
    if(!expanded)return
    Text("مرور فقط‌خواندنی — شامل مؤدیان غیرفعال",fontWeight=FontWeight.Bold)
    var choose by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick={choose=true},enabled=enabled,modifier=Modifier.fillMaxWidth()) {
            Text(companies.firstOrNull { it.id==companyId }?.name ?: "انتخاب مؤدی")
        }
        DropdownMenu(expanded=choose,onDismissRequest={choose=false}) {
            companies.forEach { c -> DropdownMenuItem(text={Text(c.name)},onClick={companyId=c.id;choose=false;error=""}) }
        }
    }
    if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
    if(companyId!=null) {
        Text("مشتریان (${customers.size})",fontWeight=FontWeight.Bold)
        customers.drop(customerPage*20).take(20).forEach { c ->
            ServicePanel(c.name,"شناسه ملی: ${c.nationalId.ifBlank { "—" }}\nشماره اقتصادی: ${c.economicId.ifBlank { "—" }}")
        }
        if(customers.size>20) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={customerPage--},enabled=customerPage>0){Text("قبلی")}
            TextButton(onClick={customerPage++},enabled=(customerPage+1)*20<customers.size){Text("بعدی")}
        }
        Text("صورتحساب‌ها (${invoices.size})",fontWeight=FontWeight.Bold)
        invoices.drop(page*20).take(20).forEach { inv ->
            val official=details.firstOrNull { it.invoiceId==inv.id }
            ServicePanel("شماره ${inv.invoiceNumber} | ${inv.issueDate}",
                "مشتری: ${customers.firstOrNull { it.id==inv.customerId }?.name.orEmpty()}\nمبلغ: ${inv.totalAmountRial} ریال\nمالیات: ${inv.taxAmountRial} ریال\nشماره مالیاتی: ${official?.taxId ?: "—"}\nوضعیت: ${invoiceStatus(inv.status)}")
            TextButton(onClick={selectedInvoice=if(selectedInvoice==inv.id)null else inv.id}) {
                Text(if(selectedInvoice==inv.id)"بستن اقلام" else "مشاهده اقلام")
            }
            if(selectedInvoice==inv.id) lines.forEach { l ->
                Text("${l.description} | تعداد: ${l.quantityExact.ifBlank { l.quantity.toString() }} | قیمت واحد: ${l.unitPriceRial} ریال",
                    style=MaterialTheme.typography.bodySmall)
            }
            if(selectedInvoice==inv.id) {
                val ctx=LocalContext.current
                val documents=runCatching { InvoiceLocalDetails.fromJson(inv.localDetailsJson).documents }.getOrDefault(emptyList())
                documents.forEach { document ->
                    OutlinedButton(onClick={
                        runCatching { InvoiceDocumentPicker.open(ctx,document) }.onFailure {error="نمایش فایل پیوست انجام نشد."}
                    },modifier=Modifier.fillMaxWidth()){Text("مشاهده پیوست: "+document.name)}
                }
            }
        }
        if(invoices.size>20) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={page--;selectedInvoice=null},enabled=page>0){Text("قبلی")}
            TextButton(onClick={page++;selectedInvoice=null},enabled=(page+1)*20<invoices.size){Text("بعدی")}
        }
    }
}

@Composable private fun ArchiveJournalBrowser(account:AccountScope,refreshKey:String,enabled:Boolean) {
    val ctx=LocalContext.current
    var opened by remember {mutableStateOf(false)}
    var files by remember {mutableStateOf<List<java.io.File>>(emptyList())}
    var selected by remember {mutableStateOf<java.io.File?>(null)}
    var record by remember {mutableStateOf<org.json.JSONObject?>(null)}
    var table by remember {mutableStateOf("")}
    var page by remember {mutableIntStateOf(0)}
    LaunchedEffect(opened,refreshKey) {
        if(opened) files=withContext(Dispatchers.IO){AccountArchivePortable(ctx.applicationContext,account).history()}
    }
    LaunchedEffect(selected) {
        record=null;page=0;table=""
        selected?.let { f -> record=withContext(Dispatchers.IO){runCatching {org.json.JSONObject(f.readText())}.getOrNull()} }
    }
    OutlinedButton(onClick={opened=!opened},enabled=enabled,modifier=Modifier.fillMaxWidth()){
        Text(if(opened)"بستن سوابق مالی" else "مشاهده سوابق مالی و ارسال پشتیبان")
    }
    if(!opened)return
    if(files.isEmpty()) Text("هنوز گزارش تاریخی بازیابی نشده است.")
    files.take(20).forEach {f->TextButton(onClick={selected=f}){
        Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(f.lastModified()))+" | "+f.name.take(8))
    }}
    record?.let {root->
        val journals=root.getJSONObject("journals")
        val names=mapOf("quota_wallets" to "کیف پول تاریخی","company_quotas" to "اعتبار مؤدیان",
            "quota_transfers" to "انتقال‌های اعتبار","invoice_quota_uses" to "مصرف اعتبار",
            "invoice_creation_claims" to "درخواست‌های ثبت فاکتور","credit_transfer_claims" to "درخواست‌های انتقال",
            "legacy_import_receipts" to "سوابق بازیابی","legacy_quota_exemptions" to "معافیت‌های قدیمی",
            "moadian_simulations" to "آزمون محلی")
        Text("سوابق تاریخی؛ موجودی قابل مصرف نیست",fontWeight=FontWeight.Bold)
        root.optJSONObject("creditSnapshot")?.let { snapshot ->
            val targets=snapshot.optJSONObject("taxpayers")?:org.json.JSONObject()
            ServicePanel("اعتبار هنگام پشتیبان‌گیری","مالک: ${snapshot.optLong("owner")} | هدیه: ${snapshot.optLong("gift")}\n"+
                targets.keys().asSequence().joinToString("\n"){id->"مودی $id: ${targets.optLong(id)}"})
        }
        val historical=root.optJSONObject("invoiceStatus")?:org.json.JSONObject()
        if(historical.length()>0) ServicePanel("وضعیت اصلی فاکتورها هنگام ذخیره",historical.keys().asSequence().take(100)
            .joinToString("\n"){id->"$id: ${invoiceStatus(historical.getString(id))}"})
        val evidence=root.optJSONObject("submissionEvidence")?:org.json.JSONObject()
        if(evidence.length()>0) ServicePanel("وضعیت درخواست‌های رسمی هنگام ذخیره",evidence.keys().asSequence().take(50)
            .joinToString("\n"){id->"$id: ${evidence.getJSONObject(id).optString("state")}"})
        names.forEach { (k,v)->FilterChip(selected=table==k,onClick={table=k;page=0},label={Text(v)})}
        val rows=journals.optJSONArray(table)
        if(rows!=null){
            Text("تعداد: ${rows.length()}")
            for(i in page*10 until minOf((page+1)*10,rows.length())){
                val row=rows.getJSONObject(i)
                ServicePanel("ردیف ${i+1}",row.keys().asSequence().filter {it !in setOf("payloadJson","receiptJson")}.joinToString("\n"){k->"$k: ${row.opt(k)}"})
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                TextButton(onClick={page--},enabled=page>0){Text("قبلی")}
                TextButton(onClick={page++},enabled=(page+1)*10<rows.length()){Text("بعدی")}
            }
        }
    }
}
