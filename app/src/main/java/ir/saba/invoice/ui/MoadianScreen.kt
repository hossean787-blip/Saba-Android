package ir.saba.invoice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.provider.OpenableColumns
import android.content.Intent
import android.net.Uri
import ir.saba.invoice.security.TaxpayerKeyVault
import ir.saba.invoice.security.TaxpayerPrivateKey
import ir.saba.invoice.security.TaxpayerKeyGenerator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.Company
import ir.saba.invoice.data.SabaDatabase
import ir.saba.invoice.data.MoadianDetails
import androidx.room.withTransaction
import ir.saba.invoice.domain.IranDivisions
import ir.saba.invoice.domain.MoadianFieldValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * First incremental Moadian UI. Uses the existing Company entity to preserve invoice foreign keys.
 * Extended tax identity and encrypted key material must be implemented before production use.
 */
@Composable
fun MoadianScreen(
    db: SabaDatabase,
    companies: List<Company>,
    onChanged: suspend () -> Unit,
    onSelect: (Long) -> Unit,
    onNotice: (String) -> Unit,
    onEditing: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val vault = remember { TaxpayerKeyVault() }
    var privateKey by remember { mutableStateOf("") }
    var generatedPublicKey by remember { mutableStateOf("") }
    var publicKeyExported by remember { mutableStateOf(false) }
    var generatedKeyConfirmed by remember { mutableStateOf(false) }
    var generatedNotice by remember { mutableStateOf<String?>(null) }
    val exportPublicKey = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null && generatedPublicKey.isNotBlank()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(generatedPublicKey.toByteArray(Charsets.US_ASCII))
                } ?: throw IllegalStateException()
                publicKeyExported = true
                generatedNotice = "کلید عمومی ذخیره شد؛ آن را در کارپوشه مالیاتی طبق روش ارسال انتخابی ثبت کنید."
            } catch (_: Exception) { generatedNotice = "ذخیره کلید عمومی انجام نشد؛ دوباره تلاش کنید." }
        }
    }
    var storedKey by remember { mutableStateOf<String?>(null) }
    var keyMode by remember { mutableStateOf("manual") }
    var keyError by remember { mutableStateOf<String?>(null) }
    val importKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    privateKey = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val filename = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else ""
                        }.orEmpty()
                        context.contentResolver.openInputStream(uri)?.use {
                            ir.saba.invoice.security.TaxpayerKeyImport.read(it, filename)
                        } ?: throw IllegalArgumentException()
                    }
                    keyError = null
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    privateKey = ""
                    keyError = "فایل معتبر txt، pem یا key با کلید خصوصی RSA و حداکثر ۶۴ کیلوبایت انتخاب کنید."
                } finally { busy = false }
            }
        }
    }
    var logoPath by remember { mutableStateOf("") }
    var logoError by remember { mutableStateOf<String?>(null) }
    val importLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= 2 * 1024 * 1024)
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: throw IllegalArgumentException()
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096)
            require(bounds.outMimeType in setOf("image/png", "image/jpeg", "image/webp"))
            val directory = java.io.File(context.filesDir, "taxpayer-logos").apply { mkdirs() }
            val file = java.io.File(directory, java.util.UUID.randomUUID().toString() + ".image")
            file.writeBytes(bytes)
            logoPath = file.absolutePath
            logoError = null
        } catch (_: Exception) { logoError = "تصویر PNG، JPEG یا WebP تا ۲ مگابایت و ابعاد حداکثر ۴۰۹۶ انتخاب کنید." }
    }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var mode by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var nationalId by remember { mutableStateOf("") }
    var economicId by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var purchaserId by remember { mutableStateOf("owner") }
    var kind by remember { mutableStateOf("شخص حقوقی") }
    var registrationNumber by remember { mutableStateOf("") }
    var invoiceSerialStart by remember { mutableStateOf("") }
    var referenceSerialStart by remember { mutableStateOf("") }
    var fax by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var county by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var certificateExpiry by remember { mutableStateOf("") }
    var fiscalMemoryId by remember { mutableStateOf("") }
    var detailRows by remember { mutableStateOf(emptyList<MoadianDetails>()) }
    LaunchedEffect(companies) { detailRows = db.moadianDetailsDao().all() }
    fun details(id: Long) = MoadianDetails(
        companyId = id,
        taxpayerType = when(kind) {
            "شخص حقیقی" -> "NATURAL"
            "شخص حقوقی" -> "LEGAL"
            "مشارکت مدنی" -> "PARTNERSHIP"
            else -> "FOREIGN"
        },
        registrationNumber = registrationNumber,
        invoiceSerialStart = MoadianFieldValidation.digits(invoiceSerialStart),
        referenceSerialStart = MoadianFieldValidation.digits(referenceSerialStart),
        fax = MoadianFieldValidation.digits(fax),
        postalCode = MoadianFieldValidation.digits(postalCode),
        province = province,
        county = county,
        mobile = MoadianFieldValidation.digits(mobile),
        certificateExpiry = certificateExpiry,
        fiscalMemoryId = MoadianFieldValidation.digits(fiscalMemoryId).trim().uppercase(java.util.Locale.ROOT),
        logoUri = logoPath
    )
    var error by remember { mutableStateOf<String?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    fun close() { privateKey = ""; generatedPublicKey = ""; publicKeyExported = false; generatedKeyConfirmed = false; generatedNotice = null; storedKey = null; keyError = null; mode = null; pending = false; error = null; onEditing(false) }
    fun open(which: String) {
        val current = if (which == "new") null else companies.firstOrNull { it.id == selectedId }
        if (which != "new" && current == null) {
            onNotice("ابتدا یک مودی از جدول انتخاب کنید.")
            return
        }
        privateKey = ""; generatedPublicKey = ""; publicKeyExported = false; generatedNotice = null; storedKey = null; keyError = null; keyMode = "manual"; logoPath = ""; logoError = null; fieldErrors = emptyMap()
        loadFailed = false
        name = current?.name.orEmpty()
        nationalId = current?.nationalId.orEmpty()
        economicId = current?.economicId.orEmpty()
        address = current?.address.orEmpty()
        phone = current?.phone.orEmpty()
        purchaserId = current?.purchaserId ?: "owner"
        kind = "شخص حقوقی"
        registrationNumber = ""
        invoiceSerialStart = ""
        referenceSerialStart = ""
        fax = ""
        postalCode = ""
        province = ""
        county = ""
        mobile = ""
        certificateExpiry = ""
        fiscalMemoryId = ""
        error = null
        mode = which
        onEditing(true)
        if (which == "edit" && current != null) {
            scope.launch {
                busy = true
                try {
                val d = db.moadianDetailsDao().byCompany(current.id)
                if (d != null) {
                    kind = when(d.taxpayerType) {
                        "NATURAL" -> "شخص حقیقی"
                        "PARTNERSHIP" -> "مشارکت مدنی"
                        "FOREIGN" -> "اتباع غیرایرانی"
                        else -> "شخص حقوقی"
                    }
                    registrationNumber = d.registrationNumber
                    invoiceSerialStart = d.invoiceSerialStart
                    referenceSerialStart = d.referenceSerialStart
                    fax = d.fax
                    postalCode = d.postalCode
                    province = d.province
                    county = d.county
                    mobile = d.mobile
                    certificateExpiry = d.certificateExpiry
                    fiscalMemoryId = d.fiscalMemoryId
                    storedKey = d.encryptedPrivateKey
                    logoPath = d.logoUri
                }
                } catch (_: Exception) { loadFailed = true; error = "بازیابی اطلاعات انجام نشد؛ فرم را ببندید و دوباره تلاش کنید." }
                finally { busy = false }
            }
        }
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF2F9FF)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (mode == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { open("new") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null, Modifier.size(17.dp)); Text("جدید")
                }
                OutlinedButton(onClick = { open("edit") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(17.dp)); Text("ویرایش")
                }
                OutlinedButton(onClick = { open("delete") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(17.dp)); Text("حذف")
                }
            }
            OutlinedTextField(enabled = !busy && !pending, value = query,
                onValueChange = { query = it }, label = { Text("جستجوی مودیان") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "جستجو") },
                shape = RoundedCornerShape(17.dp),
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("فهرست مودیان", fontWeight = FontWeight.Bold,
                color = Color(0xFF153A51), style = MaterialTheme.typography.titleMedium)
            fun searchText(value: String) = MoadianFieldValidation.digits(value).replace('ي', 'ی').replace('ك', 'ک')
            val needle = searchText(query)
            val rows = companies.filter { searchText(it.name).contains(needle, ignoreCase = true) ||
                searchText(it.nationalId).contains(needle) || searchText(it.economicId).contains(needle) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(rows, key = { _, item -> item.id }) { index, company ->
                    val selected = selectedId == company.id
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedId = company.id
                            if (detailRows.firstOrNull { it.companyId == company.id }?.inactive != true)
                                onSelect(company.id)
                        },
                        colors = CardDefaults.cardColors(containerColor =
                            if (selected) Color(0xFFE5F7F9) else Color.White),
                        border = if (selected) androidx.compose.foundation.BorderStroke(
                            1.dp, Color(0xFF2DA2B1)) else null,
                        shape = RoundedCornerShape(19.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        val d = detailRows.firstOrNull { it.companyId == company.id }
                        val type = when (d?.taxpayerType) {
                            "NATURAL" -> "حقیقی"
                            "LEGAL" -> "حقوقی"
                            "PARTNERSHIP" -> "مشارکت مدنی"
                            "FOREIGN" -> "اتباع غیرایرانی"
                            else -> "نامشخص"
                        }
                        val check = when (d?.keyCheckStatus) {
                            "NEEDS_MATCH" -> "نیازمند بررسی تطابق"
                            "INVALID" -> "نامعتبر"
                            else -> "بررسی‌نشده"
                        }
                        Row(Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFFE5F3F7)) {
                                Text("${index + 1}", modifier = Modifier.padding(11.dp),
                                    color = Color(0xFF157F96), fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(company.name, fontWeight = FontWeight.Bold,
                                    color = Color(0xFF193F55))
                                Text("نوع مودی: $type", style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF667F91))
                                Text("صحت کلیدها: $check", style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF667F91))
                            }
                            if (d?.inactive == true)
                                Text("غیرفعال", color = Color(0xFFB74C6D),
                                    style = MaterialTheme.typography.labelSmall)
                            else if (selected)
                                Text("انتخاب‌شده", color = Color(0xFF007F71),
                                    style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } else if (mode == "delete") {
            AlertDialog(
                onDismissRequest = { if (!busy) close() },
                title = { Text("حذف مودی") },
                text = { Text("مودی غیرفعال می‌شود؛ صورتحساب‌ها و سوابق آن حذف نمی‌شوند. با ویرایش و ذخیره، دوباره فعال خواهد شد.") },
                confirmButton = { TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val id = selectedId ?: return@launch
                            db.withTransaction {
                                val previous = db.moadianDetailsDao().byCompany(id) ?: MoadianDetails(companyId = id)
                                db.moadianDetailsDao().upsert(previous.copy(inactive = true))
                            }
                            detailRows = db.moadianDetailsDao().all()
                            onChanged()
                            close()
                            onNotice("مودی غیرفعال شد؛ سوابق محفوظ است.")
                        } catch (_: Exception) { onNotice("غیرفعال‌سازی انجام نشد؛ دوباره تلاش کنید.") }
                        finally { busy = false }
                    }
                }) { Text("تأیید غیرفعال‌سازی") } },
                dismissButton = { TextButton(enabled = !busy, onClick = ::close) { Text("انصراف") } }
            )
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (mode == "new") "ثبت مودی جدید" else "ویرایش مودی",
                style = MaterialTheme.typography.titleLarge, color = Color(0xFF173B51),
                fontWeight = FontWeight.ExtraBold)

            Text("نوع مودی *", fontWeight = FontWeight.SemiBold,
                color = Color(0xFF174256))
            val kinds = listOf("شخص حقیقی", "شخص حقوقی", "مشارکت مدنی", "اتباع غیرایرانی")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                kinds.forEach { item ->
                    val chosen = kind == item
                    Surface(
                        modifier = Modifier.weight(1f).height(60.dp)
                            .clickable(enabled = !busy && !pending) { kind = item },
                        shape = RoundedCornerShape(12.dp),
                        color = if (chosen) Color(0xFF0B7892) else Color(0xFFE6F1F6)
                    ) {
                        Box(contentAlignment = androidx.compose.ui.Alignment.Center,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp)) {
                            Text(item, color = if (chosen) Color.White else Color(0xFF30596C),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
            OutlinedTextField(enabled = !busy && !pending, isError = fieldErrors["name"] != null, supportingText = { fieldErrors["name"]?.let { Text(it) } }, value = name, onValueChange = { name = it }, label = { Text("نام *") }, modifier = Modifier.fillMaxWidth())
            /* help:شماره اقتصادی * */
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(enabled = !busy && !pending, isError = fieldErrors["economicId"] != null, supportingText = { fieldErrors["economicId"]?.let { Text(it) } }, value = economicId, onValueChange = { economicId = MoadianFieldValidation.digits(it) }, label = { Text("شماره اقتصادی *") }, modifier = Modifier.weight(1f))
            /* help:شناسه ملی * */
            OutlinedTextField(enabled = !busy && !pending, isError = fieldErrors["nationalId"] != null, supportingText = { fieldErrors["nationalId"]?.let { Text(it) } }, value = nationalId, onValueChange = { nationalId = MoadianFieldValidation.digits(it) }, label = { Text("شناسه ملی *") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(enabled = !busy && !pending, value = registrationNumber, onValueChange = { registrationNumber = it }, label = { Text("شماره ثبت") }, modifier = Modifier.fillMaxWidth())
            /* help:شروع سریال صورتحساب */
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(enabled = !busy && !pending, isError = fieldErrors["invoiceSerialStart"] != null, supportingText = { fieldErrors["invoiceSerialStart"]?.let { Text(it) } }, value = invoiceSerialStart, onValueChange = { invoiceSerialStart = MoadianFieldValidation.digits(it) }, label = { Text("شروع سریال صورتحساب") }, modifier = Modifier.weight(1f))
            /* help:شروع سریال صورتحساب ارجاعی */
            OutlinedTextField(enabled = !busy && !pending, isError = fieldErrors["referenceSerialStart"] != null, supportingText = { fieldErrors["referenceSerialStart"]?.let { Text(it) } }, value = referenceSerialStart, onValueChange = { referenceSerialStart = MoadianFieldValidation.digits(it) }, label = { Text("شروع سریال صورتحساب ارجاعی") }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(enabled = !busy && !pending, value = phone, onValueChange = { phone = MoadianFieldValidation.digits(it) }, label = { Text("تلفن") }, modifier = Modifier.weight(1f))
            OutlinedTextField(enabled = !busy && !pending, value = fax, onValueChange = { fax = MoadianFieldValidation.digits(it) }, label = { Text("فکس") }, modifier = Modifier.weight(1f))
            }
            /* help:کدپستی * */
            OutlinedTextField(enabled = !busy && !pending, isError = fieldErrors["postalCode"] != null, supportingText = { fieldErrors["postalCode"]?.let { Text(it) } }, value = postalCode, onValueChange = { postalCode = MoadianFieldValidation.digits(it) }, label = { Text("کدپستی *") }, modifier = Modifier.weight(1f))
            }
            MoadianChoice("استان *", province, IranDivisions.counties.keys.toList(), enabled = !busy && !pending) { if (!busy && !pending) { province = it; county = "" } }
            fieldErrors["province"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            MoadianChoice("شهرستان *", county, IranDivisions.counties[province].orEmpty(), enabled = !busy && !pending) { if (!busy && !pending) county = it }
            fieldErrors["county"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(enabled = !busy && !pending, isError = fieldErrors["address"] != null, supportingText = { fieldErrors["address"]?.let { Text(it) } }, value = address, onValueChange = { address = it }, label = { Text("آدرس *") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(enabled = !busy && !pending, isError = fieldErrors["mobile"] != null, supportingText = { fieldErrors["mobile"]?.let { Text(it) } }, value = mobile, onValueChange = { mobile = MoadianFieldValidation.digits(it) }, label = { Text("شماره موبایل *") }, modifier = Modifier.weight(1f))
            Text("لوگو")
            OutlinedButton(onClick = { importLogo.launch("image/*") }, enabled = !busy && !pending) { Text(if (logoPath.isBlank()) "انتخاب لوگو" else "تغییر لوگوی انتخاب‌شده") }
            logoError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            MoadianHelpLabel("تاریخ انقضاء گواهی امضاء", "صرفاً برای یادآوری است و اجباری نیست. تاریخ واقعی درج‌شده در گواهی را وارد کنید؛ اعتبار گواهی‌ها معمولاً تا دو سال است.")
            OutlinedTextField(enabled = !busy && !pending, value = certificateExpiry, onValueChange = { certificateExpiry = it }, label = { Text("تاریخ انقضاء گواهی امضاء") }, modifier = Modifier.fillMaxWidth())
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFE7F5FA))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("دریافت شناسه یکتای حافظه مالیاتی", fontWeight = FontWeight.Bold, color = Color(0xFF16384E))
                    Text("برای دریافت شناسه باید با حساب مؤدی وارد کارپوشه رسمی سازمان امور مالیاتی شوید. سورا شناسه صادر نمی‌کند. پس از صدور، شناسه را در کادر زیر وارد کنید.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        enabled = !busy && !pending,
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://my.tax.gov.ir")))
                            } catch (_: Exception) {
                                onNotice("مرورگر در دسترس نیست؛ نشانی my.tax.gov.ir را دستی باز کنید.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ورود به کارپوشه و دریافت شناسه یکتا") }
                }
            }
            MoadianHelpLabel("شناسه یکتا (پس از صدور)", "شناسه ۶ کاراکتری حافظه مالیاتی را از بخش عضویت کارپوشه دریافت کنید. شروع با A برای همه اجباری نشده است. در صورت نبود کلید خصوصی متناظر، شناسه و کلیدهای جدید دریافت کنید.")
            OutlinedTextField(enabled = !busy && !pending, isError = fieldErrors["fiscalMemoryId"] != null, supportingText = { fieldErrors["fiscalMemoryId"]?.let { Text(it) } }, value = fiscalMemoryId, onValueChange = { fiscalMemoryId = it }, label = { Text("شناسه یکتا (پس از صدور)") }, modifier = Modifier.fillMaxWidth())
            Text("صدور شناسه یکتا توسط سازمان امور مالیاتی انجام می‌شود؛ تولید کلید به‌تنهایی شناسه صادر نمی‌کند.", style = MaterialTheme.typography.bodySmall)
            Text("مدیریت کلید عمومی و خصوصی از کارت «کلیدهای مالیاتی» در صفحه خانه انجام می‌شود.", style = MaterialTheme.typography.bodySmall)
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            if (!pending) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    fieldErrors = buildMap {
                        fun check(field: String, message: String?) { if (message != null) put(field, message) }
                        putAll(MoadianFieldValidation.identity(kind, nationalId, economicId))
                        check("name", MoadianFieldValidation.required(name, "نام"))
                        check("nationalId", MoadianFieldValidation.required(nationalId, "شناسه ملی"))
                        check("economicId", MoadianFieldValidation.required(economicId, "شماره اقتصادی"))
                        check("address", MoadianFieldValidation.required(address, "آدرس"))
                        check("postalCode", MoadianFieldValidation.postalCode(postalCode))
                        check("mobile", MoadianFieldValidation.mobile(mobile))
                        check("province", MoadianFieldValidation.required(province, "استان"))
                        check("county", MoadianFieldValidation.required(county, "شهرستان") ?: if (county !in IranDivisions.counties[province].orEmpty()) "شهرستان با استان مطابقت ندارد." else null)
                        check("invoiceSerialStart", MoadianFieldValidation.serial(invoiceSerialStart, "سریال اصلی"))
                        check("referenceSerialStart", MoadianFieldValidation.serial(referenceSerialStart, "سریال ارجاعی")
                            ?: MoadianFieldValidation.serialPair(invoiceSerialStart, referenceSerialStart))
                        if (fiscalMemoryId.isNotBlank()) check("fiscalMemoryId", MoadianFieldValidation.fiscalMemoryId(fiscalMemoryId))
                    }
                    error = if (fieldErrors.isEmpty()) null else "موارد مشخص‌شده در فرم را اصلاح کنید."
                    if (error == null) pending = true
                }, enabled = !busy && !loadFailed, modifier = Modifier.weight(1f)) {
                    Text(if (mode == "new") "ثبت مودی" else "ذخیره تغییرات")
                }
                OutlinedButton(enabled = !busy, onClick = ::close, modifier = Modifier.weight(1f)) { Text("انصراف") }
                }
            } else {
                Text("آیا عملیات را تأیید می‌کنید؟")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                if (db.companyDao().all().any { it.nationalId == nationalId.trim() && (mode == "new" || it.id != selectedId) }) {
                                    error = "این شناسه ملی قبلاً ثبت شده است."
                                    pending = false
                                } else if (mode == "new") {
                                    val savedId = db.withTransaction {
                                        ensureUniqueTaxpayer(db, null, nationalId, economicId, fiscalMemoryId)
                                        val id = db.companyDao().insert(Company(purchaserId = purchaserId, name = name.trim(), nationalId = MoadianFieldValidation.digits(nationalId), economicId = MoadianFieldValidation.digits(economicId), address = address.trim(), phone = phone.trim()))
                                        db.moadianDetailsDao().upsert(details(id).copy(encryptedPrivateKey = null, keyCheckStatus = "UNCHECKED"))
                                        id
                                    }
                                    selectedId = savedId
                                    mode = "edit"
                                    onChanged()
                                    detailRows = db.moadianDetailsDao().all()
                                    close()
                                    onNotice("مودی ثبت شد.")
                                } else {
                                    val old = db.companyDao().byId(selectedId ?: -1) ?: throw IllegalStateException("مودی یافت نشد.")
                                    db.withTransaction {
                                        ensureUniqueTaxpayer(db, old.id, nationalId, economicId, fiscalMemoryId)
                                        db.companyDao().update(old.copy(name = name.trim(), nationalId = MoadianFieldValidation.digits(nationalId), economicId = MoadianFieldValidation.digits(economicId), address = address.trim(), phone = phone.trim()))
                                        val prior = db.moadianDetailsDao().byCompany(old.id)
                                        val encrypted = prior?.encryptedPrivateKey
                                        db.moadianDetailsDao().upsert(details(old.id).copy(encryptedPrivateKey = encrypted, keyCheckStatus = if (encrypted != null) "NEEDS_MATCH" else "UNCHECKED", inactive = false, nextInvoiceSerial = prior?.nextInvoiceSerial, nextReferenceSerial = prior?.nextReferenceSerial))
                                    }
                                    onChanged()
                                    detailRows = db.moadianDetailsDao().all()
                                    close()
                                    onNotice("تغییرات ذخیره شد.")
                                }
                            } catch (e: CancellationException) { throw e }
                            catch (e: DuplicateTaxpayer) { fieldErrors = mapOf(e.field to "این مقدار برای مودی دیگری ثبت شده است."); error = "مودی تکراری قابل ثبت نیست."; pending = false }
                            catch (_: Exception) { error = "ذخیره انجام نشد؛ اطلاعات فرم حفظ شد."; pending = false }
                            finally { busy = false }
                        }
                    }) { Text("تأیید") }
                    OutlinedButton(enabled = !busy, onClick = { pending = false }) { Text("بازگشت") }
                }
            }
            if (pending) TextButton(enabled = !busy, onClick = ::close) { Text("انصراف") }
        }
    }
}

