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
    private val ink = Color.rgb(26, 48, 66)
    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        c.drawRoundRect(l, t, r, b, 7f, 7f, Paint(3).apply { this.color = color })
    }
    private data class Palette(val accent: Int, val soft: Int, val line: Int, val muted: Int)
    private fun palette(template: String): Palette = when (template) {
        "ساده" -> Palette(Color.rgb(38, 38, 38), Color.rgb(247, 247, 247), Color.rgb(210, 210, 210), Color.rgb(90, 90, 90))
        "شرکتی" -> Palette(Color.rgb(21, 55, 85), Color.rgb(235, 242, 249), Color.rgb(195, 210, 224), Color.rgb(76, 98, 115))
        else -> Palette(Color.rgb(19, 87, 83), Color.rgb(236, 246, 244), Color.rgb(198, 219, 215), Color.rgb(81, 103, 103))
    }
    private fun rule(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        c.drawLine(x1,y1,x2,y2,Paint(3).apply { this.color=color; strokeWidth=0.65f })
    }
    private fun outline(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        c.drawRoundRect(l,t,r,b,5f,5f,Paint(3).apply {
            this.color=color; style=Paint.Style.STROKE; strokeWidth=0.75f
        })
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
            .setMaxLines(maxLines).setEllipsize(android.text.TextUtils.TruncateAt.END).setIncludePad(includePad).build()
        c.save(); c.translate(right - width, top); layout.draw(c); c.restore()
    }
    fun generate(company: Company, customer: Customer, invoice: Invoice,
                 lines: List<InvoiceLine>, template: String, logo: Boolean,
                 stamp: Boolean, qr: Boolean, official: OfficialSubmission? = null): ByteArray {
        require(invoice.companyId == company.id && customer.companyId == company.id &&
                invoice.customerId == customer.id) { "Invoice ownership mismatch" }
        require(official == null || (official.invoiceId == invoice.id &&
            official.companyId == company.id && official.ownerId == company.purchaserId)) {
            "سابقه مالیاتی متعلق به این صورتحساب نیست."
        }
        // Reject invalid documents before allocating native PDF resources.
        require(lines.isNotEmpty() && lines.size <= 500) { "برای PDF حداکثر ۵۰۰ ردیف مجاز است." }
        val totals = InvoiceCalculator.calculate(lines)
        require(invoice.totalAmountRial == totals.totalAmountRial && invoice.taxAmountRial == totals.taxAmountRial) {
            "جمع مبالغ صورتحساب با اقلام مطابقت ندارد؛ صورتحساب را دوباره بارگذاری کنید."
        }
        val rowAmounts = InvoicePdfRowAmounts.calculate(lines)
        val pages = InvoicePdfPagination.paginate(lines)
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { pageIndex, pageLines ->
                val geometry = InvoicePdfLayout(pageLines.size)
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(W, H, pageIndex + 1).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            val p = palette(template)
            val subject = when (invoice.subject) {
                InvoiceSubject.CORRECTION -> "صورتحساب اصلاحی"
                InvoiceSubject.RETURN -> "صورتحساب برگشت از فروش"
                InvoiceSubject.CANCELLATION -> "صورتحساب ابطالی"
                else -> "صورتحساب فروش کالا و خدمات"
            }
            // Print-friendly masthead, with clear hierarchy and restrained branding.
            rect(c, 24f, 24f, 571f, 28f, p.accent)
            text(c, subject, 571f, 39f, 405, 20f, true, p.accent, 1)
            text(c, company.name, 571f, 67f, 385, 12f, true, ink, 1)
            if (logo) {
                text(c, "SURA", 139f, 39f, 110, 19f, true, p.accent, 1)
                text(c, "سورا", 139f, 64f, 110, 10f, false, p.muted, 1)
            }
            rule(c,24f,87f,571f,87f,p.line)
            text(c, "شماره: ${invoice.invoiceNumber}", 571f, 96f, 265, 11f, true, ink, 1)
            text(c, "تاریخ: ${invoice.issueDate}", 293f, 96f, 268, 11f, true, ink, 1)
            rect(c,24f,120f,571f,166f,p.soft)
            text(c, invoiceReceiptLabel(invoice, official), 560f, 125f, 525, 10f, true, p.accent, 2, false)
            text(c, "شماره مالیاتی: " + (official?.taxId ?: "دریافت نشده"), 560f, 146f, 525, 9f, false, ink, 1, false)
            // Two equally sized bordered identity panels.
            outline(c, 301f, 177f, 571f, 260f, p.line)
            outline(c, 24f, 177f, 294f, 260f, p.line)
            rect(c, 301f, 177f, 571f, 197f, p.soft)
            rect(c, 24f, 177f, 294f, 197f, p.soft)
            text(c, "فروشنده", 562f, 179f, 248, 11f, true, p.accent, 1)
            text(c, "خریدار", 285f, 179f, 248, 11f, true, p.accent, 1)
            fun identity(right: Float, name: String, national: String, economic: String, phone: String) {
                text(c, name, right, 201f, 248, 10f, true, ink, 1, false)
                text(c, "شناسه ملی: " + national.ifBlank { "—" }, right, 215f, 248, 9f, false, ink, 1, false)
                text(c, "شماره اقتصادی: " + economic.ifBlank { "—" }, right, 229f, 248, 9f, false, ink, 1, false)
                text(c, "تلفن: " + phone.ifBlank { "—" }, right, 243f, 248, 9f, false, ink, 1, false)
            }
            identity(562f, company.name, company.nationalId, company.economicId, company.phone)
            identity(285f, customer.name, customer.nationalId, customer.economicId, customer.phone)
            val headers = listOf("ردیف","شرح کالا / خدمت","مقدار","بهای واحد","تخفیف","ارزش افزوده","مبلغ کل")
            val right = floatArrayOf(567f, 539f, 354f, 292f, 220f, 153f, 86f)
            val widths = intArrayOf(23, 176, 57, 67, 62, 62, 59)
            val edges = floatArrayOf(24f,90f,157f,224f,296f,358f,543f,571f)
            rect(c,24f,273f,571f,geometry.rowTop-2f,p.accent)
            headers.forEachIndexed { i, label ->
                text(c,label,right[i],280f,widths[i],8f,true,Color.WHITE,2,false)
            }
            var y = geometry.rowTop
            pageLines.forEachIndexed { index, line ->
                val rowTotal = rowAmounts[pageIndex * InvoicePdfPagination.ROWS_PER_PAGE + index]
                if(index%2==0) rect(c,24f,y,571f,y+geometry.rowHeight,p.soft)
                val quantityLabel = InvoiceLineAmounts.quantityText(line) +
                    (if(line.unitCode.isNotBlank()) " " + (MoadianMeasurementUnits.nameOf(line.unitCode) ?: line.unitCode) else "")
                val rowNumber = pageIndex * InvoicePdfPagination.ROWS_PER_PAGE + index + 1
                val cells = listOf(rowNumber.toString(),line.description,quantityLabel,
                    fmt(line.unitPriceRial),fmt(line.discountRial),
                    InvoiceLineAmounts.vatText(line)+"٪\n"+fmt(rowTotal.taxAmountRial),
                    fmt(rowTotal.totalAmountRial))
                cells.forEachIndexed { i,v -> text(c,v,right[i],y+3f,widths[i],
                    if(i==1) 9f else 7.5f,i==6,ink,if(i==1) 2 else 2,false) }
                y += geometry.rowHeight
                rule(c,24f,y,571f,y,p.line)
            }
            edges.forEach { x -> rule(c,x,geometry.rowTop,x,y,p.line) }
            text(c,"تمام مبالغ به ریال",571f,y+4f,300,8f,false,p.muted,1,false)
            if(pageIndex==pages.lastIndex) {
                val ty=geometry.totalsTop
                outline(c,24f,ty,571f,ty+105f,p.line)
                val gross=lines.fold(0L) { sum, line -> Math.addExact(sum,InvoiceLineAmounts.grossRial(line)) }
                val discount=lines.fold(0L) { sum, line -> Math.addExact(sum,line.discountRial) }
                text(c,"جمع قبل از تخفیف: "+fmt(gross),558f,ty+7f,520,10f,false,ink,1,false)
                text(c,"جمع تخفیف: "+fmt(discount),558f,ty+21f,520,10f,false,ink,1,false)
                text(c,"خالص: "+fmt(totals.netAmountRial),558f,ty+35f,520,10f,false,ink,1,false)
                text(c,"ارزش افزوده: "+fmt(invoice.taxAmountRial),558f,ty+49f,520,10f,false,ink,1,false)
                rect(c,24f,ty+65f,571f,ty+90f,p.accent)
                text(c,"مبلغ قابل پرداخت",558f,ty+70f,250,12f,true,Color.WHITE,1,false)
                text(c,fmt(invoice.totalAmountRial)+" ریال",292f,ty+68f,255,15f,true,Color.WHITE,1,false)
                val shares=InvoiceSettlement.amounts(invoice)
                text(c,"تسویه: "+InvoiceSettlement.label(invoice.settlementMode)+" | نقدی: "+fmt(shares.cashRial)+" | نسیه: "+fmt(shares.creditRial),
                    558f,ty+93f,520,8f,false,ink,1,false)
                if(stamp) {
                    outline(c,326f,geometry.signatureTop,571f,geometry.signatureTop+27f,p.line)
                    text(c,"محل مهر و امضا",560f,geometry.signatureTop+5f,220,9f,false,p.muted,1,false)
                }
                if(official!=null || qr) {
                    val trackingLabel=official?.referenceNumber?.let { "شماره پیگیری رسمی: $it" }
                        ?: invoice.trackingCode?.let { "کد پیگیری سابقه: $it" } ?: "شماره پیگیری: دریافت نشده"
                    text(c,trackingLabel,315f,geometry.signatureTop,285,8f,false,ink,2,false)
                }
            }
            rule(c,24f,779f,571f,779f,p.line)
            if(invoice.subject in setOf(InvoiceSubject.CORRECTION,InvoiceSubject.CANCELLATION,InvoiceSubject.RETURN))
                text(c,"مرجع: "+invoice.referenceTaxId.orEmpty(),571f,784f,400,8f,false,p.muted,1,false)
            text(c,"صفحه ${pageIndex+1} از ${pages.size}",145f,784f,120,8f,false,p.muted,1,false)
            text(c,"اعتبار مالیاتی بر اساس نتیجه استعلام سامانه مؤدیان تعیین می‌شود.",571f,geometry.footerTop,547,8f,false,p.muted,1,false)
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
                          onBack: () -> Unit, onSend: () -> Unit, official: OfficialSubmission? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var template by remember { mutableStateOf("پیش‌فرض") }
    var logo by remember { mutableStateOf(true) }
    var stamp by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val bytes = remember(company, customer, invoice, lines, template, logo, stamp, qr, official) {
        runCatching { SuraInvoicePdf.generate(company, customer, invoice, lines, template, logo, stamp, qr, official) }
            .onFailure { error = it.message }.getOrNull()
    }
    var previewPage by remember(invoice.id) { mutableIntStateOf(0) }
    val pageCount = (lines.size + InvoicePdfPagination.ROWS_PER_PAGE - 1).div(InvoicePdfPagination.ROWS_PER_PAGE).coerceAtLeast(1)
    val preview = remember(bytes, previewPage) {
        bytes?.let { runCatching { SuraInvoicePdf.preview(context, it, previewPage) }.getOrNull() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("پیش‌نمایش و خروجی صورتحساب", style = MaterialTheme.typography.headlineSmall)
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
        if (invoice.status in setOf("DRAFT", "FINALIZED") &&
            (official == null || official.state in setOf("PREPARED", "SENDING", "UNKNOWN", "SUBMITTED"))) {
            OutlinedButton(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
                Text(if (official?.state in setOf("SENDING", "UNKNOWN", "SUBMITTED"))
                    "استعلام وضعیت ارسال" else "ارسال به سامانه مؤدیان")
            }
        }
        TextButton(onClick = onBack) { Text("بازگشت") }
    }
}
