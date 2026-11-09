package ir.saba.invoice.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.*
import ir.saba.invoice.domain.InvoiceCalculator
import ir.saba.invoice.domain.InvoiceLineAmounts
import ir.saba.invoice.domain.InvoiceSettlement
import ir.saba.invoice.domain.InvoiceSubject
import ir.saba.moadian.MoadianMeasurementUnits
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.NumberFormat
import java.util.Locale

/** Both the preview and exported PDF are derived from exactly the same A4 page. */
object SuraInvoicePdf {
    private const val W = 595
    private const val H = 842
    private fun fmt(n: Long) = NumberFormat.getIntegerInstance(Locale.forLanguageTag("fa-IR")).format(n)
    private val navy = Color.rgb(12, 50, 78)
    private val blue = Color.rgb(218, 232, 246)
    private val ink = Color.rgb(26, 48, 66)
    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        c.drawRoundRect(l, t, r, b, 7f, 7f, Paint(3).apply { this.color = color })
    }
    /** Android StaticLayout handles Persian BiDi and glyph shaping, unlike naïve Canvas.drawText. */
    private fun text(c: Canvas, value: String, right: Float, top: Float, width: Int = 230,
                     size: Float = 11f, bold: Boolean = false, color: Int = ink,
                     maxLines: Int = 3, includePad: Boolean = true) {
        val p = TextPaint(3).apply { this.color = color; textSize = size
            typeface = if (bold) android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                       else android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL) }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, p, width)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .setTextDirection(android.text.TextDirectionHeuristics.RTL)
            .setMaxLines(maxLines).setIncludePad(includePad).build()
        c.save(); c.translate(right - width, top); layout.draw(c); c.restore()
    }
    fun generate(company: Company, customer: Customer, invoice: Invoice,
                 lines: List<InvoiceLine>, template: String, logo: Boolean,
                 stamp: Boolean, qr: Boolean): ByteArray {
        require(invoice.companyId == company.id && customer.companyId == company.id &&
                invoice.customerId == customer.id) { "Invoice ownership mismatch" }
        // Reject invalid documents before allocating native PDF resources.
        require(lines.isNotEmpty() && lines.size <= 500) { "برای PDF حداکثر ۵۰۰ ردیف مجاز است." }
        val totals = InvoiceCalculator.calculate(lines)
        require(invoice.totalAmountRial == totals.totalAmountRial && invoice.taxAmountRial == totals.taxAmountRial) {
            "جمع مبالغ صورتحساب با اقلام مطابقت ندارد؛ صورتحساب را دوباره بارگذاری کنید."
        }
        val pages = InvoicePdfPagination.paginate(lines)
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { pageIndex, pageLines ->
                val geometry = InvoicePdfLayout(pageLines.size)
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(W, H, pageIndex + 1).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            rect(c, 21f, 20f, 574f, 84f, navy)
            text(c, if (logo) "SURA | سورا" else "صورتحساب", 557f, 32f, 250, 22f, true, Color.WHITE)
            text(c, when (invoice.subject) {
                InvoiceSubject.CORRECTION -> "صورتحساب اصلاحی — ثبت محلی، فاقد ارسال رسمی"
                InvoiceSubject.RETURN -> "برگشت از فروش — ثبت محلی، فاقد ارسال رسمی"
                else -> "صورتحساب فروش کالا و خدمات"
            },
                555f, 60f, 400, 12f, false, Color.WHITE)
            rect(c, 22f, 95f, 574f, 128f, blue)
            text(c, "شماره صورتحساب: ${invoice.invoiceNumber}", 559f, 103f, 235, 11f, true)
            text(c, "تاریخ: ${invoice.issueDate}", 305f, 103f, 200)
            text(c, "قالب: $template", 112f, 103f, 85, 9f)
            rect(c, 306f, 140f, 574f, 259f, Color.rgb(241, 248, 253))
            rect(c, 22f, 140f, 290f, 259f, Color.rgb(241, 248, 253))
            text(c, "اطلاعات فروشنده", 560f, 149f, 240, 13f, true, navy)
            text(c, "نام: ${company.name}", 561f, 174f, 242)
            text(c, "شناسه ملی: ${company.nationalId}", 561f, 195f, 242)
            text(c, "اقتصادی: ${company.economicId}", 561f, 215f, 242)
            text(c, "تلفن: ${company.phone}", 561f, 235f, 242)
            text(c, "اطلاعات خریدار", 277f, 149f, 242, 13f, true, navy)
            text(c, "نام: ${customer.name}", 277f, 174f, 242)
            text(c, "شناسه ملی: ${customer.nationalId}", 277f, 195f, 242)
            text(c, "اقتصادی: ${customer.economicId}", 277f, 215f, 242)
            text(c, "تلفن: ${customer.phone}", 277f, 235f, 242)
            val headers = listOf("شرح کالا / خدمت", "تعداد", "قیمت واحد (ریال)", "جمع (ریال)")
            val right = floatArrayOf(560f, 330f, 248f, 112f)
            val widths = intArrayOf(215, 75, 128, 95)
            var y = 275f
            rect(c, 22f, y, 574f, y + 31f, blue)
            headers.forEachIndexed { i, h -> text(c, h, right[i], y + 8, widths[i], 10f, true, navy) }
            y += 34f
            pageLines.forEachIndexed { index, line ->
                if (index % 2 == 0) rect(c, 22f, y, 574f, y + geometry.rowHeight - 1, Color.rgb(245, 250, 254))
                val sum = Math.subtractExact(InvoiceLineAmounts.grossRial(line), line.discountRial)
                val quantityLabel = InvoiceLineAmounts.quantityText(line) +
                    (if (line.unitCode.isNotBlank()) " " +
                        (MoadianMeasurementUnits.nameOf(line.unitCode) ?: line.unitCode) else "")
                listOf(line.description, quantityLabel, fmt(line.unitPriceRial), fmt(sum))
                    .forEachIndexed { i, v -> text(c, v, right[i], y + 2, widths[i], 8f,maxLines=2,includePad=false) }
                y += geometry.rowHeight
            }
            if (pageIndex == pages.lastIndex) {
            val ty = geometry.totalsTop
            rect(c, 22f, ty, 574f, ty + 105f, Color.rgb(239, 247, 253))
            text(c, "جمع کل: ${fmt(Math.subtractExact(invoice.totalAmountRial, invoice.taxAmountRial))} ریال",
                557f, ty + 13, 510, 12f, true)
            text(c, "مالیات: ${fmt(invoice.taxAmountRial)} ریال", 557f, ty + 42, 510)
            text(c, "مبلغ نهایی: ${fmt(invoice.totalAmountRial)} ریال",
                557f, ty + 70, 510, 16f, true, navy)
            if (invoice.settlementMode != InvoiceSettlement.CASH) {
                val shares = InvoiceSettlement.amounts(invoice)
                text(c, "تسویه: " + InvoiceSettlement.label(invoice.settlementMode) +
                    " | نقدی: " + fmt(shares.cashRial) + " | نسیه: " + fmt(shares.creditRial) + " ریال",
                    557f, ty + 93, 510, 8f, false, navy, maxLines=1, includePad=false)
            }
            if (stamp) text(c, "محل مهر و امضا", 550f, geometry.signatureTop, 200, 12f, true,maxLines=1)
            if (qr) text(c, "کد رهگیری: ${invoice.trackingCode ?: "هنوز صادر نشده"}", 290f, geometry.signatureTop, 252, 10f,maxLines=2)
            }
            if (invoice.subject == InvoiceSubject.CORRECTION || invoice.subject == InvoiceSubject.RETURN) {
                text(c, "مرجع سند ارجاعی: " + invoice.referenceTaxId.orEmpty() +
                    " • صفحه " + (pageIndex + 1) + " از " + pages.size,
                    558f, 781f, 522, 9f, true, navy, maxLines=1, includePad=false)
            } else {
                // Approved print layout for original invoices stays exactly unchanged.
                text(c, "صفحه " + (pageIndex + 1) + " از " + pages.size, 150f, 781f, 125, 9f)
            }
            text(c, "این نسخه چاپی به‌تنهایی نشانه ارسال یا پذیرش در سامانه مؤدیان نیست.",
                565f, geometry.footerTop, 533, 9f)
            pdf.finishPage(page)
            }
            return ByteArrayOutputStream().use { out -> pdf.writeTo(out); out.toByteArray() }
        } finally { pdf.close() }
    }
    fun preview(context: Context, bytes: ByteArray, pageIndex: Int = 0): Bitmap {
        val temp = File.createTempFile("sura-invoice-", ".pdf", context.cacheDir)
        try {
            temp.writeBytes(bytes)
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    require(pageIndex in 0 until renderer.pageCount) { "صفحه انتخاب‌شده موجود نیست." }
                    renderer.openPage(pageIndex).use { page ->
                        val bmp = Bitmap.createBitmap(W * 2, H * 2, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return bmp
                    }
                }
            }
        } finally { temp.delete() }
    }
}