@Composable
private fun MoadianHelpLabel(label: String, guidance: String) {
    var visible by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold, color = Color(0xFF214961))
        IconButton(onClick = { visible = true }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.Info, contentDescription = "راهنمای " + label,
                tint = Color(0xFF13889D), modifier = Modifier.size(19.dp))
        }
    }
    if (visible) AlertDialog(onDismissRequest = { visible = false },
        title = { Text(label.removeSuffix(" *")) }, text = { Text(guidance) },
        confirmButton = { TextButton(onClick = { visible = false }) { Text("متوجه شدم") } })
}

@Composable
private fun MoadianChoice(label: String, value: String, options: List<String>, enabled: Boolean = true, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    OutlinedButton(onClick = { search = ""; expanded = true }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        Text(label + ": " + value.ifBlank { "انتخاب کنید" })
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false },
        title = { Text(label) },
        text = {
            Column {
                OutlinedTextField(search, { search = it }, label = { Text("جستجو") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    itemsIndexed(options.filter { it.contains(search) }) { _, option ->
                        TextButton(enabled = enabled, onClick = { onChange(option); expanded = false }, modifier = Modifier.fillMaxWidth()) { Text(option) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text("بستن") } })
}

private class DuplicateTaxpayer(val field: String) : IllegalArgumentException("Duplicate taxpayer identity")

private suspend fun ensureUniqueTaxpayer(db: SabaDatabase, excludedId: Long?, national: String, economic: String, fiscal: String) {
    val rows = db.companyDao().all().filter { it.id != excludedId }
    if (rows.any { MoadianFieldValidation.digits(it.nationalId) == MoadianFieldValidation.digits(national) })
        throw DuplicateTaxpayer("nationalId")
    if (rows.any { MoadianFieldValidation.digits(it.economicId) == MoadianFieldValidation.digits(economic) })
        throw DuplicateTaxpayer("economicId")
    if (fiscal.isNotBlank() && db.moadianDetailsDao().all().any { it.companyId != excludedId && MoadianFieldValidation.digits(it.fiscalMemoryId).equals(MoadianFieldValidation.digits(fiscal), ignoreCase = true) })
        throw DuplicateTaxpayer("fiscalMemoryId")
}
