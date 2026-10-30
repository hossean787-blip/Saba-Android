package ir.saba.invoice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.provider.OpenableColumns
import ir.saba.invoice.security.TaxpayerKeyVault
import ir.saba.invoice.security.TaxpayerPrivateKey
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
    val context = LocalContext.current
    val vault = remember { TaxpayerKeyVault() }
    var privateKey by remember { mutableStateOf("") }
    var storedKey by remember { mutableStateOf<String?>(null) }
    var keyMode by remember { mutableStateOf("manual") }
    var keyError by remember { mutableStateOf<String?>(null) }
    val importKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val filename = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else ""
                }.orEmpty()
                require(filename.substringAfterLast('.').lowercase() in setOf("txt", "pem", "key"))
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        require(out.size() + n <= TaxpayerPrivateKey.MAX_BYTES)
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                } ?: throw IllegalArgumentException()
                try {
                    val pem = bytes.toString(Charsets.UTF_8)
                    TaxpayerPrivateKey.parse(pem)
                    privateKey = pem
                    keyError = null
                } finally { bytes.fill(0) }
            } catch (_: Exception) {
                privateKey = ""
                keyError = "فایل معتبر txt، pem یا key با کلید خصوصی RSA و حداکثر ۶۴ کیلوبایت انتخاب کنید."
            }
        }
    }
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
        invoiceSerialStart = invoiceSerialStart,
        referenceSerialStart = referenceSerialStart,
        fax = fax,
        postalCode = postalCode,
        province = province,
        county = county,
        mobile = mobile,
        certificateExpiry = certificateExpiry,
        fiscalMemoryId = fiscalMemoryId
    )
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun close() { privateKey = ""; storedKey = null; keyError = null; mode = null; pending = false; error = null; onEditing(false) }
    fun open(which: String) {
        val current = if (which == "new") null else companies.firstOrNull { it.id == selectedId }
        if (which != "new" && current == null) {
            onNotice("ابتدا یک مودی از جدول انتخاب کنید.")
            return
        }
        privateKey = ""; storedKey = null; keyError = null; keyMode = "manual"
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
                }
                } catch (_: Exception) { error = "بازیابی اطلاعات انجام نشد؛ فرم را ببندید و دوباره تلاش کنید." }
                finally { busy = false }
            }
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("مودیان", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF168DC5))
        if (mode == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { open("new") }, modifier = Modifier.weight(1f)) { Text("＋ جدید") }
                OutlinedButton(onClick = { open("edit") }, modifier = Modifier.weight(1f)) { Text("✎ ویرایش") }
                OutlinedButton(onClick = { open("delete") }, modifier = Modifier.weight(1f)) { Text("حذف") }
            }
            OutlinedTextField(query, { query = it }, label = { Text("جستجوی مودیان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ردیف", Modifier.weight(.5f), fontWeight = FontWeight.Bold)
                Text("نام مودی", Modifier.weight(2f), fontWeight = FontWeight.Bold)
                Text("نوع مودی", Modifier.weight(1.3f), fontWeight = FontWeight.Bold)
                Text("صحت کلیدها", Modifier.weight(1.3f), fontWeight = FontWeight.Bold)
            }
            val rows = companies.filter { it.name.contains(query, ignoreCase = true) || it.nationalId.contains(query) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(rows, key = { _, item -> item.id }) { index, company ->
                    val selected = selectedId == company.id
                    Surface(
                        color = if (selected) Color(0xFFD8F1FF) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { selectedId = company.id; onSelect(company.id) }
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text((index + 1).toString(), Modifier.weight(.5f))
                            Text(company.name, Modifier.weight(2f))
                            Text(when(detailRows.firstOrNull { it.companyId == company.id }?.taxpayerType) {
                                "NATURAL" -> "حقیقی"
                                "LEGAL" -> "حقوقی"
                                "PARTNERSHIP" -> "مشارکت"
                                "FOREIGN" -> "اتباع"
                                else -> "نامشخص"
                            }, Modifier.weight(1.3f))
                            Text(when(detailRows.firstOrNull { it.companyId == company.id }?.keyCheckStatus) {
                                "NEEDS_MATCH" -> "نیازمند بررسی تطابق"
                                "INVALID" -> "نامعتبر"
                                else -> "بررسی‌نشده"
                            }, Modifier.weight(1.3f))
                        }
                    }
                }
            }
        } else if (mode == "delete") {
            AlertDialog(
                onDismissRequest = ::close,
                title = { Text("حذف مودی") },
                text = { Text("حذف فیزیکی مودی به دلیل ارتباط با صورتحساب‌ها غیرفعال است. اطلاعات حفظ می‌شود.") },
                confirmButton = { TextButton(onClick = ::close) { Text("متوجه شدم") } }
            )
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (mode == "new") "ثبت مودی جدید" else "ویرایش مودی", style = MaterialTheme.typography.titleLarge)
            Text("اطلاعات به‌صورت محلی ذخیره می‌شوند؛ تطابق کلید با شناسه یکتا نیازمند استعلام جداگانه است.", style = MaterialTheme.typography.bodySmall)
            Text("نوع مودی")
            val kinds = listOf("شخص حقیقی", "شخص حقوقی", "مشارکت مدنی", "اتباع غیرایرانی")
            kinds.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { item ->
                        FilterChip(selected = kind == item, onClick = { kind = item }, label = { Text(item) })
                    }
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text("نام *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(economicId, { economicId = it }, label = { Text("شماره اقتصادی *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(nationalId, { nationalId = it }, label = { Text("شناسه ملی *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(registrationNumber, { registrationNumber = it }, label = { Text("شماره ثبت") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(invoiceSerialStart, { invoiceSerialStart = it }, label = { Text("شروع سریال صورتحساب") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(referenceSerialStart, { referenceSerialStart = it }, label = { Text("شروع سریال صورتحساب ارجاعی") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("تلفن") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fax, { fax = it }, label = { Text("فکس") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(postalCode, { postalCode = it }, label = { Text("کدپستی *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(province, { province = it }, label = { Text("استان *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(county, { county = it }, label = { Text("شهرستان *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(address, { address = it }, label = { Text("آدرس *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(mobile, { mobile = it }, label = { Text("شماره موبایل *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(certificateExpiry, { certificateExpiry = it }, label = { Text("تاریخ انقضاء گواهی امضاء") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fiscalMemoryId, { fiscalMemoryId = it }, label = { Text("شناسه یکتا *") }, modifier = Modifier.fillMaxWidth())
            Text("کلید خصوصی (PrivateKey) *")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = keyMode == "manual", onClick = { keyMode = "manual"; privateKey = ""; keyError = null }, label = { Text("ورود دستی") })
                FilterChip(selected = keyMode == "file", onClick = { keyMode = "file"; privateKey = ""; keyError = null }, label = { Text("بارگذاری فایل") })
            }
            if (storedKey != null) Text("کلید رمزنگاری‌شده موجود است؛ ورود کلید جدید آن را جایگزین می‌کند.")
            if (keyMode == "manual") OutlinedTextField(privateKey, {
                if (it.length <= TaxpayerPrivateKey.MAX_BYTES) privateKey = it
            }, label = { Text("متن کلید خصوصی") }, visualTransformation = PasswordVisualTransformation(),
                isError = keyError != null, modifier = Modifier.fillMaxWidth())
            else OutlinedButton(onClick = { importKey.launch(arrayOf("*/*")) }, enabled = !busy) {
                Text(if (privateKey.isEmpty()) "انتخاب فایل کلید" else "فایل کلید دریافت شد")
            }
            keyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            if (!pending) {
                Button(onClick = {
                    keyError = if (privateKey.isBlank()) {
                        if (storedKey == null) "کلید خصوصی اجباری است." else null
                    } else try { TaxpayerPrivateKey.parse(privateKey); null } catch (e: IllegalArgumentException) { e.message }
                    error = when {
                        name.isBlank() -> "نام مودی اجباری است."
                        nationalId.isBlank() -> "شناسه ملی اجباری است."
                        economicId.isBlank() -> "شماره اقتصادی اجباری است."
                        address.isBlank() -> "آدرس اجباری است."
                        MoadianFieldValidation.postalCode(postalCode) != null -> MoadianFieldValidation.postalCode(postalCode)
                        MoadianFieldValidation.mobile(mobile) != null -> MoadianFieldValidation.mobile(mobile)
                        province.isBlank() || county.isBlank() -> "استان و شهرستان اجباری هستند."
                        MoadianFieldValidation.serial(invoiceSerialStart, "سریال اصلی") != null -> MoadianFieldValidation.serial(invoiceSerialStart, "سریال اصلی")
                        MoadianFieldValidation.serial(referenceSerialStart, "سریال ارجاعی") != null -> MoadianFieldValidation.serial(referenceSerialStart, "سریال ارجاعی")
                        MoadianFieldValidation.serialPair(invoiceSerialStart, referenceSerialStart) != null -> MoadianFieldValidation.serialPair(invoiceSerialStart, referenceSerialStart)
                        MoadianFieldValidation.fiscalMemoryId(fiscalMemoryId) != null -> MoadianFieldValidation.fiscalMemoryId(fiscalMemoryId)
                        else -> null
                    }
                    if (error == null && keyError == null) pending = true
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (mode == "new") "ثبت مودی" else "ذخیره تغییرات")
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
                                    db.withTransaction {
                                        val id = db.companyDao().insert(Company(purchaserId = purchaserId, name = name.trim(), nationalId = nationalId.trim(), economicId = economicId.trim(), address = address.trim(), phone = phone.trim()))
                                        db.moadianDetailsDao().upsert(details(id).copy(encryptedPrivateKey = vault.encrypt(id, privateKey), keyCheckStatus = "NEEDS_MATCH"))
                                    }
                                    onChanged()
                                    detailRows = db.moadianDetailsDao().all()
                                    close()
                                    onNotice("مودی ثبت شد.")
                                } else {
                                    val old = db.companyDao().byId(selectedId ?: -1) ?: throw IllegalStateException("مودی یافت نشد.")
                                    db.withTransaction {
                                        db.companyDao().update(old.copy(name = name.trim(), nationalId = nationalId.trim(), economicId = economicId.trim(), address = address.trim(), phone = phone.trim()))
                                        val existing = db.moadianDetailsDao().byCompany(old.id)
                                        val encrypted = if (privateKey.isNotBlank()) vault.encrypt(old.id, privateKey) else storedKey
                                        db.moadianDetailsDao().upsert(details(old.id).copy(encryptedPrivateKey = encrypted, keyCheckStatus = if (encrypted != null) "NEEDS_MATCH" else "UNCHECKED", inactive = existing?.inactive ?: false, logoUri = existing?.logoUri.orEmpty()))
                                    }
                                    onChanged()
                                    detailRows = db.moadianDetailsDao().all()
                                    close()
                                    onNotice("تغییرات ذخیره شد.")
                                }
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "ذخیره انجام نشد؛ اطلاعات فرم حفظ شد."; pending = false }
                            finally { busy = false }
                        }
                    }) { Text("تأیید") }
                    OutlinedButton(onClick = { pending = false }) { Text("بازگشت") }
                }
            }
            TextButton(onClick = ::close) { Text("انصراف") }
            }
        }
    }
}
