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

private val ink=Color(0xFF12304A)
private val muted=Color(0xFF526D80)
private val canvas=Color(0xFFF2F8FC)

@Composable private fun ServiceHeader(title:String,subtitle:String) {
    Column(Modifier.fillMaxWidth().background(
        Brush.linearGradient(listOf(Color(0xFF09253C),Color(0xFF126C88))),
        RoundedCornerShape(22.dp)).padding(22.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text(subtitle,color=Color(0xFFD5EAF2),style=MaterialTheme.typography.bodyMedium)
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
    val archive=remember(db,account,accessToken) {
        AccountArchive(db,account,{AccountSession.requireOwner(accessToken,account)}) {
            TaxpayerIdAllocator(context.applicationContext).use { allocator -> allocator.advancePast(it) }
        }
    }
    var password by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    fun execute(uri:Uri,importing:Boolean) {
        if(running || password.length !in 12..128 || (!importing && password!=repeatPassword)) return
        val secret=password.toCharArray()
        password="";repeatPassword="";confirmed=false
        running=true;onBusy(true);status=""
        scope.launch {
            try {
                val message=withContext(Dispatchers.IO) {
                    if(importing) {
                        val bytes=requireNotNull(context.contentResolver.openInputStream(uri)).use { AccountArchiveCrypto.read(it) }
                        val c=archive.restore(bytes,secret)
                        "بازیابی انجام شد: ${c.taxpayers} مؤدی، ${c.customers} مشتری و ${c.invoices} سابقه صورتحساب."
                    } else {
                        val bytes=archive.export(secret)
                        requireNotNull(context.contentResolver.openOutputStream(uri,"w")).use { it.write(bytes);it.flush() }
                        "فایل پشتیبان رمزگذاری‌شده ذخیره شد."
                    }
                }
                status=message
                if(importing) onChanged()
                onNotice(message)
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                status=if(e is IllegalArgumentException) e.message.orEmpty().take(220)
                    else if(importing) "بازیابی انجام نشد؛ رمز، سلامت و سازگاری فایل را بررسی کنید. اطلاعات مقصد بازنویسی نشد."
                    else "ساخت فایل پشتیبان ناموفق بود؛ فضای ذخیره‌سازی و مجوز فایل را بررسی کنید."
            } finally { secret.fill(' ');running=false;onBusy(false) }
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
        ServicePanel("محتوای فایل","مودیان، مشتریان، صورتحساب‌ها و اقلام، شماره‌های مالیاتی و سوابق ارسال. رمز ورود، کلید خصوصی مالیاتی، اعتبار کیف پول، فایل پیوست و لوگو منتقل نمی‌شوند.")
        if(restore) ServicePanel("شرایط بازیابی امن","فقط در حساب خالی و با همان شناسه مالک و نسخه پایگاه داده. صورتحساب‌ها به‌صورت سابقه فقط‌خواندنی و مؤدیان غیرفعال بازیابی می‌شوند؛ پیش از صدور جدید، سریال و وضعیت مؤدی را با سامانه تطبیق دهید. هیچ اعتبار یا ارسال مالیاتی ایجاد نمی‌شود.")
        else ServicePanel("حفاظت از نسخه پشتیبان","رمز مستقل حداقل ۱۲ نویسه انتخاب کنید. بدون رمز، بازیابی ممکن نیست. فایل را در محل امن نگه دارید. پشتیبان خودکار ابری نیست؛ سقف فایل داده ۱۶ مگابایت است.")
        OutlinedTextField(value=password,onValueChange={password=it.take(128)},label={Text("رمز فایل پشتیبان")},singleLine=true,
            visualTransformation=PasswordVisualTransformation(),enabled=!running,modifier=Modifier.fillMaxWidth().testTag("archivePassword"))
        if(!restore) OutlinedTextField(value=repeatPassword,onValueChange={repeatPassword=it.take(128)},label={Text("تکرار رمز")},singleLine=true,
            visualTransformation=PasswordVisualTransformation(),enabled=!running,modifier=Modifier.fillMaxWidth())
        if(restore) {
            OutlinedButton(onClick={open.launch(arrayOf("application/octet-stream","*/*"))},enabled=!running,modifier=Modifier.fillMaxWidth()) {
                Text(if(picked==null) "انتخاب فایل پشتیبان" else "فایل انتخاب شده؛ تغییر فایل")
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Checkbox(checked=confirmed,onCheckedChange={confirmed=it},enabled=!running)
                Text("شرایط بازیابی سوابق و عدم ارسال مجدد را مطالعه کردم.",modifier=Modifier.padding(top=12.dp))
            }
            Button(onClick={picked?.let { execute(it,true) }},enabled=!running && picked!=null && confirmed && password.length>=12,
                modifier=Modifier.fillMaxWidth().testTag("archiveRestore")) { Text("بازیابی اطلاعات") }
            TextButton(onClick=onLegacyRecovery,enabled=!running) { Text("بازیابی سوابق نسخه قدیمی سورا") }
        } else Button(onClick={save.launch("SURA-backup-${System.currentTimeMillis()}.sura")},
            enabled=!running && password.length>=12 && password==repeatPassword,
            modifier=Modifier.fillMaxWidth().testTag("archiveExport")) { Text("ساخت و ذخیره فایل پشتیبان") }
        if(running) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(status.isNotBlank()) ServicePanel("نتیجه عملیات",status)
    }
}

@Composable
internal fun SuraServiceScreen(kind:String,onBack:()->Unit) {
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
                ServicePanel("صبا گستر اراک","نام برند: صورتحساب سورا. اطلاعات تماس عمومی و رسمی ناشر هنوز تأیید و ثبت نشده است.")
                ServicePanel("راه ارتباطی","از انتشار شماره، ایمیل یا نشانی تأییدنشده خودداری شده است. برای آماده‌کردن گزارش، بخش پشتیبانی در منوی برنامه در دسترس است.")
            }
            "privacy" -> {
                ServicePanel("وضعیت سیاست","پیش‌نویس اطلاع‌رسانی؛ اطلاعات متولی داده، راه درخواست حذف، مدت نگهداری و تاریخ اجرای رسمی باید قبل از انتشار تجاری تکمیل شود.")
                ServicePanel("اطلاعات روی دستگاه","اطلاعات مؤدی، مشتری و صورتحساب در پایگاه داده محلی نگهداری می‌شود؛ کل پایگاه لزوماً رمزگذاری‌شده نیست. کلید خصوصی با سازوکار Android Keystore محافظت می‌شود و در فایل پشتیبان وارد نمی‌شود.")
                ServicePanel("سرویس‌های آنلاین","ورود، حساب، اعتبار و سفارش‌ها در Supabase پردازش می‌شوند؛ میزبانی خارج از ایران ممکن است. ارسال مالیاتی به سامانه مؤدیان با تأیید کاربر است. پرداخت نسخه مستقیم از زرین‌پال عبور می‌کند.")
                ServicePanel("فایل‌ها و پشتیبان","پشتیبان دستی رمزگذاری‌شده است و در محل انتخابی شما ذخیره می‌شود. فایل PDF و اکسل ممکن است محرمانه باشد. فایل پیوست، کلید مالیاتی و اطلاعات ورود در پشتیبان نیستند.")
                ServicePanel("حقوق کاربران","درخواست اصلاح یا حذف اطلاعات حساب و مسائل امنیتی باید از مسیر رسمی ناشر پیگیری شود؛ اطلاعات تماس و رویه اجرایی هنوز تکمیل نشده‌اند.")
            }
            "terms" -> {
                ServicePanel("وضعیت قوانین","پیش‌نویس شرایط استفاده؛ هنوز متن حقوقی تأییدشده یا قرارداد نهایی فروش نیست.")
                ServicePanel("مسئولیت ثبت و ارسال","کاربر باید صحت اطلاعات مالیاتی، اقلام، مبالغ، هویت مؤدی و مشتری را قبل از تأیید بررسی کند. ذخیره محلی یا دریافت شماره پیگیری به معنی پذیرش نهایی نیست.")
                ServicePanel("اعتبار و پرداخت","اعتبار برای استفاده از قابلیت صدور برنامه است. وضعیت و مبلغ پرداخت از سرور بررسی می‌شود؛ خرید یا شارژ تأییدنشده به معنی اعتبار قابل مصرف نیست. شرایط بازپرداخت و اشتراک هنوز نیازمند تأیید رسمی‌اند.")
                ServicePanel("امنیت و پشتیبان","حفاظت از رمز، کلید خصوصی، فایل پشتیبان و دستگاه ضروری است. برای سوابق بازیابی‌شده، از ارسال مجدد خودداری کنید و ابتدا وضعیت را تطبیق دهید.")
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
