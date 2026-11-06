package ir.saba.invoice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.InvoiceLine
import ir.saba.invoice.domain.InvoiceCalculator

private val suraInk = Color(0xFF17354B)
private val suraBlue = Color(0xFF0C5F82)
private val suraTint = Color(0xFFE9F5FA)

@Composable
internal fun InvoiceEditorHeading(
    seller: String,
    editingDraft: Boolean,
    lineCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = suraInk)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (editingDraft) "ویرایش پیش‌نویس صورتحساب" else "تنظیم صورتحساب جدید",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "فروشنده: ${seller.ifBlank { "مؤدی انتخاب‌شده" }}",
                color = Color(0xFFE2F3FA), style = MaterialTheme.typography.bodyMedium
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("فاکتور فروش کالا و خدمات", color = Color(0xFFE2F3FA),
                    style = MaterialTheme.typography.labelMedium)
                Text("$lineCount ردیف", color = Color(0xFFECCF9B),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun InvoiceEditorSection(
    step: String,
    title: String,
    hint: String
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = suraTint, shape = RoundedCornerShape(10.dp)) {
            Text(step, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = suraBlue, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = suraInk, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            Text(hint, color = Color(0xFF627D8E),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Live visual outline only; does not call the server, write a draft, or replace the approved A4 PDF. */
@Composable
internal fun InvoiceLiveOutline(
    seller: String,
    buyer: String?,
    invoiceNumber: String,
    issueDate: String,
    lines: List<InvoiceLine>
) {
    val totals = if (lines.isEmpty()) null else
        runCatching { InvoiceCalculator.calculate(lines) }.getOrNull()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("خلاصه زنده صورتحساب", style = MaterialTheme.typography.titleMedium,
                color = suraBlue, fontWeight = FontWeight.Bold)
            Text("این پیش‌نمایش محتوای فرم است؛ نسخه PDF پس از ثبت پیش‌نویس در دسترس خواهد بود.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFF627D8E))
            HorizontalDivider(color = Color(0xFFE0EBF1))
            Text("فروشنده: ${seller.ifBlank { "مؤدی انتخاب‌شده" }}", color = suraInk)
            Text("خریدار: ${buyer ?: "انتخاب‌نشده"}", color = suraInk)
            Text("شماره: ${invoiceNumber.ifBlank { "خودکار / ثبت‌نشده" }}    تاریخ: ${issueDate.ifBlank { "تعیین‌نشده" }}",
                color = suraInk, style = MaterialTheme.typography.bodySmall)
            Text("تعداد اقلام: ${lines.size}", color = suraInk)
            if (totals == null) {
                Text(if (lines.isEmpty()) "برای محاسبه جمع، کالا یا خدمت اضافه کنید."
                    else "اقلام نیازمند اصلاح‌اند.", color = Color(0xFF937043),
                    style = MaterialTheme.typography.bodySmall)
            } else {
                HorizontalDivider(color = Color(0xFFE0EBF1))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("جمع خالص", color = suraInk)
                    Text(rial(totals.netAmountRial), color = suraInk)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("مالیات", color = suraInk)
                    Text(rial(totals.taxAmountRial), color = suraInk)
                }
                Surface(color = suraTint, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("مبلغ قابل پرداخت", color = suraBlue, fontWeight = FontWeight.Bold)
                        Text(rial(totals.totalAmountRial), color = suraBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
