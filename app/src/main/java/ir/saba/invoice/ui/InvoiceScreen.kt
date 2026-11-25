package ir.saba.invoice.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.room.withTransaction
import ir.saba.invoice.security.AccountSession
import ir.saba.invoice.security.OfficialSubmissionService
import ir.saba.invoice.security.TaxpayerCredentialRepository
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import ir.saba.invoice.data.InvoiceLocalDetails
import ir.saba.invoice.domain.InvoiceInput
import ir.saba.invoice.domain.JalaliCalendar
import ir.saba.invoice.domain.InvoiceIdentifierPrefill
import ir.saba.invoice.domain.InvoiceLineAmounts
import ir.saba.invoice.domain.InvoiceSettlement
import ir.saba.invoice.domain.InvoiceSubject
import ir.saba.moadian.MoadianMeasurementUnits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

private val LinesSaver = listSaver<List<InvoiceLine>, String>(
    save = { rows -> listOf("__SURA_INVOICE_LINES_V3__") + rows.flatMap {
        listOf(it.itemCode, it.description, it.quantity.toString(), it.unitPriceRial.toString(),
            it.discountRial.toString(), it.taxRatePercent.toString(), it.unitCode, it.quantityExact,
            it.vatRateExact, it.itemTitle)
    } },
    restore = { values ->
        val version = values.firstOrNull()
        val width = when(version) {
            "__SURA_INVOICE_LINES_V3__" -> 10
            "__SURA_INVOICE_LINES_V2__" -> 9
            else -> 6
        }
        val data = if (width > 6) values.drop(1) else values
        data.chunked(width).filter { it.size == width }.map { item ->
            InvoiceLine(invoiceId = 0, itemCode = item[0], description = item[1],
                quantity = item[2].toLong(), unitPriceRial = item[3].toLong(),
                discountRial = item[4].toLong(), taxRatePercent = item[5].toInt(),
                unitCode = item.getOrElse(6) { "" }, quantityExact = item.getOrElse(7) { "" },
                vatRateExact = item.getOrElse(8) { "" }, itemTitle = item.getOrElse(9) { "" })
        }
    }
)

internal fun rial(amount: Long): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("fa-IR")).format(amount) + " ریال"
internal fun invoiceStatus(status: String) = when (status) {
    "DRAFT" -> "پیش‌نویس"
    "FINALIZED" -> "نهایی‌شده محلی"
    "CANCELLED" -> "لغوشده"
    "RESTORED" -> "سابقه بازیابی‌شده (فقط مشاهده)"
    else -> status
}

/** SUCCESS from authenticated direct inquiry is distinct from eligibility as a reference. */
internal fun needsLegacyReferenceCheck(row: OfficialSubmission?): Boolean =
    row?.protocol == "LEGACY" && row.state == "ACCEPTED" &&
        !row.referenceNumber.isNullOrBlank() && !OfficialReferenceEvidence.eligible(row)

internal fun isLegacyEvidenceMessage(row: OfficialSubmission): Boolean =
    row.protocol == "LEGACY" && row.state == "ACCEPTED" &&
        row.lastError in setOf(OfficialReferenceEvidence.LEGACY_BOUND, OfficialReferenceEvidence.LEGACY_UNBOUND)

/** Compact status for the list, details and PDF; never infer acceptance from a tax ID. */
internal fun invoiceReceiptLabel(invoice: Invoice, row: OfficialSubmission?): String = when (row?.state) {
    null -> if (!invoice.trackingCode.isNullOrBlank() || invoice.status == "RESTORED")
        "سابقه نیازمند استعلام" else if (invoice.status == "DRAFT") "پیش‌نویس؛ ارسال نشده" else "ارسال نشده"
    "PREPARED" -> "آماده؛ ارسال نشده"
    "SENDING", "UNKNOWN" -> "وضعیت نامشخص؛ نیازمند استعلام"
    "SUBMITTED" -> "ارسال شده؛ در انتظار پذیرش"
    "ACCEPTED" -> if (OfficialReferenceEvidence.eligible(row)) "پذیرفته شده در سامانه"
        else if (needsLegacyReferenceCheck(row)) "پذیرش گزارش‌شده؛ تطبیق مرجع لازم است"
        else "پاسخ موفق؛ پذیرش نیازمند بررسی"
    "REJECTED" -> "رد شده در سامانه"
    "RESTORED" -> "سابقه نیازمند استعلام"
    else -> "وضعیت نامشخص؛ نیازمند استعلام"
}

