package ir.saba.invoice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.*
import ir.saba.invoice.security.*
import ir.saba.invoice.domain.InvoiceSettlement
import ir.saba.invoice.domain.InvoiceSubject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun OfficialSubmissionScreen(db: SabaDatabase, account: AccountScope, companyId: Long, onNotice: (String) -> Unit, onChanged: suspend () -> Unit = {}, accessToken: String = "") {
    key(account.ownerId,companyId) { OfficialSubmissionContent(db,account,companyId,onNotice,onChanged,accessToken) }
}
@Composable
private fun OfficialSubmissionContent(db: SabaDatabase, account: AccountScope, companyId: Long, onNotice: (String) -> Unit, onChanged: suspend () -> Unit, accessToken: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember(db,account,accessToken) { OfficialSubmissionService(db,account,TaxpayerCredentialRepository(context,db,account),sessionCheck={AccountSession.requireOwner(accessToken,account)}) }
    var invoices by remember { mutableStateOf<List<Invoice>>(emptyList()) }
    var history by remember { mutableStateOf<List<OfficialSubmission>>(emptyList()) }
    var selected by remember { mutableStateOf<Invoice?>(null) }
    var issueTime by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<OfficialSubmission?>(null) }
    var localCredentialReady by remember(companyId) { mutableStateOf(false) }
    suspend fun refresh() {
        history = service.history(companyId)
        val company = requireNotNull(db.companyDao().byId(companyId))
        require(company.purchaserId == account.ownerId)
        name = company.name
        invoices = db.invoiceDao().forCompany(companyId).filter { it.status == "FINALIZED" }
        onChanged()
    }
    fun act(block: suspend () -> Unit) {
        busy = true; error = ""
        scope.launch {
            try { block(); refresh() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is IllegalArgumentException || e is IllegalStateException) e.message.orEmpty().take(300) else "عملیات انجام نشد؛ اتصال و اطلاعات مؤدی را بررسی کنید." }
            finally { busy = false }
        }
    }
    LaunchedEffect(companyId,account) {
        try { refresh() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "اطلاعات مجاز برای این مؤدی در دسترس نیست." }
        finally { busy = false }
    }
    // Read snapshots in composition, rather than deferring state reads inside the lazy DSL.
    val historyRows = history
    val availableInvoices = invoices.filter { i -> historyRows.none { it.invoiceId == i.id } }
    val invoiceRows = invoices
    LazyColumn(Modifier.fillMaxSize().padding(16.dp).testTag("officialSubmissionList").semantics { stateDescription = if (busy) "در حال پردازش" else "پایان پردازش" },verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("ارسال رسمی صورتحساب",style=MaterialTheme.typography.titleLarge)
            Text(name)
            Text("پشتیبانی آزمایشی: نوع ۱، الگوی فروش ۱، ریالی؛ اصلی، اصلاحی و برگشت از فروش جزئی با مرجع پذیرفته‌شده، نقدی/نسیه/ترکیبی. پذیرش واقعی اسناد ارجاعی هنوز آزمون نشده است.")
            Text("ارسال آینده فقط زمان انتخابی محلی است؛ سامانه به‌صورت خودکار سند مالیاتی ارسال نمی‌کند و پس از موعد نیز تأیید دستی الزامی است.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
            if (error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(enabled=!busy,onClick={
                localCredentialReady = false
                act {
                    val credential = service.checkLocalSigningReadiness(companyId)
                    localCredentialReady = true
                    onNotice("کلید خصوصی و گواهی این مؤدی به‌صورت محلی بررسی شد؛ هیچ صورتحسابی ارسال نشده است. اعتبار تا ${java.time.Instant.ofEpochMilli(credential.expiresAtEpochMillis)}")
                }
            },modifier=Modifier.fillMaxWidth()) { Text("بررسی آمادگی امضای مؤدی (بدون ارسال)") }
            if(localCredentialReady) Text(
                "کلید، گواهی و شناسه حافظه در دستگاه تطبیق دارند؛ این نتیجه تأیید سرور مالیاتی یا پذیرش فاکتور نیست.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary
            )
            TextButton(enabled=!busy,onClick={act { }}) { Text("بازخوانی وضعیت محلی") }
        }
        items(availableInvoices,key={"invoice_${it.id}"}) { invoice ->
            val schedule = runCatching {
                InvoiceSendSchedule.label(InvoiceLocalDetails.fromJson(invoice.localDetailsJson))
            }.getOrNull()
            if (schedule != null) Text("موعد انتخابی ارسال دستی: $schedule",
                style=MaterialTheme.typography.bodySmall)
            if (invoice.subject in setOf(InvoiceSubject.ORIGINAL, InvoiceSubject.CORRECTION, InvoiceSubject.RETURN)) {
                OutlinedButton(enabled=!busy,onClick={selected=invoice;issueTime=MoadianInvoiceMapper.defaultIssueTime(invoice.issueDate);unit=""}) {
                    Text("آماده‌سازی فاکتور ${invoice.invoiceNumber}")
                }
            } else {
                Text("فاکتور " + invoice.invoiceNumber +
                    ": این نوع یا موضوع صورتحساب هنوز در مسیر ارسال رسمی پشتیبانی نمی‌شود.",
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        item {
            selected?.let { invoice ->
                Text("فاکتور ${invoice.invoiceNumber} — ${invoice.issueDate} — ${invoice.totalAmountRial} ریال")
                Text("زمان پیشنهادی ساعت ۱۲ تهران است؛ زمان واقعی صدور را با قالب ISO و منطقه زمانی وارد کنید.")
                OutlinedTextField(value=issueTime,onValueChange={issueTime=it},enabled=!busy,label={Text("زمان واقعی صدور ISO")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=unit,onValueChange={unit=ir.saba.invoice.domain.InvoiceInput.digits(it).trim()},enabled=!busy,label={Text("واحد سنجش مشترک فقط برای ردیف‌های قدیمی (اختیاری)")},supportingText={Text("برای ردیف‌های جدید با واحد جداگانه لازم نیست؛ نمونه کد 1627 = عدد")},modifier=Modifier.fillMaxWidth())
                Button(enabled=!busy,onClick={act { service.prepare(companyId,invoice.id,issueTime,unit);selected=null;onNotice("محتوا ثبت شد؛ هنوز ارسال نشده است.") }}) { Text("اعتبارسنجی و ثبت محتوای ثابت") }
            }
        }
        items(historyRows,key={it.requestId}) { row ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("فاکتور ${invoiceRows.find { it.id == row.invoiceId }?.invoiceNumber ?: row.invoiceId}")
                    Text("شماره مالیاتی: ${row.taxId}")
                    Text(when(row.state) {
                        "PREPARED" -> "آماده؛ ارسال نشده"
                        "SUBMITTED" -> "در سامانه در انتظار اعتبارسنجی؛ نتیجه نهایی نشده"
                        "ACCEPTED" -> "پذیرفته‌شده؛ امضای رسید سازمان بررسی شد"
                        "REJECTED" -> "ردشده در سامانه"
                        else -> "نتیجه نامشخص؛ استعلام لازم است"
                    })
                    row.referenceNumber?.let { Text("شماره پیگیری: $it") }
                    row.lastError?.let { Text(it) }
                    if(row.state=="PREPARED") {
                        val related = invoiceRows.firstOrNull { it.id == row.invoiceId }
                        // Invalid or unavailable metadata must not make the send action appear safe.
                        val due = runCatching {
                            related != null && InvoiceSendSchedule.isDue(
                                InvoiceLocalDetails.fromJson(related.localDetailsJson))
                        }.getOrDefault(false)
                        if (!due) {
                            Text("زمان ارسال هنوز نرسیده یا اطلاعات آن معتبر نیست؛ برای ارسال دستی در موعد، وضعیت را بازخوانی کنید.",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.error)
                        }
                        Button(enabled=!busy && due,
                            onClick={act { confirmation=service.reviewPrepared(row.requestId) }}) {
                            Text("بررسی و ارسال رسمی")
                        }
                        TextButton(enabled=!busy,onClick={act { service.discardPrepared(row.requestId) }}) {
                            Text("لغو آماده‌سازی؛ هنوز ارسال نشده")
                        }
                    }
                    if(row.state in setOf("SENDING","UNKNOWN","SUBMITTED")) OutlinedButton(enabled=!busy,onClick={act { service.inquire(row.requestId) }}) { Text("استعلام همان درخواست") }
                }
            }
        }
    }
    confirmation?.let { row ->
        val content = org.json.JSONObject(row.payload)
        val header = content.getJSONObject("header")
        val body = content.getJSONArray("body")
        val units = (0 until body.length()).joinToString("، ") { index ->
            val code = body.getJSONObject(index).getString("mu")
            "${index + 1}: " + (ir.saba.moadian.MoadianMeasurementUnits.nameOf(code) ?: code)
        }
        val subjectName = when(header.getInt("ins")) {
            1 -> "اصلی"
            2 -> "اصلاحی"
            4 -> "برگشت از فروش"
            else -> "نامعتبر"
        }
        AlertDialog(onDismissRequest={confirmation=null},title={Text("تأیید ارسال واقعی به سامانه مؤدیان")},
            text={Text("مؤدی: $name\nشماره مالیاتی: ${row.taxId}\nموضوع: $subjectName\nشناسه مرجع: ${header.optString("irtaxid", "—")}\nمبلغ: ${header.get("tbill")} ریال\nمالیات: ${header.get("tvam")} ریال\nروش تسویه: ${when(header.getInt("setm")) { 1 -> "نقدی"; 2 -> "نسیه"; else -> "ترکیبی" }}\nسهم نقدی بدون مالیات: ${header.opt("cap")} ریال\nسهم نسیه بدون مالیات: ${header.opt("insp") ?: 0} ریال\nخریدار: ${header.getString("bid")}\nواحدهای هر ردیف: $units\nاطلاعات و سهم پرداخت را تأیید می‌کنید؟ این اقدام سند مالیاتی واقعی ارسال می‌کند.")},
            confirmButton={TextButton(onClick={confirmation=null;act { service.submit(row.requestId) }}) { Text("تأیید و ارسال واقعی") }},
            dismissButton={TextButton(onClick={confirmation=null}) { Text("انصراف") }})
    }
}
