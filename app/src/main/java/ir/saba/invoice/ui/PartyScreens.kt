package ir.saba.invoice.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.*
import ir.saba.invoice.domain.InvoiceInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun CompaniesScreen(db: SabaDatabase, companies: List<Company>, onChanged: suspend () -> Unit,
                    onSelect: (Long) -> Unit, onNotice: (String) -> Unit, onEditing: (Boolean) -> Unit) {
    var editor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorSession by rememberSaveable { mutableIntStateOf(0) }
    val company = companies.firstOrNull { it.id == editingId }
    LaunchedEffect(editor) { onEditing(editor) }
    DisposableEffect(Unit) { onDispose { onEditing(false) } }
    if (editor) {
        key(editorSession) {
            PartyEditor(company = company, customer = null, isCompany = true, ownerEditable = editingId == null, onClose = { editor = false }, onSave = { name, national, economic, _, address, phone, purchaser ->
                val normalized = InvoiceInput.digits(national).trim()
                require(normalized.length in 10..11 && normalized.all { it in '0'..'9' }) { "شناسه ملی شرکت/مؤدی باید ۱۰ یا ۱۱ رقم باشد." }
                require(companies.none { it.id != editingId && InvoiceInput.digits(it.nationalId) == normalized }) { "این شناسه ملی قبلاً ثبت شده است." }
                if (editingId == null) editingId = db.companyDao().insert(Company(purchaserId = purchaser.trim(), name = name, nationalId = normalized, economicId = economic, address = address, phone = phone))
                else {
                    val old = db.companyDao().byId(editingId!!) ?: error("شرکت پیدا نشد.")
                    db.companyDao().update(old.copy(name = name, nationalId = normalized, economicId = economic, address = address, phone = phone))
                }
                refreshAfterSave(onChanged); editor = false; onNotice("اطلاعات شرکت ذخیره شد.")
            })
        }
    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("شرکت‌ها و مؤدیان", style = MaterialTheme.typography.titleLarge)
        Button(onClick = { editingId = null; editorSession++; editor = true }) { Text("افزودن شرکت / مؤدی") }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (companies.isEmpty()) item { Text("برای شروع، شرکت یا مؤدی خود را ثبت کنید.") }
            items(companies, key = { it.id }) { row ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.name, style = MaterialTheme.typography.titleMedium)
                        Text("شناسه ملی: ${row.nationalId}")
                        if (row.economicId.isNotBlank()) Text("شناسه اقتصادی: ${row.economicId}")
                        OutlinedButton(onClick = { onSelect(row.id) }) { Text("انتخاب شرکت و مشاهده مشتریان") }
                        TextButton(onClick = { editingId = row.id; editorSession++; editor = true }) { Text("ویرایش اطلاعات") }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomersScreen(db: SabaDatabase, companyId: Long, customers: List<Customer>, onChanged: suspend () -> Unit,
                    onNotice: (String) -> Unit, onEditing: (Boolean) -> Unit) {
    var editor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorSession by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(editor) { onEditing(editor) }
    DisposableEffect(Unit) { onDispose { onEditing(false) } }
    if (editor) key(editorSession) {
        PartyEditor(company = null, customer = customers.firstOrNull { it.id == editingId }, isCompany = false,
            onClose = { editor = false }, onSave = { name, national, economic, postal, address, phone, _ ->
                require(national.isEmpty() || (national.length in 10..11 && national.all { it in '0'..'9' })) { "شناسه ملی مشتری باید ۱۰ یا ۱۱ رقم باشد یا خالی بماند." }
                require(postal.isEmpty() || (postal.length == 10 && postal.all { it in '0'..'9' })) { "کد پستی باید ۱۰ رقم باشد یا خالی بماند." }
                if (editingId == null) editingId = db.customerDao().insert(Customer(companyId = companyId, name = name, nationalId = national, economicId = economic, postalCode = postal, address = address, phone = phone))
                else {
                    val old = db.customerDao().byId(editingId!!) ?: error("مشتری پیدا نشد.")
                    require(old.companyId == companyId) { "مشتری متعلق به این شرکت نیست." }
                    db.customerDao().update(old.copy(name = name, nationalId = national, economicId = economic, postalCode = postal, address = address, phone = phone))
                }
                refreshAfterSave(onChanged); editor = false; onNotice("اطلاعات مشتری ذخیره شد.")
            })
    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("مشتریان شرکت", style = MaterialTheme.typography.titleLarge)
        Button(onClick = { editingId = null; editorSession++; editor = true }) { Text("افزودن مشتری") }
        OutlinedTextField(search, { search = it }, label = { Text("جستجوی نام یا شناسه ملی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val filtered = customers.filter { it.name.contains(search.trim(), true) || InvoiceInput.digits(it.nationalId).contains(InvoiceInput.digits(search.trim())) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (filtered.isEmpty()) item { Text(if (customers.isEmpty()) "هنوز مشتری ثبت نشده است." else "مشتری پیدا نشد.") }
            items(filtered, key = { it.id }) { row ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.name, style = MaterialTheme.typography.titleMedium)
                        Text("شناسه ملی: ${row.nationalId.ifBlank { "ثبت نشده" }}")
                        if (row.phone.isNotBlank()) Text("تلفن: ${row.phone}")
                        TextButton(onClick = { editingId = row.id; editorSession++; editor = true }) { Text("مشاهده و ویرایش") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartyEditor(company: Company?, customer: Customer?, isCompany: Boolean, ownerEditable: Boolean = false, onClose: () -> Unit,
    onSave: suspend (String, String, String, String, String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(company?.name ?: customer?.name.orEmpty()) }
    var national by rememberSaveable { mutableStateOf(company?.nationalId ?: customer?.nationalId.orEmpty()) }
    var economic by rememberSaveable { mutableStateOf(company?.economicId ?: customer?.economicId.orEmpty()) }
    var postal by rememberSaveable { mutableStateOf(customer?.postalCode.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(company?.address ?: customer?.address.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(company?.phone ?: customer?.phone.orEmpty()) }
    var purchaser by rememberSaveable { mutableStateOf(company?.purchaserId ?: "owner-1") }
    var error by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var leave by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    BackHandler { if (!busy) leave = true }
    Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (isCompany) "اطلاعات شرکت / مؤدی" else "اطلاعات مشتری", style = MaterialTheme.typography.titleLarge)
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        OutlinedTextField(name, { name = it }, enabled = !busy, label = { Text("نام") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(national, { national = it }, enabled = !busy, label = { Text(if (isCompany) "شناسه ملی" else "شناسه ملی (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(economic, { economic = it }, enabled = !busy, label = { Text("شناسه اقتصادی (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (!isCompany) OutlinedTextField(postal, { postal = it }, enabled = !busy, label = { Text("کد پستی (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(address, { address = it }, enabled = !busy, label = { Text("نشانی (اختیاری)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, enabled = !busy, label = { Text("تلفن (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (isCompany) {
            OutlinedTextField(purchaser, { purchaser = it }, enabled = !busy && ownerEditable, label = { Text("شناسه خریدار اصلی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!ownerEditable) Text("مالک شرکت برای حفظ سوابق سهمیه از این فرم تغییر نمی‌کند.", style = MaterialTheme.typography.bodySmall)
        }
        Button(enabled = !busy, onClick = {
            busy = true; error = ""
            scope.launch {
                try {
                    require(name.isNotBlank()) { "نام الزامی است." }
                    require(!isCompany || purchaser.isNotBlank()) { "شناسه خریدار اصلی الزامی است." }
                    val normalizedEconomic = InvoiceInput.digits(economic.trim())
                    require(normalizedEconomic.isEmpty() || normalizedEconomic.all { it in '0'..'9' }) { "شناسه اقتصادی باید فقط رقم باشد." }
                    onSave(name.trim(), InvoiceInput.digits(national.trim()), normalizedEconomic, InvoiceInput.digits(postal.trim()), address.trim(), InvoiceInput.digits(phone.trim()), purchaser.trim())
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    error = if (e is android.database.sqlite.SQLiteConstraintException) "شناسه تکراری یا اطلاعات وابسته نامعتبر است." else e.message ?: "ذخیره انجام نشد."
                    scroll.animateScrollTo(0)
                }
                finally { busy = false }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "در حال ذخیره…" else "ذخیره اطلاعات") }
        OutlinedButton(enabled = !busy, onClick = { leave = true }, modifier = Modifier.fillMaxWidth()) { Text("انصراف") }
        Spacer(Modifier.height(24.dp))
    }
    if (leave) AlertDialog(onDismissRequest = { leave = false }, title = { Text("خروج از فرم؟") }, text = { Text("تغییرات ذخیره‌نشده کنار گذاشته می‌شوند.") },
        confirmButton = { TextButton(onClick = { leave = false; onClose() }) { Text("خروج") } },
        dismissButton = { TextButton(onClick = { leave = false }) { Text("ادامه ویرایش") } })
}

private suspend fun refreshAfterSave(refresh: suspend () -> Unit) {
    try { refresh() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { throw IllegalStateException("اطلاعات ذخیره شد، اما فهرست تازه نشد؛ برای تلاش مجدد ذخیره را بزنید.", e) }
}
