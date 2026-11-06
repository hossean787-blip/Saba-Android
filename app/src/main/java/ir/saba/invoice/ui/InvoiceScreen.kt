package ir.saba.invoice.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ir.saba.invoice.data.*
import ir.saba.invoice.domain.InvoiceCalculator
import ir.saba.invoice.domain.InvoiceInput
import ir.saba.invoice.domain.InvoiceIdentifierPrefill
import ir.saba.invoice.domain.InvoiceLineAmounts
import ir.saba.moadian.MoadianMeasurementUnits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val LinesSaver = listSaver<List<InvoiceLine>, String>(
    save = { rows -> listOf("__SURA_INVOICE_LINES_V2__") + rows.flatMap {
        listOf(it.itemCode, it.description, it.quantity.toString(), it.unitPriceRial.toString(),
            it.discountRial.toString(), it.taxRatePercent.toString(), it.unitCode, it.quantityExact, it.vatRateExact)
    } },
    restore = { values ->
        val versioned = values.firstOrNull() == "__SURA_INVOICE_LINES_V2__"
        val data = if (versioned) values.drop(1) else values
        val width = if (versioned) 9 else 6
        data.chunked(width).map { item ->
            InvoiceLine(invoiceId = 0, itemCode = item[0], description = item[1],
                quantity = item[2].toLong(), unitPriceRial = item[3].toLong(),
                discountRial = item[4].toLong(), taxRatePercent = item[5].toInt(),
                unitCode = item.getOrElse(6) { "" }, quantityExact = item.getOrElse(7) { "" },
                vatRateExact = item.getOrElse(8) { "" })
        }
    }
)

internal fun rial(amount: Long): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("fa-IR")).format(amount) + " ریال"
internal fun invoiceStatus(status: String) = when (status) {
    "DRAFT" -> "پیش‌نویس"
    "FINALIZED" -> "نهایی‌شده محلی"
    "CANCELLED" -> "لغوشده"
    else -> status
}

@Composable
fun InvoiceScreen(
    db: SabaDatabase,
    ownerId: String,
    companyId: Long,
    customers: List<Customer>,
    invoices: List<Invoice>,
    onChanged: suspend () -> Unit,
    onNotice: (String) -> Unit,
    onEditorActive: (Boolean) -> Unit,
    availableCharge: Long = 0,
    accessToken: String = "",
    onNeedCharge: () -> Unit = {},
    newInvoiceRequest: Int = 0,
    onNewInvoiceRequestHandled: () -> Unit = {},
    onOpenOfficialSubmission: () -> Unit = {},
    creditGateway: InvoiceCreditGateway? = null
) {
    val states = rememberSaveableStateHolder()
    states.SaveableStateProvider("${ir.saba.invoice.security.AccountScope.of(ownerId).storageKey}:$companyId") {
        key(ownerId, companyId) { InvoiceScreenContent(db, ownerId, companyId, customers, invoices, onChanged, onNotice, onEditorActive, availableCharge, accessToken, onNeedCharge, newInvoiceRequest, onNewInvoiceRequestHandled, onOpenOfficialSubmission, creditGateway) }
    }
}

