package ir.saba.invoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.Invoice
import ir.saba.invoice.data.Customer
import ir.saba.invoice.data.OfficialSubmission
import ir.saba.invoice.data.OfficialReferenceEvidence
import java.text.NumberFormat
import java.util.Locale

private val midnight = Color(0xFF051B2D)
private val ocean = Color(0xFF0D475A)
private val teal = Color(0xFF0A8191)
private val gold = Color(0xFFEBCB90)
private val cool = Color(0xFFD8F5F8)
private val textNavy = Color(0xFF16334B)
private val numberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("fa-IR"))

internal enum class InvoiceEventStatus(val label: String) {
    SUCCESS("موفق"), REJECTED("ناموفق"), PENDING("در انتظار پاسخ"), CHECK("نیازمند استعلام"),
    READY("آماده ارسال"), DRAFT("پیش‌نویس"), UNSENT("ارسال نشده"), CANCELLED("لغوشده")
}

/** A successful direct inquiry is not buyer approval or permission to create a referred invoice. */
internal fun invoiceEventStatus(invoice: Invoice, receipt: OfficialSubmission?): InvoiceEventStatus {
    val row = receipt?.takeIf { it.invoiceId == invoice.id && it.companyId == invoice.companyId }
    if(invoice.status=="CANCELLED" && (row==null || row.state=="RESERVED") && invoice.trackingCode.isNullOrBlank()) return InvoiceEventStatus.CANCELLED
    return when (row?.state) {
        "ACCEPTED" -> if (OfficialReferenceEvidence.eligible(row) ||
            (row.protocol == "LEGACY" && !row.referenceNumber.isNullOrBlank() &&
                row.lastError == OfficialReferenceEvidence.LEGACY_UNBOUND))
            InvoiceEventStatus.SUCCESS else InvoiceEventStatus.CHECK
        "REJECTED" -> InvoiceEventStatus.REJECTED
        "SUBMITTED" -> InvoiceEventStatus.PENDING
        "UNKNOWN", "SENDING", "RESTORED" -> InvoiceEventStatus.CHECK
        "RESERVED" -> if(invoice.status=="DRAFT") InvoiceEventStatus.DRAFT else InvoiceEventStatus.UNSENT
        "PREPARED" -> if (row.lastError.isNullOrBlank()) InvoiceEventStatus.READY else InvoiceEventStatus.UNSENT
        null -> when {
            !invoice.trackingCode.isNullOrBlank() || invoice.status == "RESTORED" -> InvoiceEventStatus.CHECK
            invoice.status == "DRAFT" -> InvoiceEventStatus.DRAFT
            invoice.status == "FINALIZED" -> InvoiceEventStatus.UNSENT
            invoice.status == "CANCELLED" -> InvoiceEventStatus.CANCELLED
            else -> InvoiceEventStatus.CHECK
        }
        else -> InvoiceEventStatus.CHECK
    }
}
internal fun invoiceEventOpensPreview(status: InvoiceEventStatus): Boolean = true

/** The posting API reports registration, NOT buyer reaction or statutory cancellation.
 * Compute the link from persisted invoice/reference identity; never overwrite the original receipt. */
data class CorrectionLink(val invoice:Invoice,val label:String)
internal fun correctionLink(original:Invoice, invoices:List<Invoice>, receipts:Map<Long,OfficialSubmission>):CorrectionLink? {
    val parent=receipts[original.id]?.takeIf {
        it.invoiceId==original.id && it.companyId==original.companyId &&
            it.state=="ACCEPTED" && it.taxId.isNotBlank()
    } ?: return null
    val child=invoices.firstOrNull {
        it.id!=original.id && it.companyId==original.companyId && it.customerId==original.customerId &&
            it.subject=="CORRECTION" && it.referenceTaxId==parent.taxId && it.status!="CANCELLED"
    } ?: return null
    val row=receipts[child.id]?.takeIf {
        it.invoiceId==child.id && it.companyId==child.companyId && it.ownerId==parent.ownerId &&
            it.fiscalId==parent.fiscalId
    }
    val label=when(invoiceEventStatus(child,row)) {
        InvoiceEventStatus.SUCCESS -> "اصلاحیه ثبت شده؛ تعیین تکلیف مرجع در کارپوشه"
        InvoiceEventStatus.REJECTED -> "اصلاحیه ناموفق؛ مرجع تغییر نکرده"
        InvoiceEventStatus.PENDING,InvoiceEventStatus.CHECK -> "اصلاحیه در انتظار نتیجه؛ ابطال تأیید نشده"
        else -> "اصلاحیه ارسال نشده؛ مرجع تغییر نکرده"
    }
    return CorrectionLink(child,label)
}

internal fun cancellationLink(original:Invoice,invoices:List<Invoice>,receipts:Map<Long,OfficialSubmission>):CorrectionLink? {
    val parent=receipts[original.id]?.takeIf{it.invoiceId==original.id && it.companyId==original.companyId &&
        it.state=="ACCEPTED" && it.taxId.isNotBlank()} ?: return null
    val child=invoices.firstOrNull{it.id!=original.id && it.companyId==original.companyId &&
        it.customerId==original.customerId && it.subject=="CANCELLATION" &&
        it.referenceTaxId==parent.taxId && it.status!="CANCELLED"} ?: return null
    val row=receipts[child.id]?.takeIf{it.invoiceId==child.id && it.companyId==child.companyId &&
        it.ownerId==parent.ownerId && it.fiscalId==parent.fiscalId}
    val label=when(invoiceEventStatus(child,row)){
        InvoiceEventStatus.SUCCESS->"ابطالی ثبت شده؛ وضعیت نهایی مرجع و واکنش خریدار را در کارپوشه بررسی کنید"
        InvoiceEventStatus.REJECTED->"ابطالی ناموفق؛ مرجع تغییر نکرده"
        InvoiceEventStatus.PENDING,InvoiceEventStatus.CHECK->"ابطالی در انتظار نتیجه؛ ابطال قطعی نیست"
        else->"ابطالی ارسال نشده؛ مرجع تغییر نکرده"
    }
    return CorrectionLink(child,label)
}

