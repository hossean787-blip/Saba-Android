package ir.saba.invoice.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import java.math.BigDecimal
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.InvoiceLine
import ir.saba.invoice.domain.InvoiceCalculator
import ir.saba.invoice.domain.InvoiceLineAmounts
import ir.saba.moadian.MoadianMeasurementUnits

/** Business-form components only. No database, credit, tax API or PDF mutations. */
private val ink = Color(0xFF132D43)
private val sea = Color(0xFF086C7D)
private val muted = Color(0xFF667D8B)
private val edge = Color(0xFFDCE8EF)
private val ivory = Color(0xFFF5F9FC)
private val gold = Color(0xFFE9C884)

@Composable
internal fun InvoiceEditorHeading(seller: String, editingDraft: Boolean, lineCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("invoiceProfessionalHeader"),
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 5.dp
    ) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(ink, Color(0xFF164F66), Color(0xFF087786))))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (editingDraft) "ویرایش پیش‌نویس صورتحساب" else "تنظیم صورتحساب جدید",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Surface(color = Color(0xFFDCF3EC), shape = RoundedCornerShape(8.dp)) {
                    Text("ثبت محلی", color = Color(0xFF17604C), fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                }
            }
            Text("صورتحساب فروش کالا و خدمات", color = Color(0xFFDFEEF4),
                style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = Color.White.copy(alpha = 0.22f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("فروشنده / مؤدی", color = Color(0xFFB8D8E4),
                        style = MaterialTheme.typography.labelSmall)
                    Text(seller.ifBlank { "مؤدی انتخاب‌شده" }, color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text("تعداد ردیف", color = Color(0xFFB8D8E4),
                        style = MaterialTheme.typography.labelSmall)
                    Text("$lineCount", color = gold, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
internal fun InvoiceEditorProgress(
    headerReady: Boolean, buyerReady: Boolean, hasItems: Boolean, settlementReady: Boolean
) {
    val states = listOf(
        "۱  مشخصات" to (headerReady && buyerReady),
        "۲  انتخاب کالا" to hasItems,
        "۳  جدول فاکتور" to hasItems,
        "۴  تسویه" to settlementReady,
        "۵  پیش‌نمایش" to hasItems
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("invoiceWorkflowProgress"),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        states.forEach { (title, done) ->
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = if (done) Color(0xFFDEF2EC) else Color(0xFFECF2F6),
                border = BorderStroke(1.dp, if (done) Color(0xFFB9DDCF) else edge)
            ) {
                Text(title + if (done) "  ✓" else "",
                    color = if (done) Color(0xFF176753) else muted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp))
            }
        }
    }
}

@Composable
internal fun InvoiceEditorSection(step: String, title: String, hint: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFDBF2EF)) {
            Text(step, color = sea, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = ink)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

@Composable
internal fun InvoiceEditorPanel(
    step: String,
    title: String,
    hint: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("invoiceSection$step"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, edge),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            InvoiceEditorSection(step, title, hint)
            HorizontalDivider(color = edge)
            content()
        }
    }
}