@Composable
private fun InvoiceScreenContent(
    db: SabaDatabase,
    ownerId: String,
    companyId: Long,
    customers: List<Customer>,
    invoices: List<Invoice>,
    onChanged: suspend () -> Unit,
    onNotice: (String) -> Unit,
    onEditorActive: (Boolean) -> Unit,
    availableCharge: Long = 0,
    accessToken: String = "",
    onNeedCharge: () -> Unit = {},
    newInvoiceRequest: Int = 0,
    onNewInvoiceRequestHandled: () -> Unit = {},
    onOpenOfficialSubmission: () -> Unit = {},
    creditGateway: InvoiceCreditGateway? = null
) {
    val account = remember(ownerId) { ir.saba.invoice.security.AccountScope.of(ownerId) }
    val activeGateway = remember(account,creditGateway) { creditGateway ?: ServerInvoiceCreditGateway(account) }
    val context = LocalContext.current
    var paidRemaining by remember(companyId) { mutableLongStateOf(0L) }
    var paidLoading by remember { mutableStateOf(false) }
    // Stable idempotency key for the current unsaved invoice.
    var operationKey by rememberSaveable(companyId) { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    suspend fun refreshPaidBalance(): Long {
        val value = activeGateway.paidBalance(accessToken, companyId)
        require(value >= 0) { "موجودی معتبر نیست." }
        paidRemaining = value
        return value
    }
    LaunchedEffect(accessToken, companyId) {
        paidLoading = true
        runCatching { refreshPaidBalance() }.onFailure { paidRemaining = 0 }
        paidLoading = false
    }
    var promoRemaining by remember { mutableIntStateOf(0) }
    var promoLoading by remember { mutableStateOf(false) }
    LaunchedEffect(accessToken, companyId) {
        promoLoading = true
        promoRemaining = runCatching { activeGateway.giftBalance(accessToken) }.getOrDefault(0)
        promoLoading = false
    }
    val usableCharge = paidRemaining + promoRemaining

    var exportBytes by remember { mutableStateOf<ByteArray?>(null) }
    val pdfExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val data = exportBytes
        if (uri != null && data != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                    ?: throw IllegalStateException("فایل باز نشد.")
                onNotice("PDF صورتحساب ذخیره شد.")
            } catch (_: Exception) { onNotice("ذخیره PDF ناموفق بود.") }
        }
        exportBytes = null
    }
    var templateCompany by remember { mutableStateOf<Company?>(null) }
    val repository: InvoiceDraftGateway = remember(db) { RoomInvoiceDraftGateway(InvoiceRepository(db)) }
    val scope = rememberCoroutineScope()
    var automaticNumber by remember { mutableStateOf(false) }
    var sellerName by remember(companyId) { mutableStateOf("") }
    LaunchedEffect(companyId) {
        automaticNumber = db.moadianDetailsDao().byCompany(companyId) != null
        sellerName = db.companyDao().byId(companyId)?.name.orEmpty()
    }
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

    var pendingCreation by remember { mutableStateOf<InvoiceCreationClaim?>(null) }
    val creation = remember(db,account,accessToken,activeGateway) {
        InvoiceCreationCoordinator(db,account) { kind,id,key ->
            if(kind==InvoiceCreditKind.PAID) paidRemaining=activeGateway.consumePaid(accessToken,id,key)
            else promoRemaining=activeGateway.consumeGift(accessToken,key)
        }
    }
    val editorLocked = invoiceId==null && pendingCreation?.requestId==operationKey
    fun restoreClaim(claim:InvoiceCreationClaim) {
        val saved=creation.snapshot(claim)
        pendingCreation=claim;operationKey=claim.requestId;invoiceId=null
        customerId=saved.customerId;number=saved.number;date=saved.date;lines=saved.lines
        error="";mode="edit"
    }
    suspend fun beginInvoice() {
        val recovery=creation.pending(companyId)
        pendingCreation=recovery
        if(recovery!=null){restoreClaim(recovery);return}
        // Constructing an unsaved invoice must never require credit or perform a debit.
        // Credit is verified when the user explicitly saves a new invoice.
        operationKey=java.util.UUID.randomUUID().toString()
        invoiceId=null;number="";date="";customerId=customers.firstOrNull()?.id
        lines=emptyList();error="";mode="edit"
    }
    LaunchedEffect(companyId,account) {
        runCatching{creation.pending(companyId)}.onSuccess{pendingCreation=it}
    }
    var lastHandledNewInvoiceRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(newInvoiceRequest) {
        if(newInvoiceRequest>lastHandledNewInvoiceRequest) {
            lastHandledNewInvoiceRequest=newInvoiceRequest;onNewInvoiceRequestHandled()
            beginInvoice()
        }
    }

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
        if ((mode == "details" || mode == "template") && invoiceId != null) {
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
            "template" -> {
                val loaded = details
                val customer = loaded?.invoice?.let { inv -> customers.firstOrNull { it.id == inv.customerId } }
                val company = templateCompany
                if (loaded != null && customer != null && company != null) {
                    InvoiceTemplateScreen(company, customer, loaded.invoice, loaded.lines,
                        onExport = { bytes ->
                            exportBytes = bytes
                            pdfExport.launch("SURA-${loaded.invoice.invoiceNumber}.pdf")
                        },
                        onBack = { mode = "details" },
                        onSend = {
                            if (loaded.invoice.status == "FINALIZED") onOpenOfficialSubmission()
                            else onNotice("ابتدا صورتحساب را نهایی‌سازی کنید؛ پیش‌نویس قابل ارسال رسمی نیست.")
                        })
                } else Text("در حال بارگذاری اطلاعات الگو…")
            }
            "list" -> {
                Text("فاکتورهای شرکت", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("اعتبار مودی: $paidRemaining | اعتبار هدیه: $promoRemaining", style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !busy, onClick = { perform { onChanged() } }) { Text("تازه‌کردن فهرست") }
                if(pendingCreation!=null) Text("یک درخواست ایجاد صورتحساب ناتمام محفوظ است؛ با همان اطلاعات ادامه دهید.")
                Button(enabled = !busy, onClick = { perform { beginInvoice() } }) { Text("صدور فاکتور جدید") }
                if (customers.isEmpty()) Text("فعلاً مشتری ثبت نشده است؛ می‌توانید اسکلت فاکتور را آماده کنید، اما برای ذخیره باید مشتری داشته باشید.")
                if (usableCharge <= 0 && !promoLoading && !paidLoading) Text("برای ذخیره فاکتور جدید به اعتبار تأییدشده نیاز است؛ تکمیل فرم بدون مصرف اعتبار ممکن است.")
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
                        OutlinedButton(onClick = {
                            perform {
                                templateCompany = db.companyDao().byId(companyId)
                                    ?: throw IllegalStateException("مؤدی یافت نشد.")
                                mode = "template"
                            }
                        }) { Text("پیش‌نمایش و PDF فاکتور") }
                        Text("این سند در دستگاه ثبت شده و به سامانه مؤدیان ارسال نشده است.", style = MaterialTheme.typography.bodySmall)
                        if (invoice.status == "FINALIZED") {
                            OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(),
                                onClick = onOpenOfficialSubmission) { Text("آماده‌سازی و ارسال رسمی به سامانه مؤدیان") }
                        }
                        if (invoice.status == "DRAFT") {
                            Button(enabled = !busy, onClick = {
                                number = invoice.invoiceNumber; date = invoice.issueDate; customerId = invoice.customerId
                                lines = loaded.lines.map { it.copy(id = 0, invoiceId = 0) }; error = ""; mode = "edit"
                            }) { Text("ویرایش پیش‌نویس") }
                            Button(enabled = !busy, onClick = { actionId = invoice.id; confirmation = "finalize" }) { Text("نهایی‌سازی صورتحساب") }
                            TextButton(enabled = !busy, onClick = { actionId = invoice.id; confirmation = "delete" }) { Text("حذف پیش‌نویس", color = MaterialTheme.colorScheme.error) }
                        } else Text("سند نهایی‌شده قابل ویرایش یا حذف نیست.")
                    } else if (!busy) OutlinedButton(onClick = { mode = "list" }) { Text("بازگشت و تلاش مجدد") }
                    Spacer(Modifier.height(16.dp))
                }
            }
            "edit" -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InvoiceEditorHeading(seller = sellerName, editingDraft = invoiceId != null, lineCount = lines.size)
                    Text("صورتحساب فروش کالا و خدمات • مبالغ ریال • مقدار و نرخ اعشاری", style = MaterialTheme.typography.bodySmall)
                    if (invoiceId == null && !paidLoading && !promoLoading && usableCharge == 0L) {
                        Text("فرم بدون اعتبار قابل تکمیل است؛ هنگام ذخیره، اعتبار سرور بررسی می‌شود.", style = MaterialTheme.typography.bodySmall)
                    }
                    InvoiceEditorSection("۱", "مشخصات صورتحساب", "شماره و تاریخ صدور")
                    if(editorLocked) Text("این درخواست محفوظ است؛ برای جلوگیری از مصرف تکراری اعتبار، اطلاعات آن تا تعیین نتیجه ثابت می‌ماند.")
                    OutlinedTextField(number, { number = it }, enabled = !busy && !editorLocked, label = { Text("شماره فاکتور") }, supportingText = { if (automaticNumber) Text("برای تخصیص خودکار سریال مودی، این کادر را خالی بگذارید.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(date, { date = it }, enabled = !busy && !editorLocked, label = { Text("تاریخ شمسی، نمونه: ۱۴۰۵/۰۷/۰۲") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
                    InvoiceEditorSection("۲", "طرف حساب", "مؤدی فروشنده و مشتری خریدار")
                    Text("فروشنده: ${sellerName.ifBlank { "مؤدی انتخاب‌شده" }}", style = MaterialTheme.typography.bodyMedium)
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(enabled = !busy && !editorLocked, onClick = { expanded = true }) { Text("مشتری: " + (customers.firstOrNull { it.id == customerId }?.name ?: "انتخاب کنید")) }
                        DropdownMenu(expanded, { expanded = false }) {
                            customers.forEach { customer -> DropdownMenuItem(text = { Text(customer.name) }, onClick = { customerId = customer.id; expanded = false }) }
                        }
                    }
                    InvoiceEditorSection("۳", "اقلام کالا و خدمات", "انتخاب شناسه‌های مؤدی، شرح، تعداد، قیمت، تخفیف و مالیات")
                    Text("اقلام فاکتور (${lines.size})", style = MaterialTheme.typography.titleMedium)
                    lines.forEachIndexed { index, row ->
                        LineCard(index, row) {
                            OutlinedButton(enabled = !busy && !editorLocked, onClick = { lineIndex = index; lineDialog = true }) { Text("ویرایش ردیف") }
                            TextButton(enabled = !busy && !editorLocked, onClick = { lines = lines.filterIndexed { i, _ -> i != index } }) { Text("حذف ردیف") }
                        }
                    }
                    OutlinedButton(enabled = !busy && !editorLocked, onClick = { lineIndex = -1; lineDialog = true }) { Text("افزودن کالا یا خدمت") }
                    InvoiceLiveOutline(
                        seller = sellerName,
                        buyer = customers.firstOrNull { it.id == customerId }?.name,
                        invoiceNumber = number,
                        issueDate = date,
                        lines = lines
                    )
                    Button(enabled = !busy && lines.isNotEmpty(), onClick = {
                        perform {
                            val selectedCustomer = customerId ?: throw IllegalArgumentException("مشتری را انتخاب کنید.")
                            val savedId = if(invoiceId==null) {
                                val kind=pendingCreation?.let{InvoiceCreditKind.valueOf(it.creditKind)} ?: run {
                                    val verifiedPaid = runCatching { refreshPaidBalance() }.getOrDefault(0L)
                                    val verifiedGift = runCatching { activeGateway.giftBalance(accessToken) }.getOrDefault(0)
                                    promoRemaining = verifiedGift
                                    if (verifiedPaid > 0) InvoiceCreditKind.PAID
                                    else if (verifiedGift > 0) InvoiceCreditKind.GIFT
                                    else throw IllegalStateException("اعتبار کافی یا اتصال معتبر به سرور موجود نیست؛ فرم فعلی محفوظ است.")
                                }
                                val claim=creation.prepare(companyId,InvoiceCreationSnapshot(selectedCustomer,number,date,lines),operationKey,kind)
                                restoreClaim(claim)
                                creation.finish(claim.requestId).also{pendingCreation=null}
                            } else repository.saveDraft(companyId,selectedCustomer,number,date,lines,invoiceId)
                            // Retain identity before refreshing, so a retry never creates a second draft.
                            invoiceId = savedId
                            operationKey = java.util.UUID.randomUUID().toString()
                            onChanged()
                            runCatching { refreshPaidBalance() }
                            mode = "details"
                            onNotice("فاکتور جدید ثبت شد؛ شارژ هنگام ایجاد اولیه مصرف می‌شود، نه هنگام ارسال.")
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "در حال ذخیره…" else "ذخیره پیش‌نویس") }
                    OutlinedButton(enabled = !busy, onClick = { confirmation = "leave" }, modifier = Modifier.fillMaxWidth()) { Text("انصراف از ویرایش") }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (lineDialog) LineEditor(
        ownerId = ownerId,
        companyId = companyId,
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
            "delete" -> "پیش‌نویس لغو می‌شود؛ سوابق و مصرف اعتبار آن باقی می‌ماند."
            "finalize" -> "اعتبار هنگام ایجاد اولیه مصرف شده است؛ نهایی‌سازی اعتبار دیگری مصرف نمی‌کند. سند دیگر قابل ویرایش نخواهد بود و ارسال رسمی انجام نمی‌شود."
            else -> if(editorLocked) "درخواست ناتمام محفوظ می‌ماند و با همان اطلاعات قابل ادامه است." else "تغییرات ذخیره‌نشده کنار گذاشته می‌شوند."
        }) },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val requested = confirmation
            if (requested == "leave") { confirmation = ""; mode = if (invoiceId == null) "list" else "details"; error = "" }
            else perform {
                val id = actionId ?: throw IllegalArgumentException("فاکتور را دوباره انتخاب کنید.")
                if (requested == "delete") repository.deleteDraft(companyId, id)
                else { repository.detailsForCompany(companyId, id); repository.finalizeDraft(companyId, id) }
                confirmation = ""
                if (requested == "delete") { details = null; mode = "list" }
                else details = repository.detailsForCompany(companyId, id)
                onChanged()
                onNotice(if (requested == "delete") "پیش‌نویس لغو شد؛ شارژ مصرف‌شده بازگردانده نمی‌شود." else "فاکتور محلی نهایی شد؛ شارژ هنگام ایجاد فاکتور مصرف شده است و ارسال واقعی انجام نشد.")
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
            Text("مقدار: ${InvoiceLineAmounts.quantityText(row)}" +
                (if (row.unitCode.isNotBlank()) " ${MoadianMeasurementUnits.nameOf(row.unitCode) ?: row.unitCode}" else "") +
                " • قیمت واحد: ${rial(row.unitPriceRial)}")
            Text("تخفیف: ${rial(row.discountRial)} • مالیات: ${InvoiceLineAmounts.vatText(row)}٪")
            Text("خالص ردیف: ${rial(Math.subtractExact(InvoiceLineAmounts.grossRial(row), row.discountRial))}")
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
            Text(if (lines.any(InvoiceLineAmounts::isPrecise))
                    "مبالغ ردیف‌های جدید طبق قاعده دقیق ریالی محاسبه می‌شوند."
                else "صورتحساب‌های قدیمی با روش محاسبات زمان ثبت حفظ شده‌اند.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LineEditor(ownerId: String, companyId: Long, initial: InvoiceLine?, onDismiss: () -> Unit, onSave: (InvoiceLine) -> Unit) {
    var code by rememberSaveable { mutableStateOf(initial?.itemCode.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(initial?.let(InvoiceLineAmounts::quantityText) ?: "1") }
    var unit by rememberSaveable { mutableStateOf(initial?.unitCode.orEmpty()) }
    var price by rememberSaveable { mutableStateOf(initial?.unitPriceRial?.toString().orEmpty()) }
    var discount by rememberSaveable { mutableStateOf(initial?.discountRial?.toString() ?: "0") }
    var tax by rememberSaveable { mutableStateOf(initial?.let(InvoiceLineAmounts::vatText).orEmpty()) }
    var vatHint by rememberSaveable { mutableStateOf("") }
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
                InvoiceIdentifierPicker(ownerId = ownerId, companyId = companyId, enabled = true) { identifier ->
                    try {
                        val selected = InvoiceIdentifierPrefill.from(identifier)
                        code = selected.code
                        description = selected.description
                        tax = selected.vatRatePercent
                        vatHint = if (selected.requiresVatReview) {
                            "نرخ مالیات این شناسه مشخص یا قابل ثبت به‌صورت عدد صحیح نیست؛ نرخ معتبر را دستی وارد کنید."
                        } else "نرخ واردشده از بانک نیازمند بررسی اعتبار روز است."
                        error = ""
                    } catch (e: IllegalArgumentException) {
                        error = e.message ?: "شناسه قابل انتخاب نیست."
                    }
                }
                OutlinedTextField(description, { description = it }, label = { Text("شرح کالا یا خدمت") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(code, { code = it }, label = { Text("شناسه کالا/خدمت (اختیاری برای ثبت محلی)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumberField(quantity, { quantity = it }, "تعداد / مقدار (تا ۸ رقم اعشار)")
                OutlinedTextField(
                    unit, { unit = InvoiceInput.digits(it).trim() },
                    label = { Text("کد واحد سنجش هر ردیف") }, singleLine = true,
                    supportingText = { Text("برای ارسال رسمی، هر کالا واحد معتبر خودش را نیاز دارد.") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1627" to "عدد", "164" to "کیلوگرم", "165" to "متر").forEach { (key, title) ->
                        FilterChip(selected = unit == key, onClick = { unit = key }, label = { Text(title) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1637" to "لیتر", "16103" to "ساعت", "1653" to "واحد").forEach { (key, title) ->
                        FilterChip(selected = unit == key, onClick = { unit = key }, label = { Text(title) })
                    }
                }
                NumberField(price, { price = it }, "قیمت واحد به ریال")
                NumberField(discount, { discount = it }, "تخفیف کل این ردیف به ریال")
                NumberField(tax, { tax = it; vatHint = "" }, "درصد مالیات (تا ۲ رقم اعشار)")
                if (vatHint.isNotEmpty()) Text(vatHint, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = {
            try {
                val row = InvoiceLineAmounts.fromUserInput(
                    code = code, description = description,
                    rawQuantity = quantity, price = InvoiceInput.wholeNumber(price, "قیمت"),
                    discount = InvoiceInput.wholeNumber(discount, "تخفیف"),
                    rawRate = tax, unit = unit
                )
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
