package ir.saba.invoice.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ir.saba.invoice.data.*
import ir.saba.invoice.domain.InvoiceCalculator
import ir.saba.invoice.domain.InvoiceInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val LinesSaver = listSaver<List<InvoiceLine>, String>(
    save = { rows -> rows.flatMap { listOf(it.itemCode, it.description, it.quantity.toString(), it.unitPriceRial.toString(), it.discountRial.toString(), it.taxRatePercent.toString()) } },
    restore = { values -> values.chunked(6).map { InvoiceLine(invoiceId = 0, itemCode = it[0], description = it[1], quantity = it[2].toLong(), unitPriceRial = it[3].toLong(), discountRial = it[4].toLong(), taxRatePercent = it[5].toInt()) } }
)

internal fun rial(amount: Long): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("fa-IR")).format(amount) + " ریال"
internal fun invoiceStatus(status: String) = when (status) {
    "DRAFT" -> "پیش‌نویس"
    "FINALIZED" -> "نهایی‌شده محلی"
    else -> status
}

@Composable
fun InvoiceScreen(
    db: SabaDatabase,
    companyId: Long,
    customers: List<Customer>,
    invoices: List<Invoice>,
    onChanged: suspend () -> Unit,
    onNotice: (String) -> Unit,
    onEditorActive: (Boolean) -> Unit
) {
    val repository = remember(db) { InvoiceRepository(db) }
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf("list") }
    var invoiceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var number by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var customerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var lines by rememberSaveable(stateSaver = LinesSaver) { mutableStateOf(emptyList<InvoiceLine>()) }
    var search by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("ALL") }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var details by remember { mutableStateOf<InvoiceDetails?>(null) }
    var lineDialog by rememberSaveable { mutableStateOf(false) }
    var lineIndex by rememberSaveable { mutableIntStateOf(-1) }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var actionId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(mode, busy) { onEditorActive(mode == "edit" || busy) }
    DisposableEffect(Unit) { onDispose { onEditorActive(false) } }

    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = ""
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error = if (e is ArithmeticException) "مبلغ فاکتور بیش از محدوده مجاز است." else e.message ?: "عملیات انجام نشد؛ دوباره تلاش کنید."
                onNotice(error)
            } finally { busy = false }
        }
    }

    LaunchedEffect(mode, invoiceId) {
        if (mode == "details" && invoiceId != null) {
            details = null
            busy = true
            try { details = repository.detailsForCompany(companyId, invoiceId!!) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "جزئیات فاکتور بارگذاری نشد." }
            finally { busy = false }
        }
    }

    BackHandler(enabled = mode != "list") {
        if (!busy) {
            if (mode == "edit") confirmation = "leave" else { mode = "list"; error = "" }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (error.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (mode) {
            "list" -> {
                Text("فاکتورهای شرکت", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(enabled = !busy, onClick = { perform { onChanged() } }) { Text("تازه‌کردن فهرست") }
                Button(enabled = !busy && customers.isNotEmpty(), onClick = {
                    invoiceId = null; number = ""; date = ""; customerId = customers.firstOrNull()?.id
                    lines = emptyList(); error = ""; mode = "edit"
                }) { Text("صدور فاکتور جدید") }
                if (customers.isEmpty()) Text("برای صدور فاکتور ابتدا مشتری این شرکت را ثبت کنید.")
                OutlinedTextField(search, { search = it }, label = { Text("جستجوی شماره فاکتور یا مشتری") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "همه", "DRAFT" to "پیش‌نویس", "FINALIZED" to "نهایی").forEach { (value, title) ->
                        FilterChip(selected = status == value, onClick = { status = value }, label = { Text(title) })
                    }
                }
                val needle = InvoiceInput.digits(search.trim())
                val filtered = invoices.filter { invoice ->
                    (status == "ALL" || invoice.status == status) &&
                        (needle.isEmpty() || InvoiceInput.digits(invoice.invoiceNumber).contains(needle, true) ||
                            customers.firstOrNull { it.id == invoice.customerId }?.name?.contains(search.trim(), true) == true)
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (filtered.isEmpty()) item { Text(if (invoices.isEmpty()) "هنوز فاکتوری ثبت نشده است." else "فاکتوری مطابق جستجو پیدا نشد.") }
                    items(filtered, key = { it.id }) { invoice ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("فاکتور ${invoice.invoiceNumber}", fontWeight = FontWeight.Bold)
                                Text(customers.firstOrNull { it.id == invoice.customerId }?.name ?: "مشتری شماره ${invoice.customerId}")
                                Text("${invoice.issueDate} • ${invoiceStatus(invoice.status)}")
                                Text(rial(invoice.totalAmountRial), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                OutlinedButton(enabled = !busy, onClick = { invoiceId = invoice.id; error = ""; mode = "details" }) { Text("مشاهده جزئیات") }
                            }
                        }
                    }
                }
            }
            "details" -> {
                val loaded = details
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { mode = "list"; error = "" }) { Text("بازگشت به فهرست") }
                    if (loaded != null) {
                        val invoice = loaded.invoice
                        Text("فاکتور ${invoice.invoiceNumber}", style = MaterialTheme.typography.titleLarge)
                        Text("مشتری: ${customers.firstOrNull { it.id == invoice.customerId }?.name ?: invoice.customerId}")
                        Text("تاریخ: ${invoice.issueDate}")
                        Text("وضعیت: ${invoiceStatus(invoice.status)}")
                        loaded.lines.forEachIndexed { index, row -> LineCard(index, row) }
                        TotalsCard(loaded.lines, invoice)
                        Text("این سند در دستگاه ثبت شده و به سامانه مؤدیان ارسال نشده است.", style = MaterialTheme.typography.bodySmall)
                        if (invoice.status == "DRAFT") {
                            Button(enabled = !busy, onClick = {
                                number = invoice.invoiceNumber; date = invoice.issueDate; customerId = invoice.customerId
                                lines = loaded.lines.map { it.copy(id = 0, invoiceId = 0) }; error = ""; mode = "edit"
                            }) { Text("ویرایش پیش‌نویس") }
                            Button(enabled = !busy, onClick = { actionId = invoice.id; confirmation = "finalize" }) { Text("نهایی‌سازی و مصرف یک سهمیه") }
                            TextButton(enabled = !busy, onClick = { actionId = invoice.id; confirmation = "delete" }) { Text("حذف پیش‌نویس", color = MaterialTheme.colorScheme.error) }
                        } else Text("سند نهایی‌شده قابل ویرایش یا حذف نیست.")
                    } else if (!busy) OutlinedButton(onClick = { mode = "list" }) { Text("بازگشت و تلاش مجدد") }
                    Spacer(Modifier.height(16.dp))
                }
            }
            "edit" -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (invoiceId == null) "صدور فاکتور جدید" else "ویرایش پیش‌نویس", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("مبالغ به ریال؛ تعداد در این نسخه فقط عدد صحیح است.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(number, { number = it }, enabled = !busy, label = { Text("شماره فاکتور") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(date, { date = it }, enabled = !busy, label = { Text("تاریخ شمسی، نمونه: ۱۴۰۵/۰۷/۰۲") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(enabled = !busy, onClick = { expanded = true }) { Text("مشتری: " + (customers.firstOrNull { it.id == customerId }?.name ?: "انتخاب کنید")) }
                        DropdownMenu(expanded, { expanded = false }) {
                            customers.forEach { customer -> DropdownMenuItem(text = { Text(customer.name) }, onClick = { customerId = customer.id; expanded = false }) }
                        }
                    }
                    Text("اقلام فاکتور (${lines.size})", style = MaterialTheme.typography.titleMedium)
                    lines.forEachIndexed { index, row ->
                        LineCard(index, row) {
                            OutlinedButton(enabled = !busy, onClick = { lineIndex = index; lineDialog = true }) { Text("ویرایش ردیف") }
                            TextButton(enabled = !busy, onClick = { lines = lines.filterIndexed { i, _ -> i != index } }) { Text("حذف ردیف") }
                        }
                    }
                    OutlinedButton(enabled = !busy, onClick = { lineIndex = -1; lineDialog = true }) { Text("افزودن کالا یا خدمت") }
                    TotalsCard(lines)
                    Button(enabled = !busy && lines.isNotEmpty(), onClick = {
                        perform {
                            val selectedCustomer = customerId ?: throw IllegalArgumentException("مشتری را انتخاب کنید.")
                            val savedId = repository.saveDraft(companyId, selectedCustomer, number, date, lines, invoiceId)
                            // Retain identity before refreshing, so a retry never creates a second draft.
                            invoiceId = savedId
                            onChanged()
                            mode = "details"
                            onNotice("پیش‌نویس ذخیره شد؛ هنوز نهایی یا ارسال نشده است.")
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "در حال ذخیره…" else "ذخیره پیش‌نویس") }
                    OutlinedButton(enabled = !busy, onClick = { confirmation = "leave" }, modifier = Modifier.fillMaxWidth()) { Text("انصراف از ویرایش") }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (lineDialog) LineEditor(
        initial = lines.getOrNull(lineIndex),
        onDismiss = { lineDialog = false },
        onSave = { row ->
            val next = lines.toMutableList()
            if (lineIndex < 0) next.add(row) else next[lineIndex] = row
            // Validate the whole invoice as well as the line, including total overflow.
            InvoiceCalculator.calculate(next)
            lines = next.toList(); lineDialog = false
        }
    )

    if (confirmation.isNotEmpty()) AlertDialog(
        onDismissRequest = { if (!busy) confirmation = "" },
        title = { Text(when (confirmation) { "delete" -> "حذف پیش‌نویس؟"; "finalize" -> "نهایی‌سازی فاکتور؟"; else -> "خروج از ویرایش؟" }) },
        text = { Text(when (confirmation) {
            "delete" -> "پیش‌نویس و ردیف‌های آن حذف می‌شوند. سهمیه‌ای مصرف نمی‌شود."
            "finalize" -> "یک سهمیه مصرف می‌شود و سند دیگر قابل ویرایش نیست. این کار ارسال به مؤدیان نیست."
            else -> "تغییرات ذخیره‌نشده کنار گذاشته می‌شوند."
        }) },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val requested = confirmation
            if (requested == "leave") { confirmation = ""; mode = if (invoiceId == null) "list" else "details"; error = "" }
            else perform {
                val id = actionId ?: throw IllegalArgumentException("فاکتور را دوباره انتخاب کنید.")
                if (requested == "delete") repository.deleteDraft(companyId, id)
                else { repository.detailsForCompany(companyId, id); repository.finalizeDraft(id) }
                confirmation = ""
                if (requested == "delete") { details = null; mode = "list" }
                else details = repository.detailsForCompany(companyId, id)
                onChanged()
                onNotice(if (requested == "delete") "پیش‌نویس حذف شد." else "فاکتور محلی نهایی شد؛ یک سهمیه مصرف شد و ارسال واقعی انجام نشد.")
            }
        }) { Text("تأیید") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmation = "" }) { Text("بازگشت") } }
    )
}