/** Receipt status must never be inferred from a local draft, tracking code alone or a POST response. */
internal fun invoiceTaxStatus(row: OfficialSubmission?): String = when (row?.state) {
    null -> "این صورتحساب هنوز به سامانه مؤدیان ارسال نشده است."
    "PREPARED" -> "درخواست رسمی آماده شده ولی هنوز به سامانه مؤدیان ارسال نشده است."
    "SENDING", "UNKNOWN" -> "وضعیت ارسال قطعی نیست؛ تنها استعلام کنید و صورتحساب را مجدداً ارسال نکنید."
    "SUBMITTED" -> "در سامانه ارسال شده؛ پذیرش نهایی هنوز تأیید نشده و استعلام لازم است."
    "ACCEPTED" -> if (row.protocol == "LEGACY")
        if (OfficialReferenceEvidence.eligible(row))
            "پذیرفته شده در سامانه؛ شناسه‌های مرجع با استعلام تطبیق داده شده‌اند."
        else "استعلام مستقیم سامانه موفق گزارش شده است. برای صدور سند ارجاعی، تطبیق کامل مرجع لازم است؛ ارسال مجدد نکنید."
        else if (row.signedReceipt.isNullOrBlank())
        "وضعیت پذیرش نیازمند بررسی رسید سازمان است."
        else "صورتحساب در سامانه مؤدیان پذیرفته شده و رسید امضاشده بررسی شده است."
    "REJECTED" -> "صورتحساب در سامانه مؤدیان رد شده است؛ جزئیات خطا را بررسی کنید."
    "RESTORED" -> "سابقه مالیاتی بازیابی‌شده است؛ وضعیت آنلاین باید مستقل بررسی شود. ارسال مجدد مجاز نیست."
    else -> "وضعیت رسمی صورتحساب ناشناخته است؛ استعلام لازم است."
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
    creditGateway: InvoiceCreditGateway? = null,
    openEventInvoiceId: Long? = null,
    onEventInvoiceHandled: () -> Unit = {},
    onBackToEvents: () -> Unit = {}
) {
    val states = rememberSaveableStateHolder()
    states.SaveableStateProvider("${ir.saba.invoice.security.AccountScope.of(ownerId).storageKey}:$companyId") {
        key(ownerId, companyId) { InvoiceScreenContent(db, ownerId, companyId, customers, invoices, onChanged, onNotice, onEditorActive, availableCharge, accessToken, onNeedCharge, newInvoiceRequest, onNewInvoiceRequestHandled, onOpenOfficialSubmission, creditGateway, openEventInvoiceId, onEventInvoiceHandled, onBackToEvents) }
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
    creditGateway: InvoiceCreditGateway? = null,
    openEventInvoiceId: Long? = null,
    onEventInvoiceHandled: () -> Unit = {},
    onBackToEvents: () -> Unit = {}
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
    var fromEvents by rememberSaveable { mutableStateOf(false) }
    fun backFromInvoice() { if (fromEvents) { fromEvents = false; mode = "list"; onBackToEvents() } else mode = "list" }
    var invoiceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var number by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var settlementMode by rememberSaveable(companyId) { mutableStateOf(InvoiceSettlement.CASH) }
    var cashPortionInput by rememberSaveable(companyId) { mutableStateOf("") }
    var subject by rememberSaveable(companyId) { mutableStateOf(InvoiceSubject.ORIGINAL) }
    var referenceTaxId by rememberSaveable(companyId) { mutableStateOf("") }
    var localDetailsJson by rememberSaveable(companyId) { mutableStateOf("") }
    var previewDialog by rememberSaveable { mutableStateOf(false) }
    var acceptedOriginals by remember(companyId) { mutableStateOf(emptyList<Pair<String, Long>>()) }
    var customerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inactiveBuyers by remember(companyId) { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(companyId, customers, mode) {
        try{inactiveBuyers=db.customerProfileDao().forCompany(companyId).filter{!it.active}.map{it.customerId}.toSet()}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){onNotice(SuraErrors.describe(e,"بازیابی وضعیت مشتریان انجام نشد. [CUSTOMER-STATUS]"))}
    }
    var lines by rememberSaveable(stateSaver = LinesSaver) { mutableStateOf(emptyList<InvoiceLine>()) }
    var search by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("ALL") }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var details by remember { mutableStateOf<InvoiceDetails?>(null) }
    var officialRecord by remember(companyId) { mutableStateOf<OfficialSubmission?>(null) }
    var officialByInvoice by remember(companyId, ownerId) { mutableStateOf(emptyMap<Long, OfficialSubmission>()) }
    LaunchedEffect(companyId, ownerId, invoices, mode) {
        try {
            officialByInvoice = db.officialSubmissionDao().forCompany(companyId)
                .filter { it.companyId == companyId && it.ownerId == ownerId }
                .associateBy { it.invoiceId }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { officialByInvoice = emptyMap() }
    }
    val credentials = remember(db, account) { TaxpayerCredentialRepository(context, db, account) }
    val officialService = remember(db, account, accessToken) {
        OfficialSubmissionService(db, account, credentials,
            sessionCheck = { AccountSession.requireOwner(accessToken, account) }, directOnly = true)
    }
    var sendConfirm by rememberSaveable { mutableStateOf(false) }
    var issueClock by rememberSaveable { mutableStateOf("") }
    var sendProgress by remember { mutableStateOf("") }
    val tehran = remember { ZoneId.of("Asia/Tehran") }
    var lineDialog by rememberSaveable { mutableStateOf(false) }
    var lineEditorZone by rememberSaveable { mutableStateOf("pick") }
    var lineIndex by rememberSaveable { mutableIntStateOf(-1) }
    var duplicateLineIndex by rememberSaveable { mutableIntStateOf(-1) }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var actionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var documentBusy by remember { mutableStateOf(false) }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        if (selected != null) {
            documentBusy = true
            scope.launch {
                try {
                    val reference = withContext(Dispatchers.IO) {
                        InvoiceDocumentPicker.inspect(context, selected)
                    }
                    val current = InvoiceLocalDetails.fromJson(localDetailsJson)
                    require(current.documents.size < InvoiceDocumentRef.MAX_FILES) {
                        "حداکثر پنج سند به هر صورتحساب پیوست می‌شود."
                    }
                    require(current.documents.none { it.uri == reference.uri }) {
                        "این سند قبلاً پیوست شده است."
                    }
                    context.contentResolver.takePersistableUriPermission(
                        selected, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    localDetailsJson = current.copy(documents = current.documents + reference).toJson()
                    onNotice("سند به فرم پیوست شد؛ با ذخیره پیش‌نویس ثبت می‌شود.")
                } catch (e: Exception) {
                    onNotice(e.message ?: "خواندن سند یا دریافت دسترسی به آن ناموفق بود.")
                } finally {
                    documentBusy = false
                }
            }
        }
    }

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
        settlementMode=saved.settlementMode
        subject=saved.subject;referenceTaxId=saved.referenceTaxId.orEmpty()
        localDetailsJson=saved.localDetailsJson
        cashPortionInput=saved.cashPaidRial?.toString().orEmpty()
        error="";mode="edit"
    }
    suspend fun beginInvoice() {
        val recovery=creation.pending(companyId)
        pendingCreation=recovery
        if(recovery!=null){restoreClaim(recovery);return}
        // Constructing an unsaved invoice must never require credit or perform a debit.
        // Credit is verified when the user explicitly saves a new invoice.
        operationKey=java.util.UUID.randomUUID().toString()
        val suggestedNumber = if (db.moadianDetailsDao().byCompany(companyId) != null) {
            TaxpayerSerialAllocator(db).suggest(companyId)
        } else {
            // Local-only taxpayers also receive a convenient editable number.
            val highest = db.invoiceDao().forCompany(companyId).mapNotNull {
                InvoiceInput.number(it.invoiceNumber).toLongOrNull()
            }.maxOrNull() ?: 0L
            (highest + 1).toString()
        }
        invoiceId=null;number=suggestedNumber
        date=JalaliCalendar.fromGregorian(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tehran")))
        inactiveBuyers=db.customerProfileDao().forCompany(companyId).filter{!it.active}.map{it.customerId}.toSet()
        customerId=customers.firstOrNull{it.id !in inactiveBuyers}?.id
        settlementMode=InvoiceSettlement.CASH;cashPortionInput=""
        subject=InvoiceSubject.ORIGINAL;referenceTaxId="";localDetailsJson=""
        lines=emptyList();error="";mode="edit"
    }
    LaunchedEffect(companyId,account) {
        runCatching{creation.pending(companyId)}.onSuccess{pendingCreation=it}
    }
    LaunchedEffect(companyId, mode) {
        if (mode == "edit") {
            acceptedOriginals = runCatching {
                db.officialSubmissionDao().forCompany(companyId)
                    .filter { it.ownerId == ownerId && OfficialReferenceEvidence.eligible(it) }
                    .mapNotNull { row ->
                        val original = db.invoiceDao().byId(row.invoiceId)
                        if (original?.companyId == companyId &&
                            original.subject == InvoiceSubject.ORIGINAL &&
                            original.status == "FINALIZED") row.taxId to original.customerId
                        else null
                    }
            }.getOrDefault(emptyList())
        }
    }
    var lastHandledNewInvoiceRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(openEventInvoiceId) {
        val id = openEventInvoiceId ?: return@LaunchedEffect
        fromEvents = true
        invoiceId = id
        error = ""
        mode = "event_open"
        onEventInvoiceHandled()
    }
    LaunchedEffect(newInvoiceRequest) {
        if(newInvoiceRequest>lastHandledNewInvoiceRequest) {
            lastHandledNewInvoiceRequest=newInvoiceRequest;onNewInvoiceRequestHandled();fromEvents=false
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
                error = if (pendingCreation != null && e is java.io.IOException) {
                    "تأیید مصرف اعتبار دریافت نشد؛ برای جلوگیری از برداشت تکراری، همین درخواست را ادامه دهید."
                } else SuraErrors.transport(e) ?: if (e is ArithmeticException) "مبلغ فاکتور بیش از محدوده مجاز است." else e.message ?: "عملیات انجام نشد؛ دوباره تلاش کنید."
                onNotice(error)
            } finally { busy = false }
        }
    }

    fun populateEditor(loaded: InvoiceDetails, keepId: Boolean) {
        val inv = loaded.invoice
        if (keepId) {
            invoiceId = inv.id; number = inv.invoiceNumber; date = inv.issueDate
        } else {
            invoiceId = null
            if (inv.subject != InvoiceSubject.ORIGINAL && automaticNumber) number = ""
        }
        customerId = inv.customerId; settlementMode = inv.settlementMode
        cashPortionInput = inv.cashPaidRial?.toString().orEmpty()
        subject = inv.subject; referenceTaxId = inv.referenceTaxId.orEmpty()
        localDetailsJson = if (keepId) inv.localDetailsJson else
            InvoiceLocalDetails.fromJson(inv.localDetailsJson).copy(sendLaterDate = "", sendLaterTime = "").toJson()
        lines = loaded.lines.map { it.copy(id = 0, invoiceId = 0) }
        error = ""; mode = "edit"
    }
    fun editExistingOrCopy(loaded: InvoiceDetails) {
        perform {
            val inv = loaded.invoice
            require(db.companyDao().byId(companyId)?.purchaserId == ownerId)
            val current = repository.detailsForCompany(companyId, inv.id)
            val receipt = db.officialSubmissionDao().forInvoice(inv.id)
            require(receipt == null || (receipt.ownerId == ownerId && receipt.companyId == companyId))
            when {
                receipt?.state == "REJECTED" -> {
                    require(creation.pending(companyId) == null) { "ابتدا درخواست صدور ناتمام را تکمیل کنید." }
                    beginInvoice()
                    // beginInvoice may restore a concurrent pending claim; never overwrite it.
                    if (pendingCreation == null) {
                        populateEditor(current, false)
                        onNotice("نسخه جدید قابل ویرایش است؛ سند ردشده محفوظ می‌ماند. ذخیره نسخه جدید به اعتبار نیاز دارد.")
                    }
                }
                current.invoice.status == "DRAFT" && receipt == null -> populateEditor(current, true)
                current.invoice.status == "FINALIZED" && (receipt == null || receipt.state == "PREPARED") -> {
                    InvoiceRepository(db).reopenUnsentForEdit(companyId, inv.id, ownerId)
                    officialRecord = null
                    officialByInvoice = officialByInvoice - inv.id
                    populateEditor(repository.detailsForCompany(companyId, inv.id), true)
                    onChanged()
                }
                else -> error("این سند قابل ویرایش نیست؛ وضعیت سامانه را استعلام کنید.")
            }
        }
    }

    suspend fun reloadOfficial(id: Long) {
        details = repository.detailsForCompany(companyId, id)
        officialRecord = db.officialSubmissionDao().forInvoice(id)
            ?.takeIf { it.companyId == companyId && it.ownerId == ownerId }
        officialRecord?.let { officialByInvoice = officialByInvoice + (id to it) }
        onChanged()
    }

    fun requestOfficialSend(loaded: InvoiceDetails) {
        if (busy) return
        val invoice = loaded.invoice
        val record = officialRecord
        if (needsLegacyReferenceCheck(record)) {
            perform {
                try {
                    sendProgress = "در حال تطبیق مرجع همان صورتحساب…"
                    officialService.verifyLegacyReference(requireNotNull(record).requestId)
                    onNotice("تطبیق مرجع موفق بود؛ هیچ صورتحساب جدیدی ارسال نشد.")
                } finally {
                    sendProgress = ""
                    reloadOfficial(invoice.id)
                    mode = if (fromEvents) "template" else "details"
                }
            }
            return
        }
        if (record?.state in setOf("SENDING", "UNKNOWN", "SUBMITTED")) {
            perform {
                try {
                    sendProgress = "در حال استعلام وضعیت صورتحساب…"
                    val row = requireNotNull(record)
                    if (row.protocol == "LEGACY") officialService.inquireLegacy(row.requestId)
                    else officialService.inquire(row.requestId)
                } finally {
                    sendProgress = ""
                    reloadOfficial(invoice.id)
                    mode = if (fromEvents) "template" else "details"
                }
            }
            return
        }
        if (record?.state == "PREPARED" && record.protocol != "LEGACY") {
            onNotice("این درخواست قدیمی ارسال نشده است؛ از سوابق، آماده‌سازی را لغو و با اتصال مستقیم دوباره آماده کنید.")
            onOpenOfficialSubmission()
            return
        }
        if (record != null && record.state != "PREPARED") {
            onNotice(invoiceTaxStatus(record))
            return
        }
        if (invoice.status !in setOf("DRAFT", "FINALIZED") ||
            invoice.subject !in setOf(InvoiceSubject.ORIGINAL, InvoiceSubject.CORRECTION,
                InvoiceSubject.CANCELLATION, InvoiceSubject.RETURN)) {
            onNotice("ارسال این صورتحساب از وضعیت فعلی مجاز نیست.")
            return
        }
        if (!InvoiceSubject.supportsLegacy(invoice.subject)) {
            onNotice("ارسال مستقیم این موضوع هنوز پشتیبانی نمی‌شود؛ صورتحساب محفوظ است.")
            return
        }
        // Legacy finalized rows without per-line units require the existing recovery tools.
        if (record == null && invoice.subject != InvoiceSubject.CANCELLATION &&
            loaded.lines.any { MoadianMeasurementUnits.nameOf(it.unitCode) == null }) {
            if (invoice.status == "FINALIZED") onOpenOfficialSubmission()
            else onNotice("واحد سنجش هر ردیف را در ویرایش پیش‌نویس انتخاب کنید.")
            return
        }
        issueClock = ""
        sendConfirm = true
    }

    fun sendConfirmed(id: Long, declaredClock: String) {
        perform {
            try {
                sendProgress = "در حال بررسی و ارسال صورتحساب…"
                // Freeze the draft and create its immutable outbox entry together.
                // Any local validation failure rolls back finalization, retaining an editable draft.
                val prepared = db.withTransaction {
                    AccountSession.requireOwner(accessToken, account)
                    val invoice = repository.detailsForCompany(companyId, id).invoice
                    InvoiceSendSchedule.requireDueForManualSubmission(
                        InvoiceLocalDetails.fromJson(invoice.localDetailsJson))
                    val prior = db.officialSubmissionDao().forInvoice(id)
                    if (prior != null) {
                        require(prior.ownerId == ownerId && prior.companyId == companyId)
                        require(prior.state == "PREPARED") {
                            "ارسال قبلاً آغاز شده است؛ فقط استعلام همان درخواست مجاز است."
                        }
                        officialService.reviewPrepared(prior.requestId)
                    } else {
                        val issuedDay = JalaliCalendar.toGregorian(invoice.issueDate)
                        val now = Instant.now().atZone(tehran)
                        val issued = if (issuedDay == now.toLocalDate()) now.toOffsetDateTime()
                        else {
                            val time = InvoiceInput.digits(declaredClock.trim())
                            require(time.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))) {
                                "ساعت واقعی صدور فاکتور را به صورت ساعت و دقیقه وارد کنید."
                            }
                            issuedDay.atTime(LocalTime.parse(time)).atZone(tehran).toOffsetDateTime()
                        }
                        require(!issued.toInstant().isAfter(now.toInstant())) {
                            "تاریخ و ساعت صدور نباید در آینده باشد."
                        }
                        require(InvoiceSubject.supportsLegacy(invoice.subject)) {
                            "ارسال مستقیم این موضوع هنوز پشتیبانی نمی‌شود."
                        }
                        val protocol = "LEGACY"
                        officialService.checkLegacySigningReadiness(companyId)
                        repository.finalizeDraft(companyId, id)
                        val row = officialService.prepare(companyId, id, issued.toString(), "", protocol)
                        officialService.reviewPrepared(row.requestId)
                    }
                }
                // A real POST is possible only after the user confirmed this exact invoice.
                officialRecord = officialService.submitAndInquire(prepared.requestId) { updated ->
                    officialRecord = updated
                    sendProgress = if (updated.state in setOf("SENDING", "UNKNOWN", "SUBMITTED"))
                        "در حال استعلام نتیجه همان صورتحساب…" else "نتیجه ثبت شد."
                }
                onNotice(invoiceTaxStatus(officialRecord))
            } finally {
                sendProgress = ""
                reloadOfficial(id)
                mode = "details"
            }
        }
    }

    // Preserve the existing credit-idempotent persistence path; only the UI entry moved.
    fun saveCurrentInvoice() {
        perform {
                            val selectedCustomer = customerId ?: throw IllegalArgumentException("مشتری را انتخاب کنید.")
                            InvoiceSubject.validate(subject, referenceTaxId.takeIf { it.isNotBlank() })
                            InvoiceLocalDetails.fromJson(localDetailsJson).requireValidSchedule()
                            val settlementCash = InvoiceSettlement.cashFromUserInput(
                                settlementMode, InvoiceCalculator.calculate(lines).totalAmountRial, cashPortionInput
                            )
                            val savedId = if(invoiceId==null) {
                                if(pendingCreation==null && db.customerProfileDao().byCustomer(selectedCustomer)?.active==false)throw IllegalStateException("مشتری غیرفعال است؛ مشتری دیگری انتخاب کنید.")
                                val kind=pendingCreation?.let{InvoiceCreditKind.valueOf(it.creditKind)} ?: run {
                                    val verifiedPaid = runCatching { refreshPaidBalance() }.getOrDefault(0L)
                                    val verifiedGift = runCatching { activeGateway.giftBalance(accessToken) }.getOrDefault(0)
                                    promoRemaining = verifiedGift
                                    if (verifiedPaid > 0) InvoiceCreditKind.PAID
                                    else if (verifiedGift > 0) InvoiceCreditKind.GIFT
                                    else throw IllegalStateException("اعتبار کافی یا اتصال معتبر به سرور موجود نیست؛ فرم فعلی محفوظ است.")
                                }
                                val claim=creation.prepare(companyId,InvoiceCreationSnapshot(selectedCustomer,number,date,lines,settlementMode,settlementCash,
                                    subject,referenceTaxId.takeIf { it.isNotBlank() }, localDetailsJson),operationKey,kind)
                                restoreClaim(claim)
                                creation.finish(claim.requestId).also{pendingCreation=null}
                            } else repository.saveDraft(companyId,selectedCustomer,number,date,lines,invoiceId,
                                settlementMode=settlementMode,cashPaidRial=settlementCash,
                                subject=subject,referenceTaxId=referenceTaxId.takeIf { it.isNotBlank() },
                                localDetailsJson=localDetailsJson)
                            // Retain identity before refreshing, so a retry never creates a second draft.
                            invoiceId = savedId
                            operationKey = java.util.UUID.randomUUID().toString()
                            onChanged()
                            runCatching { refreshPaidBalance() }
                            mode = "details"
                            onNotice("پیش‌نویس ذخیره شد.")

        }
    }

    LaunchedEffect(mode, invoiceId) {
        if ((mode == "details" || mode == "template" || mode == "event_open") && invoiceId != null) {
            details = null
            officialRecord = null
            busy = true
            try {
                val id = requireNotNull(invoiceId)
                details = repository.detailsForCompany(companyId, id)
                officialRecord = db.officialSubmissionDao().forInvoice(id)
                    ?.takeIf { it.companyId == companyId && it.ownerId == ownerId }
                if (mode == "event_open") {
                    val loaded = requireNotNull(details)
                    if (invoiceEventOpensPreview(invoiceEventStatus(loaded.invoice, officialRecord))) {
                        templateCompany = db.companyDao().byId(companyId)?.takeIf { it.purchaserId == ownerId }
                            ?: error("مودی یافت نشد.")
                        mode = "template"
                    } else mode = "details"
                } else if (mode == "template" && templateCompany == null) {
                    templateCompany = db.companyDao().byId(companyId)?.takeIf { it.purchaserId == ownerId }
                        ?: error("مودی یافت نشد.")
                }
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = SuraErrors.describe(e,"جزئیات فاکتور بارگذاری نشد. [INVOICE-READ]") }
            finally { busy = false }
        }
    }

    BackHandler(enabled = mode != "list") {
        if (!busy) {
            if (mode == "edit") confirmation = "leave" else { backFromInvoice(); error = "" }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (error.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (sendProgress.isNotBlank()) Text(sendProgress)
        }
        when (mode) {
            "event_open" -> { if (!busy) TextButton(onClick = { backFromInvoice() }) { Text("بازگشت به فاکتورها") } }
            "template" -> {
                val loaded = details
                val customer = loaded?.invoice?.let { inv -> customers.firstOrNull { it.id == inv.customerId } }
                val company = templateCompany
                if (loaded != null && customer != null && company != null) {
                    val directPreview = fromEvents && invoiceEventOpensPreview(invoiceEventStatus(loaded.invoice, officialRecord))
                    InvoiceTemplateScreen(company, customer, loaded.invoice, loaded.lines,
                        onExport = { bytes ->
                            exportBytes = bytes
                            pdfExport.launch("SURA-${loaded.invoice.invoiceNumber}.pdf")
                        },
                        onBack = { if (directPreview) backFromInvoice() else mode = "details" },
                        onSend = { requestOfficialSend(loaded) },
                        official = officialRecord, db = db, directPreview = directPreview)
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
                                val receipt = officialByInvoice[invoice.id]
                                Text("${invoice.issueDate} • ${invoiceReceiptLabel(invoice, receipt)}")
                                receipt?.taxId?.let { Text("شماره مالیاتی: $it", style = MaterialTheme.typography.bodySmall) }
                                receipt?.referenceNumber?.let { Text("شماره پیگیری: $it", style = MaterialTheme.typography.bodySmall) }
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
                    OutlinedButton(enabled = !busy, onClick = { backFromInvoice(); error = "" }) { Text("بازگشت به فهرست") }
                    if (loaded != null) {
                        val invoice = loaded.invoice
                        Text("فاکتور ${invoice.invoiceNumber}", style = MaterialTheme.typography.titleLarge)
                        Text("مشتری: ${customers.firstOrNull { it.id == invoice.customerId }?.name ?: invoice.customerId}")
                        Text("تاریخ: ${invoice.issueDate}")
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("وضعیت سامانه مؤدیان", fontWeight = FontWeight.Bold)
                                Text(invoiceReceiptLabel(invoice, officialRecord),
                                    color = if (officialRecord?.let(OfficialReferenceEvidence::eligible) == true)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                officialRecord?.taxId?.let { Text("شماره مالیاتی: $it") }
                                officialRecord?.referenceNumber?.let { Text("شماره پیگیری رسمی: $it") }
                                if (needsLegacyReferenceCheck(officialRecord)) {
                                    OutlinedButton(enabled = !busy, onClick = { requestOfficialSend(loaded) },
                                        modifier = Modifier.fillMaxWidth().testTag("invoiceVerifyLegacyReference")) {
                                        Text("استعلام و تطبیق مرجع")
                                    }
                                }
                                if (officialRecord == null && !invoice.trackingCode.isNullOrBlank())
                                    Text("کد پیگیری سابقه: ${invoice.trackingCode}؛ نیازمند استعلام")
                            }
                        }
                        Text("موضوع: " + InvoiceSubject.label(invoice.subject))
                        invoice.referenceTaxId?.let { Text("شماره مالیاتی مرجع: " + it) }
                        Text("تسویه: " + InvoiceSettlement.label(invoice.settlementMode))
                        InvoiceSettlement.amounts(invoice).also { amounts ->
                            Text("نقد: ${rial(amounts.cashRial)} • نسیه: ${rial(amounts.creditRial)}")
                        }
                        val local = runCatching { InvoiceLocalDetails.fromJson(invoice.localDetailsJson) }
                            .getOrDefault(InvoiceLocalDetails())
                        val extra = listOf(
                            "موضوع مالیات بند ۱۷" to local.article17Subject,
                            "شماره سوئیچ پرداخت" to local.paymentSwitchNumber,
                            "روش پرداخت" to local.paymentMethod,
                            "تاریخ پرداخت" to local.paymentDate,
                            "شماره پیگیری" to local.paymentTrackingNumber,
                            "کد شعبه فروشنده" to local.sellerBranchCode,
                            "کد شعبه خریدار" to local.buyerBranchCode,
                            "توضیحات صورتحساب" to local.notes,
                            "یادداشت ۱" to local.note1,
                            "یادداشت ۲" to local.note2,
                            "شرح تکمیلی" to local.invoiceDescription,
                            "ارسال آتی (فقط ثبت محلی)" to
                                if (local.sendLaterDate.isBlank()) "" else "${local.sendLaterDate} ${local.sendLaterTime}",
                            "صورتحساب ماده ۹ (علامت‌گذاری محلی)" to
                                if (local.article9Flag) "فعال" else ""
                        ).filter { it.second.isNotBlank() }
                        if (extra.isNotEmpty()) {
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("جزئیات پرداخت و توضیحات", fontWeight = FontWeight.Bold)
                                    extra.forEach { (title, value) -> Text("${title}: ${value}") }
                                }
                            }
                        }
                        if (local.documents.isNotEmpty()) {
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("اسناد پیوست‌شده (فقط در دستگاه)",
                                        fontWeight = FontWeight.SemiBold)
                                    local.documents.forEach { ref ->
                                        OutlinedButton(onClick = {
                                            runCatching { InvoiceDocumentPicker.open(context, ref) }
                                                .onFailure { onNotice("نمایش سند ممکن نیست؛ فایل یا مجوز دسترسی را بررسی کنید.") }
                                        }) { Text(ref.name) }
                                    }
                                }
                            }
                        }
                        InvoiceEditorItemsTable(loaded.lines, enabled = false,
                            onEdit = {}, onCopy = {}, onDelete = {})
                        TotalsCard(loaded.lines, invoice)
                        OutlinedButton(onClick = {
                            perform {
                                templateCompany = db.companyDao().byId(companyId)
                                    ?: throw IllegalStateException("مؤدی یافت نشد.")
                                mode = "template"
                            }
                        }) { Text("پیش‌نمایش و PDF فاکتور") }
                        Text(invoiceTaxStatus(officialRecord),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (officialRecord?.let(OfficialReferenceEvidence::eligible) == true)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                        officialRecord?.takeUnless(::isLegacyEvidenceMessage)?.lastError?.takeIf { it.isNotBlank() }?.let {
                            Text(it.take(300), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        val canEditUnsent = invoice.trackingCode.isNullOrBlank() &&
                            (invoice.status == "DRAFT" && officialRecord == null ||
                                invoice.status == "FINALIZED" && (officialRecord == null || officialRecord?.state == "PREPARED"))
                        if (canEditUnsent) {
                            Button(enabled = !busy, onClick = { editExistingOrCopy(loaded) },
                                modifier = Modifier.fillMaxWidth()) { Text(if (invoice.status == "DRAFT") "ویرایش پیش‌نویس" else "ویرایش فاکتور") }
                            if(invoice.status == "DRAFT") TextButton(enabled = !busy, onClick = { actionId = invoice.id; confirmation = "delete" }) {
                                Text("حذف پیش‌نویس", color = MaterialTheme.colorScheme.error)
                            }
                        } else if (officialRecord?.state == "REJECTED") {
                            Text("سند ردشده محفوظ می‌ماند. برای اصلاح، نسخه جدید با شماره جدید ایجاد می‌شود؛ ذخیره آن نیازمند اعتبار است.",
                                style = MaterialTheme.typography.bodySmall)
                            Button(enabled = !busy, onClick = { editExistingOrCopy(loaded) },
                                modifier = Modifier.fillMaxWidth()) { Text("ویرایش و ایجاد نسخه جدید") }
                        } else Text("سند ارسال‌شده قابل ویرایش یا حذف نیست.")
                    } else if (!busy) OutlinedButton(onClick = { mode = "list" }) { Text("بازگشت و تلاش مجدد") }
                    Spacer(Modifier.height(16.dp))
                }
            }
            "edit" -> {
                val local = remember(localDetailsJson) { InvoiceLocalDetails.fromJson(localDetailsJson) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InvoiceEditorHeading(seller = sellerName, editingDraft = invoiceId != null, lineCount = lines.size)
                    InvoiceEditorProgress(
                        headerReady = date.isNotBlank() && (automaticNumber || number.isNotBlank()),
                        buyerReady = customerId != null,
                        hasItems = lines.isNotEmpty(),
                        settlementReady = settlementMode != InvoiceSettlement.MIXED || cashPortionInput.isNotBlank()
                    )
                    if (invoiceId == null && !paidLoading && !promoLoading && usableCharge == 0L) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                            Text("فرم بدون اعتبار قابل تکمیل است؛ هنگام ذخیره، اعتبار سرور بررسی می‌شود.",
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    InvoiceEditorPanel("۱", "مشخصات صورتحساب", "تاریخ، موضوع، سریال، الگو، مشتری و نوع") {
                        if (editorLocked) Text("درخواست صدور ناتمام محفوظ است و برای جلوگیری از مصرف مجدد اعتبار، قابل تغییر نیست.")
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth >= 440.dp) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(date, { date = it }, enabled = !busy && !editorLocked,
                                        label = { Text("تاریخ شمسی، نمونه: ۱۴۰۵/۰۷/۰۲") },
                                        singleLine = true, modifier = Modifier.weight(1f),
                                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
                                    OutlinedTextField(number, { number = it }, enabled = !busy && !editorLocked,
                                        label = { Text("شماره فاکتور") }, singleLine = true,
                                        supportingText = { if (automaticNumber) Text("سریال پیشنهادی قابل ویرایش است؛ یکتایی هنگام ذخیره کنترل می‌شود") },
                                        modifier = Modifier.weight(1f))
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(date, { date = it }, enabled = !busy && !editorLocked,
                                    label = { Text("تاریخ شمسی، نمونه: ۱۴۰۵/۰۷/۰۲") }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
                                OutlinedTextField(number, { number = it }, enabled = !busy && !editorLocked,
                                    label = { Text("شماره فاکتور") },
                                    supportingText = { if (automaticNumber) Text("سریال پیشنهادی قابل ویرایش است؛ برای شماره خودکار خالی بگذارید") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        Surface(Modifier.fillMaxWidth(), color = androidx.compose.ui.graphics.Color(0xFFF1F7FA),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                            Text("فروشنده / مؤدی: ${sellerName.ifBlank { "مؤدی انتخاب‌شده" }}",
                                modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                        InvoiceSupportedTaxFormat()
                        Text("ارسال رسمی نوع اول، الگوی فروش و موضوع اصلی با تسویه نقدی، نسیه یا ترکیبی پس از اعتبارسنجی مبلغ فعال است.",
                            color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        var expandedBuyer by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(enabled = !busy && !editorLocked,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { expandedBuyer = true }) {
                                Text("مشتری: " + (customers.firstOrNull { it.id == customerId }?.name ?: "انتخاب کنید"))
                            }
                            DropdownMenu(expandedBuyer, onDismissRequest = { expandedBuyer = false }) {
                                customers.filter{it.id !in inactiveBuyers || (invoiceId!=null && it.id==customerId)}.forEach { customer ->
                                    DropdownMenuItem(text = { Text(customer.name) }, onClick = {
                                        customerId = customer.id
                                        referenceTaxId = ""
                                        expandedBuyer = false
                                    })
                                }
                            }
                        }
                        val selectedBuyer = customers.firstOrNull { it.id == customerId }
                        if (selectedBuyer != null) {
                            Surface(Modifier.fillMaxWidth(),
                                color = androidx.compose.ui.graphics.Color(0xFFF1F7FA),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("مشخصات خریدار انتخاب‌شده", fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall)
                                    Text(selectedBuyer.name, style = MaterialTheme.typography.bodyMedium)
                                    if (selectedBuyer.nationalId.isNotBlank())
                                        Text("شناسه ملی/کد ملی: ${selectedBuyer.nationalId}",
                                            style = MaterialTheme.typography.bodySmall)
                                    if (selectedBuyer.economicId.isNotBlank())
                                        Text("کد اقتصادی: ${selectedBuyer.economicId}",
                                            style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    Text("موضوع صورتحساب", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = subject == InvoiceSubject.ORIGINAL,
                            enabled = !busy && !editorLocked,
                            onClick = { subject = InvoiceSubject.ORIGINAL;referenceTaxId = "" },
                            label = { Text("اصلی") })
                        FilterChip(selected = subject == InvoiceSubject.CORRECTION,
                            enabled = !busy && !editorLocked && acceptedOriginals.isNotEmpty(),
                            onClick = { subject = InvoiceSubject.CORRECTION;referenceTaxId = "";lines = emptyList() },
                            label = { Text("اصلاحی (آزمایشی)") })
                        FilterChip(selected = subject == InvoiceSubject.RETURN,
                            enabled = !busy && !editorLocked && acceptedOriginals.isNotEmpty(),
                            onClick = { subject = InvoiceSubject.RETURN; referenceTaxId = ""; lines = emptyList() },
                            label = { Text("برگشت از فروش (آزمایشی)") })
                        FilterChip(selected = subject == InvoiceSubject.CANCELLATION,
                            enabled = !busy && !editorLocked && acceptedOriginals.isNotEmpty(),
                            onClick = { subject = InvoiceSubject.CANCELLATION; referenceTaxId = ""; lines = emptyList() },
                            label = { Text("ابطالی (آزمایشی)") })
                    }
                    if (subject in setOf(InvoiceSubject.CORRECTION, InvoiceSubject.CANCELLATION, InvoiceSubject.RETURN)) {
                        Text(when (subject) {
                            InvoiceSubject.RETURN -> "برگشت از فروش: اقلام مرجع را کپی کنید و مقدار باقی‌مانده پس از برگشت را وارد کنید. قیمت، نرخ و شناسه کالا نباید تغییر کنند؛ ارسال رسمی آزمایشی است."
                            InvoiceSubject.CANCELLATION -> "ابطالی با شماره مرجع پذیرفته‌شده آماده می‌شود؛ اقلام مرجع را بدون تغییر کپی کنید. ارسال رسمی آزمایشی و نیازمند تأیید شماست؛ ذخیره یک فاکتور جدید اعتبار مصرف می‌کند."
                            else -> "برای اصلاحی، تنها صورتحساب اصلی پذیرفته‌شده همان مؤدی و خریدار قابل ارجاع است؛ ارسال رسمی آزمایشی است."
                        },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        var expandedReference by remember { mutableStateOf(false) }
                        val matches = acceptedOriginals.filter { it.second == customerId }
                        Box {
                            OutlinedButton(enabled = !busy && !editorLocked && matches.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(), onClick = { expandedReference = true }) {
                                Text("شناسه مالیاتی مرجع: " +
                                    referenceTaxId.ifBlank { "انتخاب از صورتحساب‌های پذیرفته‌شده" })
                            }
                            DropdownMenu(expandedReference, { expandedReference = false }) {
                                matches.forEach { (taxId, _) ->
                                    DropdownMenuItem(text = { Text(taxId) }, onClick = {
                                        if (referenceTaxId != taxId) lines = emptyList()
                                        referenceTaxId = taxId;expandedReference = false
                                    })
                                }
                            }
                        }
                        if (matches.isEmpty()) Text("برای این مشتری صورتحساب اصلی پذیرفته‌شده‌ای وجود ندارد.",
                            style = MaterialTheme.typography.bodySmall)
                        if (referenceTaxId.isNotBlank() && lines.isEmpty()) {
                            OutlinedButton(enabled = !busy && !editorLocked,
                                modifier = Modifier.fillMaxWidth(), onClick = {
                                    perform {
                                        val reference = db.officialSubmissionDao().forCompany(companyId)
                                            .firstOrNull {
                                                it.taxId == referenceTaxId && OfficialReferenceEvidence.eligible(it)
                                            } ?: throw IllegalArgumentException("صورتحساب مرجع پذیرفته‌شده پیدا نشد.")
                                        require(reference.ownerId == account.ownerId) {
                                            "مرجع متعلق به حساب فعلی نیست."
                                        }
                                        val original = repository.detailsForCompany(companyId, reference.invoiceId)
                                        require(original.invoice.customerId == customerId &&
                                            original.invoice.subject == InvoiceSubject.ORIGINAL &&
                                            original.invoice.status == "FINALIZED") {
                                            "مشتری یا وضعیت صورتحساب مرجع معتبر نیست."
                                        }
                                        val copied = original.lines.map { it.copy(id = 0, invoiceId = 0) }
                                        InvoiceCalculator.calculate(copied)
                                        lines = copied
                                        onNotice(when (subject) {
                                            InvoiceSubject.RETURN -> "اقلام مرجع کپی شد؛ مقدار باقی‌مانده پس از برگشت را کاهش دهید. ثبت محلی است."
                                            InvoiceSubject.CANCELLATION -> "اقلام مرجع کپی شد؛ برای ابطال رسمی تنها سرآمد و شماره مرجع ارسال می‌شود و تأیید دستی الزامی است."
                                            else -> "اقلام مرجع بارگذاری شد؛ اکنون مبلغ و مقدار را اصلاح کنید. هنوز فاکتوری ثبت نشده است."
                                        })
                                    }
                                }) { Text(when (subject) {
                                    InvoiceSubject.RETURN -> "کپی اقلام مرجع برای برگشت از فروش"
                                    InvoiceSubject.CANCELLATION -> "کپی بدون تغییر اقلام مرجع برای ابطال محلی"
                                    else -> "کپی اقلام صورتحساب مرجع برای اصلاح"
                                }) }
                        }
                    } else if (acceptedOriginals.isEmpty()) {
                        Text("صورتحساب ارجاعی پس از پذیرش صورتحساب اصلی و ثبت رسید معتبر در دسترس می‌شود.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    }
                    InvoiceEditorPanel("۲", "انتخاب کالا/خدمت", "شناسه را انتخاب کرده و مشخصات قلم را وارد کنید") {
                        Button(enabled = !busy && !editorLocked && subject != InvoiceSubject.CANCELLATION && lines.size < 500,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { lineIndex = -1; duplicateLineIndex = -1; lineEditorZone = "pick"; lineDialog = true }) {
                            Text("افزودن کالا یا خدمت")
                        }
                        if (lineDialog) {
                            LineEditor(ownerId = ownerId, companyId = companyId,
                                initial = lines.getOrNull(if (lineIndex >= 0) lineIndex else duplicateLineIndex),
                                onDismiss = { lineDialog = false; duplicateLineIndex = -1 },
                                onSave = { row ->
                                    val next = lines.toMutableList()
                                    if (lineIndex < 0) next.add(row) else next[lineIndex] = row
                                    InvoiceCalculator.calculate(next)
                                    lines = next.toList(); lineDialog = false; duplicateLineIndex = -1
                                })
                        }
                    }
                    InvoiceEditorPanel("۳", "جدول فاکتور", "ثبت ردیف‌ها، اصلاح، حذف و جمع ستون‌ها") {
                        InvoiceEditorItemsTable(
                            lines = lines, enabled = !busy && !editorLocked && subject != InvoiceSubject.CANCELLATION,
                            onEdit = { index ->
                                lineIndex = index; duplicateLineIndex = -1
                                lineEditorZone = "pick"; lineDialog = true
                            },
                            onCopy = { index ->
                                lineIndex = -1; duplicateLineIndex = index
                                lineEditorZone = "pick"; lineDialog = true
                            },
                            onDelete = { index -> lines = lines.filterIndexed { i, _ -> i != index } }
                        )
                    }
                    InvoiceEditorPanel("۴", "اطلاعات تسویه و پرداخت", "روش تسویه، جزئیات پرداخت و توضیحات صورتحساب") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                InvoiceSettlement.CASH to "نقدی",
                                InvoiceSettlement.CREDIT to "نسیه",
                                InvoiceSettlement.MIXED to "ترکیبی"
                            ).forEach { (method, label) ->
                                FilterChip(selected = settlementMode == method,
                                    enabled = !busy && !editorLocked,
                                    onClick = { settlementMode = method; cashPortionInput = "" },
                                    label = { Text(label) })
                            }
                        }
                        if (settlementMode == InvoiceSettlement.MIXED)
                            NumberField(cashPortionInput, { cashPortionInput = it }, "سهم نقدی از مبلغ کل (ریال)")
                        if (settlementMode != InvoiceSettlement.CASH)
                            Text("برای ارسال رسمی، سهم نقدی و نسیه باید مطابق قواعد نسخه ۷.۹ به ریال قابل تفکیک و اعتبارسنجی باشند.",
                                color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        val fieldsEnabled = !busy && !editorLocked
                        OutlinedTextField(local.article17Subject, { localDetailsJson =
                            local.copy(article17Subject = it.take(120)).toJson() }, enabled = fieldsEnabled,
                            label = { Text("موضوع مالیات بند ۱۷") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(local.paymentSwitchNumber, { localDetailsJson =
                            local.copy(paymentSwitchNumber = it.take(120)).toJson() }, enabled = fieldsEnabled,
                            label = { Text("شماره سوئیچ پرداخت") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("روش پرداخت", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("نقدی", "کارت به کارت", "کارتخوان", "واریز بانکی", "سایر").forEach { method ->
                                FilterChip(selected = local.paymentMethod == method, enabled = fieldsEnabled,
                                    onClick = { localDetailsJson = local.copy(paymentMethod = method).toJson() },
                                    label = { Text(method) })
                            }
                        }
                        OutlinedTextField(local.paymentMethod, { localDetailsJson =
                            local.copy(paymentMethod = it.take(120)).toJson() }, enabled = fieldsEnabled,
                            label = { Text("روش پرداخت (قابل ویرایش)") }, modifier = Modifier.fillMaxWidth())
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth >= 440.dp) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(local.paymentDate, { localDetailsJson =
                                        local.copy(paymentDate = it.take(120)).toJson() }, enabled = fieldsEnabled,
                                        label = { Text("تاریخ پرداخت") }, singleLine = true, modifier = Modifier.weight(1f))
                                    OutlinedTextField(local.paymentTrackingNumber, { localDetailsJson =
                                        local.copy(paymentTrackingNumber = it.take(120)).toJson() }, enabled = fieldsEnabled,
                                        label = { Text("شماره پیگیری") }, singleLine = true, modifier = Modifier.weight(1f))
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(local.paymentDate, { localDetailsJson =
                                    local.copy(paymentDate = it.take(120)).toJson() }, enabled = fieldsEnabled,
                                    label = { Text("تاریخ پرداخت") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(local.paymentTrackingNumber, { localDetailsJson =
                                    local.copy(paymentTrackingNumber = it.take(120)).toJson() }, enabled = fieldsEnabled,
                                    label = { Text("شماره پیگیری") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth >= 440.dp) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(local.sellerBranchCode, { localDetailsJson =
                                        local.copy(sellerBranchCode = it.take(120)).toJson() }, enabled = fieldsEnabled,
                                        label = { Text("کد شعبه فروشنده") }, singleLine = true, modifier = Modifier.weight(1f))
                                    OutlinedTextField(local.buyerBranchCode, { localDetailsJson =
                                        local.copy(buyerBranchCode = it.take(120)).toJson() }, enabled = fieldsEnabled,
                                        label = { Text("کد شعبه خریدار") }, singleLine = true, modifier = Modifier.weight(1f))
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(local.sellerBranchCode, { localDetailsJson =
                                    local.copy(sellerBranchCode = it.take(120)).toJson() }, enabled = fieldsEnabled,
                                    label = { Text("کد شعبه فروشنده") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(local.buyerBranchCode, { localDetailsJson =
                                    local.copy(buyerBranchCode = it.take(120)).toJson() }, enabled = fieldsEnabled,
                                    label = { Text("کد شعبه خریدار") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        OutlinedTextField(local.notes, { localDetailsJson = local.copy(notes = it.take(2000)).toJson() },
                            enabled = fieldsEnabled, minLines = 2, maxLines = 4,
                            label = { Text("توضیحات صورتحساب") },
                            modifier = Modifier.fillMaxWidth().testTag("invoiceLocalNotes"))
                        Text("اطلاعات تکمیلی پرداخت محلی‌اند؛ لزوماً به PDF یا پیام رسمی مؤدیان منتقل نمی‌شوند.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    InvoiceEditorPanel("۵", "مدارک، یادداشت‌ها و ارسال در آینده",
                        "پیوست فایل، دو یادداشت و زمان پیشنهادی ارسال") {
                        val fieldsEnabled = !busy && !editorLocked && !documentBusy
                        OutlinedTextField(local.note1, {
                            localDetailsJson = local.copy(note1 = it.take(1200)).toJson()
                        }, enabled = fieldsEnabled, modifier = Modifier.fillMaxWidth(),
                            label = { Text("یادداشت ۱") }, minLines = 2, maxLines = 3)
                        OutlinedTextField(local.note2, {
                            localDetailsJson = local.copy(note2 = it.take(1200)).toJson()
                        }, enabled = fieldsEnabled, modifier = Modifier.fillMaxWidth(),
                            label = { Text("یادداشت ۲") }, minLines = 2, maxLines = 3)
                        Text("برای ارسال رسمی، یادداشت ۱ و ۲ هرکدام حداکثر ۳۰ نویسه دارند؛ متن بلندتر فقط برای ثبت محلی است.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary)
                        OutlinedTextField(local.invoiceDescription, {
                            localDetailsJson = local.copy(invoiceDescription = it.take(120)).toJson()
                        }, enabled = fieldsEnabled, modifier = Modifier.fillMaxWidth(),
                            label = { Text("شرح تکمیلی صورتحساب") })
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("علامت‌گذاری صورتحساب ماده ۹ (محلی)",
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = local.article9Flag,
                                onCheckedChange = { localDetailsJson = local.copy(article9Flag = it).toJson() },
                                enabled = fieldsEnabled)
                        }
                        if (local.article9Flag) Text(
                            "ارسال رسمی سند موضوع ماده ۹ هنوز فعال نیست؛ فقط در دستگاه ثبت می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        Text("پیوست اسناد و مدارک", fontWeight = FontWeight.SemiBold)
                        Text("حداکثر ۵ فایل، هر فایل ۶ مگابایت؛ فقط مرجع سند در برنامه ذخیره می‌شود.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            enabled = fieldsEnabled && local.documents.size < InvoiceDocumentRef.MAX_FILES,
                            onClick = { documentPicker.launch(InvoiceDocumentPicker.acceptedMimeTypes) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (documentBusy) "در حال بررسی فایل..." else "انتخاب و پیوست سند از دستگاه") }
                        local.documents.forEach { ref ->
                            Row(Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(ref.name, modifier = Modifier.weight(1f), maxLines = 2)
                                TextButton(onClick = {
                                    runCatching { InvoiceDocumentPicker.open(context, ref) }
                                        .onFailure { onNotice("سند در دسترس نیست یا برنامه نمایش مناسب نصب نشده است.") }
                                }) { Text("نمایش") }
                                TextButton(enabled = fieldsEnabled, onClick = {
                                    localDetailsJson = local.copy(
                                        documents = local.documents.filterNot { it.uri == ref.uri }
                                    ).toJson()
                                }) { Text("حذف") }
                            }
                        }
                        HorizontalDivider()
                        Text("تنظیم ارسال در آینده (اختیاری)", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(local.sendLaterDate, {
                            localDetailsJson = local.copy(sendLaterDate = it.take(16)).toJson()
                        }, enabled = fieldsEnabled, modifier = Modifier.fillMaxWidth(),
                            label = { Text("تاریخ شمسی ارسال در آینده (مثال: ۱۴۰۵/۰۹/۰۱)") },
                            singleLine = true)
                        OutlinedTextField(local.sendLaterTime, {
                            localDetailsJson = local.copy(sendLaterTime = it.take(8)).toJson()
                        }, enabled = fieldsEnabled, modifier = Modifier.fillMaxWidth(),
                            label = { Text("ساعت ارسال (HH:mm)") }, singleLine = true)
                        TextButton(enabled = fieldsEnabled && (local.sendLaterDate.isNotBlank() ||
                            local.sendLaterTime.isNotBlank()), onClick = {
                            localDetailsJson = local.copy(sendLaterDate = "", sendLaterTime = "").toJson()
                        }) { Text("پاک‌کردن زمان ارسال") }
                        Text("تاریخ و ساعت فعلاً فقط در سند ذخیره می‌شوند؛ ارسال خودکار به سامانه مؤدیان فعال نیست.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    InvoiceEditorPanel("۶", "ذخیره و پیش‌نمایش فاکتور", "کنترل نهایی پیش از مصرف اعتبار و ثبت") {
                        InvoiceLiveOutline(
                            seller = sellerName,
                            buyer = customers.firstOrNull { it.id == customerId }?.name,
                            invoiceNumber = number,
                            issueDate = date,
                            lines = lines
                        )
                        OutlinedButton(enabled = lines.isNotEmpty(), onClick = { previewDialog = true },
                            modifier = Modifier.fillMaxWidth()) { Text("پیش‌نمایش فاکتور") }
                        Text("پیش‌نمایش بدون ثبت و مصرف اعتبار است. پس از ذخیره پیش‌نویس، PDF از صفحه جزئیات قابل دریافت است.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        if (mode == "details" && details != null) {
            val loaded = requireNotNull(details)
            val row = officialRecord
            if (loaded.invoice.status in setOf("DRAFT", "FINALIZED") &&
                (row == null || row.state in setOf("PREPARED", "SENDING", "UNKNOWN", "SUBMITTED"))) {
                Button(enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("invoiceDirectSend"),
                    onClick = { requestOfficialSend(loaded) }) {
                    Text(if (row?.state in setOf("SENDING", "UNKNOWN", "SUBMITTED"))
                        "استعلام وضعیت ارسال" else "ارسال به سامانه مؤدیان")
                }
            }
        }
        if (mode == "edit") {
            InvoiceEditorDock(
                total = if (lines.isEmpty()) null else
                    runCatching { InvoiceCalculator.calculate(lines).totalAmountRial }.getOrNull(),
                saving = busy || documentBusy,
                canSave = lines.isNotEmpty() && !documentBusy,
                onSave = { saveCurrentInvoice() },
                onCancel = { confirmation = "leave" },
                onPreview = { previewDialog = true }
            )
        }
    }

    if (previewDialog && mode == "edit") {
        val localPreview = remember(localDetailsJson) { InvoiceLocalDetails.fromJson(localDetailsJson) }
        AlertDialog(
            onDismissRequest = { previewDialog = false },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("پیش‌نمایش محلی صورتحساب") },
            text = {
                Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("فروشنده: ${sellerName.ifBlank { "مؤدی انتخاب‌شده" }}")
                    Text("مشتری: ${customers.firstOrNull { it.id == customerId }?.name ?: "انتخاب‌نشده"}")
                    Text("نوع اول | الگوی ۱ | موضوع: ${InvoiceSubject.label(subject)}")
                    Text("سریال: ${number.ifBlank { "خودکار" }} | تاریخ: ${date.ifBlank { "نامشخص" }}")
                    InvoiceEditorItemsTable(lines, enabled = false,
                        onEdit = {}, onCopy = {}, onDelete = {})
                    InvoiceLiveOutline(sellerName, customers.firstOrNull { it.id == customerId }?.name,
                        number, date, lines)
                    Text("روش تسویه: ${InvoiceSettlement.label(settlementMode)}")
                    if (localPreview.paymentMethod.isNotBlank()) Text("روش پرداخت: ${localPreview.paymentMethod}")
                    if (localPreview.paymentDate.isNotBlank()) Text("تاریخ پرداخت: ${localPreview.paymentDate}")
                    if (localPreview.paymentTrackingNumber.isNotBlank()) Text("شماره پیگیری: ${localPreview.paymentTrackingNumber}")
                    if (localPreview.notes.isNotBlank()) Text("توضیحات: ${localPreview.notes}")
                    if (localPreview.note1.isNotBlank()) Text("یادداشت ۱: ${localPreview.note1}")
                    if (localPreview.note2.isNotBlank()) Text("یادداشت ۲: ${localPreview.note2}")
                    if (localPreview.documents.isNotEmpty())
                        Text("تعداد پیوست‌های محلی: ${localPreview.documents.size}")
                    if (localPreview.sendLaterDate.isNotBlank())
                        Text("زمان پیشنهادی ارسال: ${localPreview.sendLaterDate} ${localPreview.sendLaterTime} (ارسال خودکار غیرفعال)")
                    Text("این پیش‌نمایش محلی است؛ نه PDF نهایی و نه رسید ارسال سامانه مؤدیان.",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = { TextButton(onClick = { previewDialog = false }) { Text("بازگشت به فاکتور") } }
        )
    }

    if (sendConfirm && details != null) {
        val loaded = requireNotNull(details)
        val invoice = loaded.invoice
        val today = Instant.now().atZone(tehran).toLocalDate()
        val historical = runCatching { JalaliCalendar.toGregorian(invoice.issueDate) != today }.getOrDefault(true)
        val needsTime = historical && officialRecord == null
        AlertDialog(
            onDismissRequest = { if (!busy) sendConfirm = false },
            title = { Text("ارسال به سامانه مؤدیان") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("فاکتور ${invoice.invoiceNumber} | ${InvoiceSubject.label(invoice.subject)}")
                    Text("مؤدی: $sellerName")
                    Text("خریدار: ${customers.firstOrNull { it.id == invoice.customerId }?.name ?: invoice.customerId}")
                    Text("تاریخ: ${invoice.issueDate}")
                    Text("مبلغ: ${rial(invoice.totalAmountRial)}")
                    Text("مالیات: ${rial(invoice.taxAmountRial)}")
                    Text("تسویه: ${InvoiceSettlement.label(invoice.settlementMode)}")
                    if (needsTime) OutlinedTextField(issueClock, { issueClock = it },
                        label = { Text("ساعت واقعی صدور (مثلاً ۱۴:۳۰)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    else if (officialRecord == null) Text("زمان صدور: زمان تأیید، به وقت تهران.")
                    Text("با تأیید، صورتحساب رسمی ارسال و امکان ویرایش آن محدود می‌شود.")
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && (!needsTime || issueClock.isNotBlank()), onClick = {
                    sendConfirm = false
                    sendConfirmed(invoice.id, issueClock)
                }) { Text("تأیید و ارسال") }
            },
            dismissButton = { TextButton(onClick = { sendConfirm = false }) { Text("انصراف") } }
        )
    }

    if (confirmation.isNotEmpty()) AlertDialog(
        onDismissRequest = { if (!busy) confirmation = "" },
        title = { Text(if (confirmation == "delete") "حذف پیش‌نویس؟" else "خروج از ویرایش؟") },
        text = { Text(when (confirmation) {
            "delete" -> "پیش‌نویس لغو می‌شود؛ سوابق و مصرف اعتبار آن باقی می‌ماند."
            else -> if(editorLocked) "درخواست ناتمام محفوظ می‌ماند و با همان اطلاعات قابل ادامه است." else "تغییرات ذخیره‌نشده کنار گذاشته می‌شوند."
        }) },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val requested = confirmation
            if (requested == "leave") { confirmation = ""; if (invoiceId == null) backFromInvoice() else mode = "details"; error = "" }
            else perform {
                val id = actionId ?: throw IllegalArgumentException("فاکتور را دوباره انتخاب کنید.")
                require(requested == "delete")
                repository.deleteDraft(companyId, id)
                confirmation = ""
                details = null; mode = "list"
                onChanged()
                onNotice("پیش‌نویس لغو شد؛ شارژ مصرف‌شده بازگردانده نمی‌شود.")
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LineEditor(ownerId: String, companyId: Long, initial: InvoiceLine?,
    onDismiss: () -> Unit, onSave: (InvoiceLine) -> Unit
) {
    var code by rememberSaveable { mutableStateOf(initial?.itemCode.orEmpty()) }
    var itemTitle by rememberSaveable { mutableStateOf(initial?.itemTitle.orEmpty().ifBlank { initial?.description.orEmpty() }) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(initial?.let(InvoiceLineAmounts::quantityText) ?: "1") }
    var unit by rememberSaveable { mutableStateOf(initial?.unitCode.orEmpty()) }
    var price by rememberSaveable { mutableStateOf(initial?.unitPriceRial?.toString().orEmpty()) }
    var discount by rememberSaveable { mutableStateOf(initial?.discountRial?.toString() ?: "0") }
    var tax by rememberSaveable { mutableStateOf(initial?.let(InvoiceLineAmounts::vatText).orEmpty()) }
    var vatHint by rememberSaveable { mutableStateOf("") }
    var identifierLocked by rememberSaveable { mutableStateOf(initial?.itemCode?.matches(Regex("[0-9]{13}")) == true) }
    var rateNeedsConfirmation by rememberSaveable { mutableStateOf(false) }
    var rateConfirmed by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    val headerRequester = remember { BringIntoViewRequester() }
    // Editing from the wide grid opens this form below the table. Explicitly
    // reveal the form heading so users do not think the edit button failed.
    LaunchedEffect(initial) { headerRequester.bringIntoView() }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("invoiceInlineLineEditor"),
        color = androidx.compose.ui.graphics.Color(0xFFF2F8FA),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFDCE8EF))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(if (initial == null) "مشخصات ردیف جدید" else "اصلاح مشخصات کالا/خدمت",
                modifier = Modifier.bringIntoViewRequester(headerRequester).testTag("invoiceLineEditorHeading"),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Text("۱. شناسه کالا یا خدمت را انتخاب کنید؛ نرخ مالیات و عنوان آن از بانک خوانده می‌شود.",
                style = MaterialTheme.typography.labelSmall)
            InvoiceIdentifierPicker(ownerId = ownerId, companyId = companyId, enabled = true) { identifier ->
                try {
                    val selected = InvoiceIdentifierPrefill.from(identifier)
                    code = selected.code
                    itemTitle = selected.description
                    if (description.isBlank()) description = selected.description
                    tax = selected.vatRatePercent
                    identifierLocked = true
                    rateNeedsConfirmation = selected.requiresVatReview && tax.isNotBlank()
                    rateConfirmed = false
                    vatHint = when {
                        tax.isBlank() -> "نرخ معتبر در بانک ثبت نشده است؛ نرخ را در بخش شناسه‌های مؤدی تکمیل و دوباره انتخاب کنید."
                        rateNeedsConfirmation -> "نرخ گزارش‌شده است؛ قبل از ثبت ردیف با مرجع رسمی تطبیق دهید."
                        else -> "نرخ ثبت‌شده شناسه به‌صورت خودکار اعمال شد."
                    }
                    error = ""
                } catch (e: IllegalArgumentException) {
                    error = e.message ?: "شناسه قابل انتخاب نیست."
                }
            }
            OutlinedTextField(code, { code = InvoiceInput.digits(it).trim() },
                label = { Text("شناسه کالا و خدمت") }, singleLine = true, readOnly = identifierLocked,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tax, { if (!identifierLocked) { tax = it; vatHint = "" } },
                label = { Text("درصد مالیات (تا ۲ رقم اعشار)") }, singleLine = true,
                readOnly = identifierLocked, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("invoiceVatRate"))
            if (identifierLocked && rateNeedsConfirmation) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = rateConfirmed, onCheckedChange = { rateConfirmed = it })
                Text("نرخ گزارش‌شده را با مرجع رسمی کنترل و تأیید کردم.")
            }
            if (vatHint.isNotBlank()) Text(vatHint, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(itemTitle, { itemTitle = it },
                label = { Text("عنوان کالا/خدمت") }, singleLine = true, readOnly = identifierLocked,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it },
                label = { Text("شرح کالا یا خدمت") },
                supportingText = { Text("همین شرح در خروجی PDF و صورتحساب نمایش داده می‌شود.") },
                modifier = Modifier.fillMaxWidth())
            InvoiceMeasurementUnitPicker(unit) { unit = it; error = "" }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 380.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(quantity, { quantity = it }, "مقدار/تعداد", modifier = Modifier.weight(1f))
                        NumberField(price, { price = it }, "قیمت واحد به ریال", modifier = Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(quantity, { quantity = it }, "تعداد / مقدار (تا ۸ رقم اعشار)")
                        NumberField(price, { price = it }, "قیمت واحد به ریال")
                    }
                }
            }
            NumberField(discount, { discount = it }, "تخفیف کل این ردیف به ریال")
            // Local, read-only preview: never spends credit or calls the tax service.
            val provisional = remember(code, description, quantity, price, discount, tax, unit) {
                runCatching {
                    val row = InvoiceLineAmounts.fromUserInput(
                        code = code, description = description, rawQuantity = quantity,
                        price = InvoiceInput.wholeNumber(price, "قیمت"),
                        discount = InvoiceInput.wholeNumber(discount, "تخفیف"),
                        rawRate = tax, unit = unit)
                    InvoiceCalculator.calculate(listOf(row))
                }.getOrNull()
            }
            if (provisional != null) {
                val gross = runCatching {
                    Math.addExact(provisional.netAmountRial, InvoiceInput.wholeNumber(discount, "تخفیف"))
                }.getOrNull()
                Surface(Modifier.fillMaxWidth(),
                    color = androidx.compose.ui.graphics.Color(0xFFE5F5F0),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("محاسبه همین ردیف پیش از افزودن", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall)
                        if (gross != null) Text("مبلغ قبل از تخفیف: ${rial(gross)}")
                        Text("خالص پس از تخفیف: ${rial(provisional.netAmountRial)}")
                        Text("مبلغ مالیات: ${rial(provisional.taxAmountRial)}")
                        Text("مبلغ کل کالا/خدمت: ${rial(provisional.totalAmountRial)}",
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text("با ثبت ردیف، این قلم به جدول فاکتور اضافه می‌شود؛ هنوز فاکتوری ذخیره نشده است.",
                style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        try {
                            require(!rateNeedsConfirmation || rateConfirmed) { "ابتدا نرخ گزارش‌شده را تأیید کنید." }
                            require(!identifierLocked || tax.isNotBlank()) { "نرخ این شناسه در بانک مشخص نیست." }
                            val row = InvoiceLineAmounts.fromUserInput(
                                code = code, description = description,
                                rawQuantity = quantity,
                                price = InvoiceInput.wholeNumber(price, "قیمت"),
                                discount = InvoiceInput.wholeNumber(discount, "تخفیف"),
                                rawRate = tax, unit = unit
                            ).copy(itemTitle = itemTitle.trim().ifBlank { description.trim() })
                            InvoiceCalculator.calculate(listOf(row))
                            onSave(row)
                        } catch (e: ArithmeticException) {
                            error = "مبلغ ردیف یا جمع فاکتور بیش از محدوده مجاز است."
                        } catch (e: IllegalArgumentException) {
                            error = e.message ?: "اطلاعات ردیف معتبر نیست."
                        }
                    }) { Text("ثبت ردیف") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(0.7f)) {
                    Text("انصراف")
                }
            }
        }
    }
}

/** Select an official code; never infer a unit from the item description. */
@Composable
private fun InvoiceMeasurementUnitPicker(unit: String, onSelect: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val name = MoadianMeasurementUnits.nameOf(unit)
    Text("واحد اندازه‌گیری", style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold)
    OutlinedButton(
        onClick = { query = ""; open = true },
        modifier = Modifier.fillMaxWidth().testTag("invoiceUnitPicker")
    ) {
        Text(name ?: "انتخاب واحد اندازه‌گیری")
    }
    if (name == null && unit.isNotBlank()) Text("کد قدیمی معتبر نیست؛ واحد صحیح را انتخاب کنید.",
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    Text("برای ارسال رسمی، انتخاب واحد معتبر هر قلم ضروری است.",
        style = MaterialTheme.typography.bodySmall)
    Row(Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf("1627", "164", "165", "1637", "16103", "1653").forEach { code ->
            FilterChip(selected = unit == code, onClick = { onSelect(code) },
                label = { Text(requireNotNull(MoadianMeasurementUnits.nameOf(code))) })
        }
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("انتخاب واحد اندازه‌گیری") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, singleLine = true,
                    label = { Text("جستجوی واحد اندازه‌گیری") },
                    placeholder = { Text("مثلاً عد، کیلو، نفر") },
                    modifier = Modifier.fillMaxWidth().testTag("invoiceUnitSearch"))
                val matches = remember(query) { MoadianMeasurementUnits.searchByName(query) }
                Text("${matches.size} واحد سنجش", style = MaterialTheme.typography.bodySmall)
                if (matches.isEmpty()) Text("واحدی یافت نشد.")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)
                    .testTag("invoiceUnitResults")) {
                    items(matches, key = { it.code }) { option ->
                        TextButton(onClick = { onSelect(option.code); open = false },
                            modifier = Modifier.fillMaxWidth().testTag("invoiceUnit_${option.code}")) {
                            Text(option.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("بستن") } }
    )
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
        modifier = modifier)
}