/** Only never-posted documents or a definitively rejected attempt may enter editing. */
/** A correction is a NEW referred invoice, not an edit or resend of the original. */
internal fun canStartCorrection(invoice:Invoice, receipt:OfficialSubmission?):Boolean =
    invoice.status=="FINALIZED" && invoice.subject=="ORIGINAL" &&
        receipt?.invoiceId==invoice.id && receipt.companyId==invoice.companyId &&
        receipt.protocol=="LEGACY" && receipt.state=="ACCEPTED" &&
        !receipt.referenceNumber.isNullOrBlank() &&
        (OfficialReferenceEvidence.eligible(receipt) || needsLegacyReferenceCheck(receipt))

internal fun canEditInvoicePreview(invoice: Invoice, receipt: OfficialSubmission?): Boolean {
    if(receipt!=null && (receipt.invoiceId!=invoice.id || receipt.companyId!=invoice.companyId)) return false
    if(receipt?.state=="REJECTED") return invoice.status=="FINALIZED"
    return invoice.trackingCode.isNullOrBlank() &&
        (invoice.status=="DRAFT" && (receipt==null || receipt.state=="RESERVED") ||
         invoice.status=="FINALIZED" && (receipt==null || receipt.state in setOf("PREPARED","RESERVED")))
}

internal fun invoicePreviewAction(invoice:Invoice, receipt:OfficialSubmission?):String? {
    if(receipt!=null && (receipt.invoiceId!=invoice.id || receipt.companyId!=invoice.companyId)) return null
    if(invoice.status !in setOf("DRAFT","FINALIZED")) return null
    return when {
        receipt?.state in setOf("SENDING","UNKNOWN","SUBMITTED") -> if(receipt?.protocol=="LEGACY") "استعلام وضعیت ارسال" else null
        needsLegacyReferenceCheck(receipt) && invoiceEventStatus(invoice,receipt)!=InvoiceEventStatus.SUCCESS -> "استعلام و تطبیق مرجع"
        receipt==null && invoice.trackingCode.isNullOrBlank() ||
            receipt?.state in setOf("PREPARED","RESERVED") -> "ارسال به سامانه مؤدیان"
        else -> null
    }
}

internal fun invoiceEventTitle(invoice: Invoice, customers: List<Customer>): String =
    "صورتحساب " + (customers.firstOrNull { it.id == invoice.customerId && it.companyId == invoice.companyId }
        ?.name?.takeIf { it.isNotBlank() } ?: "مشتری نامشخص")

@Composable
fun EventsScreen(invoices: List<Invoice>, onInvoices: () -> Unit,
                 customers: List<Customer> = emptyList(), receipts: Map<Long, OfficialSubmission> = emptyMap(),
                 onOpenInvoice: (Long) -> Unit = { onInvoices() }) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF2F9FF)),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("رویدادها", style = MaterialTheme.typography.titleMedium,
                    color = textNavy, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = onInvoices) { Text("مشاهده همه", color = teal) }
            }
        }
        if (invoices.isEmpty()) {
            item {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFE7F4F9)), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.List, null, tint = teal)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("هنوز صورتحسابی ثبت نشده", fontWeight = FontWeight.Bold,
                                color = textNavy, style = MaterialTheme.typography.bodyMedium)
                            Text("برای این مودی، اولین صورتحساب را ایجاد کنید.",
                                color = Color(0xFF74869B), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            items(invoices, key = { it.id }) { invoice ->
                val eventStatus = invoiceEventStatus(invoice, receipts[invoice.id])
                val statusText = eventStatus.label
                val statusColor = when (eventStatus) {
                    InvoiceEventStatus.SUCCESS -> Color(0xFF0B806F)
                    InvoiceEventStatus.PENDING, InvoiceEventStatus.CHECK -> Color(0xFFAD792A)
                    InvoiceEventStatus.REJECTED, InvoiceEventStatus.CANCELLED -> Color(0xFFBD4A5E)
                    else -> Color(0xFF59718F)
                }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenInvoice(invoice.id) },
                    shape = RoundedCornerShape(19.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFE4F4F8)), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.List, null, tint = teal)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(invoiceEventTitle(invoice, customers), color = textNavy,
                                fontWeight = FontWeight.Bold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text("${invoice.issueDate} • شماره ${invoice.invoiceNumber}", color = Color(0xFF75899B),
                                style = MaterialTheme.typography.labelSmall)
                            correctionLink(invoice,invoices,receipts)?.let { link ->
                                Text(link.label,color=Color(0xFFAD792A),style=MaterialTheme.typography.labelSmall)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${numberFormat.format(invoice.totalAmountRial)} ریال",
                                color = textNavy, style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold)
                            Text(statusText, color = statusColor,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                .background(Color(0xFFE8F2F7)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("ⓘ", color = Color(0xFF42728C))
                Text("وضعیت بر اساس آخرین پاسخ ثبت‌شده سامانه مؤدیان است. موفق به معنی موفقیت ارسال است، نه تأیید خریدار.",
                    color = Color(0xFF56768C), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