@Composable
internal fun InvoiceEditorEmptyItems() {
    Surface(color = ivory, shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, edge), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("هنوز قلمی در فاکتور نیست", color = ink,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("کالا یا خدمت را از شناسه‌های مؤدی انتخاب کنید یا به‌صورت دستی وارد کنید.",
                color = muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun InvoiceEditorLineRow(
    index: Int,
    row: InvoiceLine,
    enabled: Boolean,
    canCopy: Boolean,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val total = runCatching { InvoiceCalculator.calculate(listOf(row)).totalAmountRial }.getOrNull()
    Card(
        modifier = Modifier.fillMaxWidth().testTag("invoiceItemRow$index"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, edge),
        colors = CardDefaults.cardColors(containerColor = ivory)
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${index + 1}. ${row.description}", color = ink,
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                Text(total?.let(::rial) ?: "نیازمند اصلاح",
                    color = sea, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (row.itemCode.isNotBlank()) {
                Text("شناسه کالا/خدمت: ${row.itemCode}", color = muted,
                    style = MaterialTheme.typography.labelSmall)
            }
            val unit = row.unitCode.takeIf { it.isNotBlank() }
                ?.let { MoadianMeasurementUnits.nameOf(it) ?: it }.orEmpty()
            Text(
                "مقدار ${InvoiceLineAmounts.quantityText(row)} ${unit}  |  نرخ ${rial(row.unitPriceRial)}",
                style = MaterialTheme.typography.bodySmall, color = ink
            )
            Text("تخفیف ${rial(row.discountRial)}  |  مالیات ${InvoiceLineAmounts.vatText(row)}٪",
                style = MaterialTheme.typography.labelSmall, color = muted)
            HorizontalDivider(color = edge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onEdit, enabled = enabled, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)) {
                    Text("ویرایش ردیف", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onCopy, enabled = enabled && canCopy, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)) {
                    Text("کپی ردیف", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onDelete, enabled = enabled, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)) {
                    Text("حذف ردیف", color = Color(0xFFA34D4D),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}


/**
 * Accounting-style, horizontally scrollable invoice grid. Keep fiscal totals
 * sourced from InvoiceCalculator rather than duplicating VAT arithmetic.
 * An aggregate of unit prices is deliberately omitted (different products/units).
 */
@Composable
internal fun InvoiceEditorItemsTable(
    lines: List<InvoiceLine>,
    enabled: Boolean,
    onEdit: (Int) -> Unit,
    onCopy: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    val widths = listOf(54.dp, 166.dp, 208.dp, 114.dp, 130.dp, 130.dp, 140.dp, 132.dp, 160.dp, 188.dp)
    val headers = listOf("ردیف", "عنوان کالا/خدمت", "شرح کالا/خدمت", "مقدار/تعداد",
        "مبلغ فی", "مبلغ تخفیف", "بعد از تخفیف", "مبلغ مالیات",
        "مبلغ کل کالا/خدمت", "عملیات")
    val summary = remember(lines) { if (lines.isEmpty()) null else runCatching { InvoiceCalculator.calculate(lines) }.getOrNull() }
    val discountSum = remember(lines) { runCatching {
        lines.fold(0L) { sum, line -> Math.addExact(sum, line.discountRial) }
    }.getOrNull() }
    val totalQuantity = remember(lines) { if (lines.isEmpty() || lines.map { it.unitCode }.distinct().size != 1) null
        else runCatching { lines.fold(BigDecimal.ZERO) { sum, line ->
            sum.add(InvoiceLineAmounts.effectiveQuantity(line))
        }.stripTrailingZeros().toPlainString() }.getOrNull() }
    Column(Modifier.fillMaxWidth().testTag("invoiceItemsTable"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("برای مشاهده همه ستون‌ها، جدول را به چپ یا راست بکشید.",
            style = MaterialTheme.typography.labelSmall, color = muted)
        Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)) {
            @Composable fun cell(value: String, index: Int, strong: Boolean = false, header: Boolean = false) {
                Box(Modifier.width(widths[index]).heightIn(min = 51.dp)
                    .padding(horizontal = 6.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(value,
                        color = if (header) Color.White else if (strong) sea else ink,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (strong || header) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = if (index in 1..2) 3 else 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            Surface(color = ink, shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    headers.forEachIndexed { idx, label -> cell(label, idx, header = true) }
                }
            }
            lines.forEachIndexed { i, row ->
                val amounts = remember(row) { runCatching { InvoiceCalculator.calculate(listOf(row)) }.getOrNull() }
                Surface(color = if (i % 2 == 0) ivory else Color.White,
                    border = BorderStroke(0.5.dp, edge),
                    modifier = Modifier.testTag("invoiceItemRow$i")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        cell("${i + 1}", 0)
                        cell("${i + 1}. ${row.itemTitle.ifBlank { row.description }}", 1)
                        cell(row.description, 2)
                        cell(InvoiceLineAmounts.quantityText(row), 3)
                        cell(rial(row.unitPriceRial), 4)
                        cell(rial(row.discountRial), 5)
                        cell(amounts?.netAmountRial?.let(::rial) ?: "—", 6)
                        cell(amounts?.taxAmountRial?.let(::rial) ?: "—", 7)
                        cell(amounts?.totalAmountRial?.let(::rial) ?: "—", 8, strong = true)
                        Row(Modifier.width(widths[9]), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            TextButton(onClick = { onEdit(i) }, enabled = enabled,
                                contentPadding = PaddingValues(1.dp)) {
                                Text("ویرایش ردیف", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { onDelete(i) }, enabled = enabled,
                                contentPadding = PaddingValues(1.dp)) {
                                Text("حذف ردیف", color = Color(0xFFA34D4D),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Surface(color = Color(0xFFDAF2EE), border = BorderStroke(1.dp, Color(0xFFB9DDCF)),
                modifier = Modifier.testTag("invoiceItemTotals")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    cell("جمع", 0, strong = true)
                    cell("${lines.size} قلم", 1, strong = true)
                    cell("—", 2)
                    cell(totalQuantity ?: "—", 3, strong = true)
                    cell("—", 4)
                    cell(discountSum?.let(::rial) ?: "—", 5, strong = true)
                    cell(summary?.netAmountRial?.let(::rial) ?: "—", 6, strong = true)
                    cell(summary?.taxAmountRial?.let(::rial) ?: "—", 7, strong = true)
                    cell(summary?.totalAmountRial?.let(::rial) ?: "—", 8, strong = true)
                    cell("—", 9)
                }
            }
        }
        if (lines.isEmpty()) InvoiceEditorEmptyItems()
        if (lines.isNotEmpty() && discountSum == null) Text("جمع تخفیف خارج از محدوده مجاز است.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** Live preview, independent of both the fiscal payload and the approved A4 PDF. */
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
        modifier = Modifier.fillMaxWidth().testTag("invoiceLiveSummary"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, edge),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("خلاصه زنده صورتحساب", color = ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("فروشنده: ${seller.ifBlank { "مؤدی انتخاب‌شده" }}", color = muted,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text("${lines.size} ردیف", color = sea,
                    style = MaterialTheme.typography.labelSmall)
            }
            Text("خریدار: ${buyer ?: "انتخاب‌نشده"}", color = muted,
                style = MaterialTheme.typography.bodySmall)
            Text("شماره: ${invoiceNumber.ifBlank { "خودکار" }}  |  تاریخ: ${issueDate.ifBlank { "تعیین‌نشده" }}",
                color = muted, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(color = edge)
            if (totals == null) {
                Text(if (lines.isEmpty()) "برای محاسبه جمع، کالا یا خدمت اضافه کنید."
                    else "اقلام نیازمند اصلاح‌اند.",
                    color = Color(0xFF996B32), style = MaterialTheme.typography.bodySmall)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("خالص پس از تخفیف", color = muted)
                    Text(rial(totals.netAmountRial), color = ink,
                        fontWeight = FontWeight.Medium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("مالیات و عوارض", color = muted)
                    Text(rial(totals.taxAmountRial), color = ink,
                        fontWeight = FontWeight.Medium)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    color = Color(0xFFDAF2EE)
                ) {
                    Row(Modifier.padding(13.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("مبلغ قابل پرداخت", color = ink,
                            fontWeight = FontWeight.Bold)
                        Text(rial(totals.totalAmountRial), color = sea,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Text("پیش‌نمایش مبلغ است؛ ارسال مالیاتی فقط پس از نهایی‌سازی و تأیید جداگانه انجام می‌شود.",
                style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}


/** Persistent actions keep Save reachable while editing large invoices on a phone. */
@Composable
internal fun InvoiceEditorDock(
    total: Long?,
    saving: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("invoiceStickyActionDock"),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = Color.White,
        shadowElevation = 9.dp,
        border = BorderStroke(1.dp, edge)
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("جمع فاکتور (ریال)", color = muted,
                    style = MaterialTheme.typography.bodySmall)
                Text(total?.let(::rial) ?: "—", color = sea,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(
                    onClick = onCancel, enabled = !saving,
                    modifier = Modifier.weight(0.42f),
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("انصراف از ویرایش", style = MaterialTheme.typography.labelSmall) }
                Button(
                    onClick = onSave, enabled = !saving && canSave,
                    modifier = Modifier.weight(0.58f),
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = sea)
                ) {
                    Text(if (saving) "در حال ذخیره…" else "ذخیره پیش‌نویس",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
