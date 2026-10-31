package ir.saba.invoice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.*
import ir.saba.invoice.domain.InvoiceInput
import kotlinx.coroutines.launch

/** Full customer/supplier form, preserving Customer rows used by existing invoices. */
@Composable
fun CustomerManagementScreen(db: SabaDatabase, companyId: Long,
    customers: List<Customer>, onChanged: suspend () -> Unit,
    onNotice: (String) -> Unit, onEditing: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var profiles by remember(companyId) { mutableStateOf<List<CustomerProfile>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var editorId by remember(companyId) { mutableStateOf<Long?>(null) }
    var editorOpen by remember(companyId) { mutableStateOf(false) }
    var search by remember(companyId) { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(companyId, customers.size) {
        try { profiles = db.customerProfileDao().forCompany(companyId) } catch (_: Exception) { onNotice("بارگذاری اطلاعات تکمیلی مشتری انجام نشد.") }
    }
    DisposableEffect(editorOpen) { onEditing(editorOpen); onDispose { onEditing(false) } }
    val current = customers.firstOrNull { it.id == editorId }
    val oldProfile = profiles.firstOrNull { it.customerId == editorId }
    var name by remember(editorOpen, editorId) { mutableStateOf(current?.name.orEmpty()) }
    var national by remember(editorOpen, editorId) { mutableStateOf(current?.nationalId.orEmpty()) }
    var economic by remember(editorOpen, editorId) { mutableStateOf(current?.economicId.orEmpty()) }
    var postal by remember(editorOpen, editorId) { mutableStateOf(current?.postalCode.orEmpty()) }
    var address by remember(editorOpen, editorId) { mutableStateOf(current?.address.orEmpty()) }
    var phone by remember(editorOpen, editorId) { mutableStateOf(current?.phone.orEmpty()) }
    var type by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.accountType ?: "NATURAL") }
    var active by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.active ?: true) }
    var isCustomer by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.isCustomer ?: true) }
    var isSupplier by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.isSupplier ?: false) }
    var mobile by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.mobile.orEmpty()) }
    var fax by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.fax.orEmpty()) }
    var email by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.email.orEmpty()) }
    var province by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.province.orEmpty()) }
    var county by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.county.orEmpty()) }
    var registration by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.registrationNumber.orEmpty()) }
    var birthCertificate by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.birthCertificateNumber.orEmpty()) }
    if (!editorOpen) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("مشتریان و تأمین‌کنندگان", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { editorId = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("ثبت مشتری / تأمین‌کننده جدید") }
            OutlinedTextField(search, { search = it }, label = { Text("جستجوی نام، شناسه ملی یا کد اقتصادی") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            val rows = customers.filter {
                it.name.contains(search, true) || it.nationalId.contains(InvoiceInput.digits(search)) || it.economicId.contains(InvoiceInput.digits(search))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (rows.isEmpty()) item { Text("موردی یافت نشد.") }
                items(rows, key = { it.id }) { item ->
                    ElevatedCard(Modifier.fillMaxWidth().clickable { editorId = item.id; editorOpen = true }) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            val profile = profiles.firstOrNull { it.customerId == item.id }
                            Text(if (profile?.isSupplier == true) "مشتری / تأمین‌کننده" else "مشتری", style = MaterialTheme.typography.labelSmall)
                            Text("شناسه: ${item.nationalId.ifBlank { "ثبت نشده" }}")
                            if (profile?.active == false) Text("غیرفعال", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { editorId = item.id; editorOpen = true }) { Text("مشاهده و ویرایش") }
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (editorId == null) "ثبت مشتری / تأمین‌کننده" else "ویرایش مشخصات", style = MaterialTheme.typography.titleLarge)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            @Composable fun Toggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
                Row { Checkbox(checked = value, onCheckedChange = change, enabled = !busy); Text(label, modifier = Modifier.padding(top = 12.dp)) }
            }
            Toggle("فعال", active) { active = it }
            Toggle("مشتری است", isCustomer) { isCustomer = it }
            Toggle("تأمین‌کننده است", isSupplier) { isSupplier = it }
            Text("نوع طرف حساب", style = MaterialTheme.typography.titleSmall)
            listOf("NATURAL" to "شخص حقیقی", "LEGAL" to "شخص حقوقی", "PARTNERSHIP" to "مشارکت مدنی", "FOREIGN" to "اتباع غیرایرانی", "OTHER" to "سایر").chunked(2).forEach { pair ->
                Row { pair.forEach { (value, title) ->
                    FilterChip(selected = type == value, onClick = { type = value }, label = { Text(title) }, modifier = Modifier.padding(end = 6.dp))
                } }
            }
            @Composable fun Field(label: String, value: String, change: (String) -> Unit) {
                OutlinedTextField(value, change, enabled = !busy, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Field("نام / عنوان *", name) { name = it }
            Field(if (type == "LEGAL") "شناسه ملی ۱۱ رقمی *" else "کد ملی / شناسه هویتی", national) { national = InvoiceInput.digits(it) }
            Field("شماره اقتصادی", economic) { economic = InvoiceInput.digits(it) }
            Field("تلفن", phone) { phone = InvoiceInput.digits(it) }
            Field("فکس", fax) { fax = InvoiceInput.digits(it) }
            Field("موبایل", mobile) { mobile = InvoiceInput.digits(it) }
            Field("شماره ثبت", registration) { registration = it }
            Field("شماره شناسنامه", birthCertificate) { birthCertificate = it }
            Field("ایمیل", email) { email = it.trim() }
            Field("کد پستی", postal) { postal = InvoiceInput.digits(it) }
            Field("استان", province) { province = it }
            Field("شهرستان", county) { county = it }
            OutlinedTextField(address, { address = it }, enabled = !busy, label = { Text("آدرس") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button(enabled = !busy, onClick = {
                busy = true; error = ""
                scope.launch {
                    try {
                        require(name.isNotBlank()) { "نام الزامی است." }
                        require(isCustomer || isSupplier) { "حداقل یکی از گزینه‌های مشتری یا تأمین‌کننده را انتخاب کنید." }
                        require(national.isBlank() || national.length in 10..14) { "طول شناسه هویتی نامعتبر است." }
                        require(type != "LEGAL" || national.length == 11) { "شناسه ملی شخص حقوقی باید ۱۱ رقم باشد." }
                        require(postal.isBlank() || postal.length == 10) { "کد پستی باید ۱۰ رقم باشد." }
                        require(email.isBlank() || (email.contains("@") && email.substringAfter("@").contains("."))) { "ایمیل معتبر نیست." }
                        val id = if (editorId == null) db.customerDao().insert(Customer(
                            companyId = companyId, name = name.trim(), nationalId = national, economicId = economic,
                            postalCode = postal, address = address.trim(), phone = phone)) else {
                            val old = db.customerDao().byId(editorId!!) ?: error("مشتری یافت نشد.")
                            require(old.companyId == companyId)
                            db.customerDao().update(old.copy(name = name.trim(), nationalId = national, economicId = economic,
                                postalCode = postal, address = address.trim(), phone = phone))
                            old.id
                        }
                        db.customerProfileDao().upsert(CustomerProfile(customerId = id, companyId = companyId,
                            accountType = type, active = active, isCustomer = isCustomer, isSupplier = isSupplier,
                            mobile = mobile, fax = fax, email = email, province = province, county = county,
                            registrationNumber = registration, birthCertificateNumber = birthCertificate))
                        profiles = db.customerProfileDao().forCompany(companyId)
                        onChanged()
                        editorOpen = false
                        onNotice("اطلاعات طرف حساب ذخیره شد.")
                    } catch (e: Exception) { error = e.message ?: "خطا در ذخیره اطلاعات" }
                    finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره اطلاعات") }
            OutlinedButton(enabled = !busy, onClick = { editorOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("انصراف") }
        }
    }
}
