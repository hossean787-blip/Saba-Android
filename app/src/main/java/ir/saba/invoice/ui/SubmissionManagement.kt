package ir.saba.invoice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ir.saba.invoice.data.*
import ir.saba.invoice.domain.InvoiceSubject
import ir.saba.invoice.domain.JalaliCalendar
import ir.saba.moadian.SubmissionPreparation
import ir.saba.moadian.TaxIdGenerator
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun needsSubmissionManagement(invoice:Invoice,row:OfficialSubmission?):Boolean =
    invoiceEventStatus(invoice,row) in setOf(InvoiceEventStatus.PENDING,InvoiceEventStatus.CHECK,InvoiceEventStatus.REJECTED) ||
        needsLegacyReferenceCheck(row) || (row?.state=="PREPARED" && !row.lastError.isNullOrBlank())

internal fun canCancelNeverSent(invoice:Invoice,row:OfficialSubmission?):Boolean =
    invoice.status in setOf("DRAFT","FINALIZED") && invoice.trackingCode.isNullOrBlank() &&
        (row==null || row.invoiceId==invoice.id && row.companyId==invoice.companyId &&
            row.state in setOf("PREPARED","RESERVED") && row.referenceNumber.isNullOrBlank() && row.signedReceipt.isNullOrBlank())

internal fun canDraftOriginalCancellation(invoice:Invoice,row:OfficialSubmission?,invoices:List<Invoice>):Boolean =
    invoice.status=="FINALIZED" && invoice.subject==InvoiceSubject.ORIGINAL &&
        row?.invoiceId==invoice.id && row.companyId==invoice.companyId && row.protocol=="LEGACY" &&
        OfficialReferenceEvidence.eligible(row) && invoices.none {
            it.companyId==invoice.companyId && it.referenceTaxId==row.taxId && it.status!="CANCELLED"
        }

internal data class SubmissionCheck(val title:String,val ok:Boolean)

internal fun submissionChecks(invoice:Invoice,row:OfficialSubmission?,source:OfficialSubmission?,memory:String?):List<SubmissionCheck> {
    if(row==null) return emptyList()
    fun check(label:String,test:()->Boolean)=SubmissionCheck(label,runCatching(test).getOrDefault(false))
    return listOf(
        check("مالکیت و حافظه درخواست"){row.invoiceId==invoice.id && row.companyId==invoice.companyId && row.fiscalId==memory},
        check("تمامیت محتوای محفوظ"){SubmissionPreparation.sha256Hex(row.payload.toByteArray(Charsets.UTF_8))==row.payloadHash},
        check("شماره مالیاتی، تاریخ و سریال"){val h=JSONObject(row.payload).getJSONObject("header")
            h.getString("taxid")==row.taxId && TaxIdGenerator.generate(row.fiscalId,h.getString("inno").toLong(16),h.getLong("indatim"))==row.taxId},
        check("موضوع و شماره مرجع"){val h=JSONObject(row.payload).getJSONObject("header")
            h.getInt("ins")==InvoiceSubject.officialSubjectCode(invoice) &&
                (if(invoice.subject==InvoiceSubject.ORIGINAL) !h.has("irtaxid")
                else h.getString("irtaxid")==invoice.referenceTaxId && source!=null &&
                    source.taxId==invoice.referenceTaxId && source.companyId==row.companyId &&
                    source.ownerId==row.ownerId && source.fiscalId==row.fiscalId &&
                    OfficialReferenceEvidence.eligible(source))},
        check("مبلغ و ساختار اقلام"){val p=JSONObject(row.payload);val h=p.getJSONObject("header")
            if(invoice.subject==InvoiceSubject.CANCELLATION) p.getJSONArray("body").length()==0
            else p.getJSONArray("body").length()>0 && h.getLong("tbill")==invoice.totalAmountRial && h.getLong("tvam")==invoice.taxAmountRial}
    )
}

private fun managementTime(ms:Long):String {
    val dt=Instant.ofEpochMilli(ms).atZone(ZoneId.of("Asia/Tehran"))
    return JalaliCalendar.fromGregorian(dt.toLocalDate())+" "+dt.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}