@Composable
private fun LineCard(index: Int, row: InvoiceLine, actions: (@Composable ColumnScope.() -> Unit)? = null) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${index + 1}. ${row.description}", fontWeight = FontWeight.SemiBold)
            if (row.itemCode.isNotBlank()) Text("شناسه کالا/خدمت: ${row.itemCode}")
            Text("تعداد: ${row.quantity} • قیمت واحد: ${rial(row.unitPriceRial)}")
            Text("تخفیف: ${rial(row.discountRial)} • مالیات: ${row.taxRatePercent}٪")
            Text("خالص ردیف: ${rial(Math.subtractExact(Math.multiplyExact(row.quantity, row.unitPriceRial), row.discountRial))}")
            actions?.invoke(this)
        }
    }
}

@Composable
private fun TotalsCard(lines: List<InvoiceLine>, stored: Invoice? = null) {
    val result = remember(lines) { if (lines.isEmpty()) null else runCatching { InvoiceCalculator.calculate(lines) }.getOrNull() }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("جمع فاکتور", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (stored != null) {
                Text("خالص: ${rial(stored.totalAmountRial - stored.taxAmountRial)}")
                Text("مالیات: ${rial(stored.taxAmountRial)}")
                Text("قابل پرداخت: ${rial(stored.totalAmountRial)}", fontWeight = FontWeight.Bold)
            } else if (result != null) {
                Text("خالص پس از تخفیف: ${rial(result.netAmountRial)}")
                Text("مالیات: ${rial(result.taxAmountRial)}")
                Text("قابل پرداخت: ${rial(result.totalAmountRial)}", fontWeight = FontWeight.Bold)
            } else Text(if (lines.isEmpty()) "برای مشاهده جمع، یک ردیف اضافه کنید." else "مبالغ فاکتور معتبر نیستند.")
            Text("مالیات پس از جمع ردیف‌ها به ریال گرد می‌شود.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LineEditor(initial: InvoiceLine?, onDismiss: () -> Unit, onSave: (InvoiceLine) -> Unit) {
    var code by rememberSaveable { mutableStateOf(initial?.itemCode.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(initial?.quantity?.toString() ?: "1") }
    var price by rememberSaveable { mutableStateOf(initial?.unitPriceRial?.toString().orEmpty()) }
    var discount by rememberSaveable { mutableStateOf(initial?.discountRial?.toString() ?: "0") }
    var tax by rememberSaveable { mutableStateOf(initial?.taxRatePercent?.toString() ?: "10") }
    var error by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(12.dp).imePadding(),
        title = { Text(if (initial == null) "افزودن ردیف" else "ویرایش ردیف") },
        text = {
            Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(description, { description = it }, label = { Text("شرح کالا یا خدمت") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(code, { code = it }, label = { Text("شناسه کالا/خدمت (اختیاری برای ثبت محلی)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumberField(quantity, { quantity = it }, "تعداد صحیح")
                NumberField(price, { price = it }, "قیمت واحد به ریال")
                NumberField(discount, { discount = it }, "تخفیف کل این ردیف به ریال")
                NumberField(tax, { tax = it }, "درصد مالیات، صفر تا صد")
            }
        },
        confirmButton = { TextButton(onClick = {
            try {
                val rate = InvoiceInput.wholeNumber(tax, "نرخ مالیات")
                require(rate in 0..100) { "نرخ مالیات باید بین صفر و صد باشد." }
                val row = InvoiceLine(invoiceId = 0, itemCode = InvoiceInput.digits(code.trim()), description = description.trim(),
                    quantity = InvoiceInput.wholeNumber(quantity, "تعداد"), unitPriceRial = InvoiceInput.wholeNumber(price, "قیمت"),
                    discountRial = InvoiceInput.wholeNumber(discount, "تخفیف"), taxRatePercent = rate.toInt())
                InvoiceCalculator.calculate(listOf(row))
                onSave(row)
            } catch (e: ArithmeticException) {
                error = "مبلغ ردیف یا جمع فاکتور بیش از محدوده مجاز است."
                scope.launch { scroll.animateScrollTo(0) }
            } catch (e: IllegalArgumentException) {
                error = e.message ?: "اطلاعات ردیف معتبر نیست."
                scope.launch { scroll.animateScrollTo(0) }
            }
        }) { Text("ثبت ردیف") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr), modifier = Modifier.fillMaxWidth())
}
