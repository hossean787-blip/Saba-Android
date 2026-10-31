package ir.saba.invoice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
            Button(onClick = { editorId = null; editorOpen = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("جدید") }
            OutlinedTextField(search, { search = it }, placeholder = { Text("جست‌وجوی مشتریان و تأمین‌کنندگان") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp))
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
            @Composable fun Field(label: String, value: String, help: String = "", modifier: Modifier = Modifier.fillMaxWidth(), change: (String) -> Unit) {
                var showHelp by remember { mutableStateOf(false) }
                OutlinedTextField(value, change, enabled = !busy,
                    placeholder = { Text(label, maxLines = 1) },
                    trailingIcon = if (help.isEmpty()) null else { {
                        IconButton(onClick = { showHelp = true }) {
                            Icon(Icons.Default.Info, contentDescription = "راهنمای $label", tint = Color(0xFF12819B))
                        }
                    } },
                    shape = RoundedCornerShape(14.dp), modifier = modifier, singleLine = true)
                if (showHelp) AlertDialog(onDismissRequest = { showHelp = false },
                    title = { Text(label) }, text = { Text(help) },
                    confirmButton = { TextButton(onClick = { showHelp = false }) { Text("متوجه شدم") } })
            }
            Field("نام / عنوان *", name) { name = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(if (type == "LEGAL") "شناسه ملی *" else "کد ملی / شناسه", national,
                    "شناسه هویتی مرتبط با نوع طرف حساب را وارد کنید.", Modifier.weight(1f)) { national = InvoiceInput.digits(it) }
                Field("شماره اقتصادی", economic, "شماره اقتصادی ثبت‌شده برای طرف حساب.", Modifier.weight(1f)) { economic = InvoiceInput.digits(it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("تلفن", phone, modifier = Modifier.weight(1f)) { phone = InvoiceInput.digits(it) }
                Field("فکس", fax, modifier = Modifier.weight(1f)) { fax = InvoiceInput.digits(it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("موبایل", mobile, modifier = Modifier.weight(1f)) { mobile = InvoiceInput.digits(it) }
                Field("کد پستی", postal, "کد پستی ده رقمی محل طرف حساب.", Modifier.weight(1f)) { postal = InvoiceInput.digits(it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("شماره ثبت", registration, modifier = Modifier.weight(1f)) { registration = it }
                Field("شماره شناسنامه", birthCertificate, modifier = Modifier.weight(1f)) { birthCertificate = it }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("استان", province, modifier = Modifier.weight(1f)) { province = it }
                Field("شهرستان", county, modifier = Modifier.weight(1f)) { county = it }
            }
            Field("ایمیل", email) { email = it.trim() }
            OutlinedTextField(address, { address = it }, enabled = !busy,
                placeholder = { Text("آدرس") }, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("ذخیره اطلاعات") }
            OutlinedButton(enabled = !busy, onClick = { editorOpen = false }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("انصراف") }
            }
        }
    }
}