@Composable
fun InvoiceTemplateScreen(company: Company, customer: Customer, invoice: Invoice,
                          lines: List<InvoiceLine>, onExport: (ByteArray) -> Unit,
                          onBack: () -> Unit, onSend: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var template by remember { mutableStateOf("پیش‌فرض") }
    var logo by remember { mutableStateOf(true) }
    var stamp by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val bytes = remember(company, customer, invoice, lines, template, logo, stamp, qr) {
        runCatching { SuraInvoicePdf.generate(company, customer, invoice, lines, template, logo, stamp, qr) }
            .onFailure { error = it.message }.getOrNull()
    }
    var previewPage by remember(invoice.id) { mutableIntStateOf(0) }
    val pageCount = (lines.size + 13).div(14).coerceAtLeast(1)
    val preview = remember(bytes, previewPage) {
        bytes?.let { runCatching { SuraInvoicePdf.preview(context, it, previewPage) }.getOrNull() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("الگوی فاکتور", style = MaterialTheme.typography.headlineSmall)
        if (preview != null) Image(preview.asImageBitmap(), "پیش‌نمایش واقعی PDF",
            modifier = Modifier.fillMaxWidth().heightIn(max = 570.dp))
        else Text(error ?: "پیش‌نمایش در دسترس نیست", color = MaterialTheme.colorScheme.error)
        if (preview != null && pageCount > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = previewPage > 0, onClick = { previewPage-- }) { Text("صفحه قبل") }
                Text("صفحه ${previewPage + 1} از ${pageCount}", style = MaterialTheme.typography.bodyMedium)
                TextButton(enabled = previewPage < pageCount - 1, onClick = { previewPage++ }) { Text("صفحه بعد") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("پیش‌فرض", "ساده", "شرکتی").forEach { option ->
                FilterChip(selected = template == option, onClick = { template = option },
                    label = { Text(option) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("لوگو"); Switch(logo, { logo = it })
            Text("مهر"); Switch(stamp, { stamp = it })
            Text("رهگیری"); Switch(qr, { qr = it })
        }
        Button(enabled = bytes != null, onClick = { bytes?.let(onExport) },
            modifier = Modifier.fillMaxWidth()) { Text("ذخیره PDF") }
        OutlinedButton(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
            Text("ارسال به سامانه مؤدیان")
        }
        TextButton(onClick = onBack) { Text("بازگشت") }
    }
}