@Composable
internal fun SubmissionManagementDialog(invoice:Invoice,row:OfficialSubmission?,db:SabaDatabase,
    ownerId:String,invoices:List<Invoice>,busy:Boolean,onDismiss:()->Unit,onInquiry:()->Unit,
    onCancel:()->Unit,onEdit:()->Unit,onCancellation:()->Unit) {
    var confirm by remember(invoice.id){mutableStateOf("")}
    var checks by remember(invoice.id,row){mutableStateOf<List<SubmissionCheck>?>(null)}
    var history by remember(invoice.id,row){mutableStateOf<List<RejectedSubmissionHistory>?>(null)}
    var loadError by remember(invoice.id,row){mutableStateOf(false)}
    var timeline by remember(invoice.id,row){mutableStateOf<List<SubmissionAudit>>(emptyList())}
    LaunchedEffect(invoice.id,row,ownerId) {
        try {
            require(db.companyDao().byId(invoice.companyId)?.purchaserId==ownerId)
            require(row==null || row.ownerId==ownerId && row.companyId==invoice.companyId && row.invoiceId==invoice.id)
            val source=db.officialSubmissionDao().forCompany(invoice.companyId).firstOrNull{it.taxId==invoice.referenceTaxId && it.ownerId==ownerId}
            checks=submissionChecks(invoice,row,source,db.moadianDetailsDao().byCompany(invoice.companyId)?.fiscalMemoryId)
            history=db.rejectedSubmissionHistoryDao().forInvoice(invoice.id).filter{it.ownerId==ownerId && it.companyId==invoice.companyId}
            timeline=db.submissionAuditDao().recent(invoice.id,ownerId,invoice.companyId)
        } catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){loadError=true}
    }
    val pending=row?.state in setOf("SENDING","UNKNOWN","SUBMITTED")
    val canInquire=row?.protocol=="LEGACY" && (pending || needsLegacyReferenceCheck(row))
    val canCancel=canCancelNeverSent(invoice,row)
    val canAbtal=canDraftOriginalCancellation(invoice,row,invoices)
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},modifier=Modifier.fillMaxWidth().padding(8.dp),
        properties=DialogProperties(usePlatformDefaultWidth=false),
        title={Text("مدیریت و تعیین تکلیف ارسال",style=MaterialTheme.typography.titleLarge)},
        text={
            Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()).testTag("submissionManagementPanel"),verticalArrangement=Arrangement.spacedBy(12.dp)){
                ElevatedCard(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text("فاکتور "+invoice.invoiceNumber+" • "+InvoiceSubject.label(invoice.subject),style=MaterialTheme.typography.titleMedium)
                        Text(if(invoice.status=="CANCELLED") "لغوشده محلی" else invoiceEventStatus(invoice,row).label,style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                        Text(invoiceReceiptLabel(invoice,row),style=MaterialTheme.typography.bodySmall)
                        row?.let {
                            Text("شماره مالیاتی: "+it.taxId,style=MaterialTheme.typography.bodySmall)
                            Text("شماره پیگیری: "+(it.referenceNumber?:"دریافت نشده"),style=MaterialTheme.typography.bodySmall)
                            it.lastError?.takeUnless{_->isLegacyEvidenceMessage(it)}?.let{msg->Text(msg.take(2000),style=MaterialTheme.typography.bodySmall)}
                        }
                        if(pending) Text("درخواست آغاز شده؛ حذف، لغو و ارسال دوباره مجاز نیست.",color=MaterialTheme.colorScheme.error)
                    }
                }
                Text("عملیات مجاز",style=MaterialTheme.typography.titleMedium)
                Button(enabled=!busy && canInquire && !loadError,onClick=onInquiry,modifier=Modifier.fillMaxWidth().testTag("manageInquiry")){Text("استعلام پیشرفته")}
                OutlinedButton(enabled=!busy && canCancel && !loadError,onClick={confirm="cancel"},modifier=Modifier.fillMaxWidth().testTag("manageCancel")){Text("لغو محلی سند ارسال‌نشده")}
                OutlinedButton(enabled=!busy && row?.state=="REJECTED" && canEditInvoicePreview(invoice,row) && !loadError,onClick={confirm="edit"},modifier=Modifier.fillMaxWidth().testTag("manageRetry")){Text("رفع خطا و ویرایش برای ارسال مجدد")}
                OutlinedButton(enabled=!busy && canAbtal && !loadError,onClick={confirm="abtal"},modifier=Modifier.fillMaxWidth().testTag("manageCancellation")){Text("آماده‌سازی صورتحساب ابطالی")}
                if(row?.state=="ACCEPTED" && !canAbtal) Text("ابطال از این صفحه فقط برای مرجع اصلی معتبر و بدون سند ارجاعی فعال فراهم است. وضعیت مرجع و واکنش خریدار را در کارپوشه بررسی کنید.",style=MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("عیب‌یابی محلی بسته ارسالی",style=MaterialTheme.typography.titleMedium)
                if(loadError) Text("بازیابی اطلاعات معتبر انجام نشد؛ عملیات متوقف است.",color=MaterialTheme.colorScheme.error)
                else if(checks==null) Text("در حال بررسی…")
                else if(checks!!.isEmpty()) Text("هنوز بسته رسمی آماده نشده است.")
                else checks!!.forEach{result->
                    Text((if(result.ok) "✓ " else "✕ ")+result.title,
                        color=if(result.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                }
                Text("این کنترل‌ها محلی‌اند و جایگزین پذیرش یا تأیید کارپوشه نیستند.",style=MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("سوابق ارسال",style=MaterialTheme.typography.titleMedium)
                row?.let { Text("آماده‌سازی: "+managementTime(it.preparedAt));Text("آخرین وضعیت: "+invoiceTaxStatus(it),style=MaterialTheme.typography.bodySmall) }
                if(timeline.isEmpty()) Text("رویدادهای دقیق نسخه‌های قبلی ثبت نشده‌اند.",style=MaterialTheme.typography.bodySmall)
                else {
                    Text("۱۰۰ رویداد اخیر ثبت‌شده در این دستگاه",style=MaterialTheme.typography.labelMedium)
                    timeline.forEach{event->
                        OutlinedCard(Modifier.fillMaxWidth()){
                            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
                                Text(when(event.kind){
                                    "SEND_START"->"آغاز ارسال"
                                    "ACK"->"پاسخ اولیه ارسال"
                                    "INQUIRY"->"استعلام"
                                    "REFERENCE_CHECK"->"تطبیق مرجع"
                                    "LOCAL_CANCEL"->"لغو محلی"
                                    "CANCEL_PREPARATION"->"لغو آماده‌سازی"
                                    else->"رویداد"
                                }+" • "+event.state,style=MaterialTheme.typography.titleSmall)
                                Text(managementTime(event.observedAt),style=MaterialTheme.typography.bodySmall)
                                if(event.message.isNotBlank()) Text(event.message,style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                history?.forEach{old->
                    OutlinedCard(Modifier.fillMaxWidth()){
                        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                            Text("تلاش ردشده • "+old.invoiceNumber)
                            Text("شماره مالیاتی: "+old.taxId,style=MaterialTheme.typography.bodySmall)
                            Text("آماده‌سازی: "+managementTime(old.preparedAt),style=MaterialTheme.typography.bodySmall)
                            Text("بایگانی: "+managementTime(old.archivedAt),style=MaterialTheme.typography.bodySmall)
                            old.lastError?.let{Text(it.take(1800),style=MaterialTheme.typography.bodySmall)}
                        }
                    }
                }
                Text("زمان آماده‌سازی با زمان واقعی ارسال متفاوت است. استعلام و عیب‌یابی اعتبار مصرف نمی‌کند.",style=MaterialTheme.typography.bodySmall)
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },confirmButton={TextButton(enabled=!busy,onClick=onDismiss){Text("بازگشت")}})
    if(confirm.isNotEmpty()) AlertDialog(onDismissRequest={confirm=""},title={Text("تأیید عملیات")},
        text={Text(when(confirm){
            "cancel"->"فقط سند ارسال‌نشده لغو محلی می‌شود. شماره مالیاتی و سابقه اعتبار محفوظ می‌ماند؛ اعتبار بازگردانده نمی‌شود."
            "edit"->"تلاش ردشده بایگانی می‌شود و همین فاکتور برای اصلاح باز می‌شود. ارسال بعدی نیازمند تأیید جداگانه است."
            else->"با بررسی کارپوشه تأیید می‌کنم شرایط قانونی ابطال، از جمله عدم استفاده در امور تجاری و پرونده مالیاتی و تأیید لازم طرفین، برقرار است. فقط پیش‌نویس جدید آماده می‌شود؛ ذخیره یک اعتبار مصرف می‌کند و ارسال نیازمند تأیید جداگانه است."
        })},confirmButton={TextButton(enabled=!busy,onClick={
            val action=confirm;confirm=""
            when(action){"cancel"->onCancel();"edit"->onEdit();"abtal"->onCancellation()}
        }){Text("تأیید")}},dismissButton={TextButton(onClick={confirm=""}){Text("انصراف")}})
}
