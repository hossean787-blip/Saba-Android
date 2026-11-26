package ir.saba.invoice.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import ir.saba.invoice.R
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
import androidx.compose.ui.platform.testTag
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
    private const val W = 842
    private const val H = 595
    private fun fmt(n: Long) = persianDigits(NumberFormat.getIntegerInstance(Locale.forLanguageTag("fa-IR")).format(n))
    private fun persianDigits(value:String) = value.map { if(it in '0'..'9') '۰'+(it-'0') else it }.joinToString("")
    private val navy = Color.rgb(12, 50, 78)
    private val ink = Color.rgb(22, 43, 55)
    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        c.drawRoundRect(l, t, r, b, 7f, 7f, Paint(3).apply { this.color = color })
    }
    private data class Palette(val accent: Int, val soft: Int, val line: Int, val muted: Int)
    private fun palette(template: String): Palette = when (template) {
        "ساده" -> Palette(Color.rgb(38, 38, 38), Color.rgb(247, 247, 247), Color.rgb(210, 210, 210), Color.rgb(90, 90, 90))
        "شرکتی" -> Palette(Color.rgb(21, 55, 85), Color.rgb(235, 242, 249), Color.rgb(195, 210, 224), Color.rgb(76, 98, 115))
        else -> Palette(Color.rgb(19, 87, 83), Color.rgb(230, 239, 238), Color.rgb(145, 164, 164), Color.rgb(79, 101, 105))
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
                     maxLines: Int = 3, includePad: Boolean = true, center: Boolean = false,
                     fonts: Pair<Typeface,Typeface>? = null, height: Float = Float.MAX_VALUE) {
        val p=TextPaint(3).apply {
            this.color=color
            typeface=if(bold) fonts?.second ?: Typeface.create("sans-serif",Typeface.BOLD)
                else fonts?.first ?: Typeface.create("sans-serif",Typeface.NORMAL)
        }
        fun build(fs:Float,ellipsize:Boolean):StaticLayout {
            p.textSize=fs
            val builder=StaticLayout.Builder.obtain(value,0,value.length,p,width)
                .setAlignment(if(center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL)
                .setMaxLines(if(ellipsize) maxLines else Int.MAX_VALUE).setIncludePad(includePad)
            if(ellipsize)builder.setEllipsize(android.text.TextUtils.TruncateAt.END)
            return builder.build()
        }
        var fs=size
        var measured=build(fs,false)
        while((measured.lineCount>maxLines || measured.height>height) && fs>6.5f) {
            fs=(fs-0.5f).coerceAtLeast(6.5f)
            measured=build(fs,false)
        }
        val layout=build(fs,true)
        c.save();c.translate(right-width,top);layout.draw(c);c.restore()
    }
    fun generate(company: Company, customer: Customer, invoice: Invoice,
                 lines: List<InvoiceLine>, template: String, logo: Boolean,
                 stamp: Boolean, showLegacyTracking: Boolean, official: OfficialSubmission? = null,
                 sellerProfile: MoadianDetails? = null, buyerProfile: CustomerProfile? = null,
                 brandLogo: Bitmap? = null, fonts: Pair<Typeface,Typeface>? = null): ByteArray {
        require(invoice.companyId == company.id && customer.companyId == company.id &&
                invoice.customerId == customer.id) { "Invoice ownership mismatch" }
        require(official == null || (official.invoiceId == invoice.id &&
            official.companyId == company.id && official.ownerId == company.purchaserId)) {
            "سابقه مالیاتی متعلق به این صورتحساب نیست."
        }
        require(sellerProfile == null || sellerProfile.companyId == company.id)
        require(buyerProfile == null || (buyerProfile.companyId == company.id && buyerProfile.customerId == customer.id))
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
            val p=palette(template)
            val local=InvoiceLocalDetails.fromJson(invoice.localDetailsJson)
            val left=20f; val right=822f
            fun text(c:Canvas,value:String,right:Float,top:Float,width:Int,size:Float,bold:Boolean,color:Int,
                     maxLines:Int,includePad:Boolean,center:Boolean=false,height:Float=Float.MAX_VALUE) =
                SuraInvoicePdf.text(c,value,right,top,width,size,bold,color,maxLines,includePad,center,fonts,height)
            fun cell(value:String,l:Float,t:Float,r:Float,b:Float,size:Float=9f,bold:Boolean=false,center:Boolean=false,shade:Boolean=false) {
                if(shade)c.drawRect(l,t,r,b,Paint(3).apply{color=if(t==geometry.totalsTop && template=="پیش‌فرض")Color.rgb(237,244,243) else p.soft})
                c.drawRect(l,t,r,b,Paint(3).apply{color=p.line;style=Paint.Style.STROKE;strokeWidth=0.55f})
                c.save();c.clipRect(l+2,t+1,r-2,b-1)
                text(c,value,r-4,t+3,(r-l-8).toInt(),size,bold,ink,2,false,center,b-t-5f)
                c.restore()
            }
            fun heading(value:String,top:Float,bottom:Float) {
                cell("",left,top,right,bottom,10f,true,true,true)
                text(c,value,right-4,top+3,(right-left-8).toInt(),10f,true,p.accent,1,false,true,bottom-top-5f)
            }
            val subject=when(invoice.subject) {
                InvoiceSubject.CORRECTION->"صورتحساب اصلاحی فروش کالا و خدمات"
                InvoiceSubject.RETURN->"صورتحساب برگشت از فروش"
                InvoiceSubject.CANCELLATION->"صورتحساب ابطالی"
                else->"صورتحساب فروش کالا و خدمات"
            }
            if(logo && brandLogo!=null) {
                val scale=minOf(43f/brandLogo.width,43f/brandLogo.height)
                val w=brandLogo.width*scale;val h=brandLogo.height*scale
                c.drawBitmap(brandLogo,null,RectF(742f-w/2,21f,742f+w/2,21f+h),Paint(3))
            }
            text(c,"صورتحساب الکترونیک سورا",822f,66f,160,9f,true,p.accent,1,false,true)
            text(c,subject,670f,39f,401,18f,true,p.accent,2,false,true)
            text(c,"تاریخ: "+persianDigits(invoice.issueDate),266f,28f,240,9f,true,ink,1,false)
            text(c,"شماره مالیاتی: "+(official?.taxId?:"صادر نشده"),266f,44f,240,9f,false,ink,2,false)
            text(c,"شماره فاکتور: "+persianDigits(invoice.invoiceNumber),266f,60f,240,9f,true,ink,1,false)
            rule(c,left,82f,right,82f,p.accent)
            fun kind(type:String?)=when(type){"NATURAL"->"شخص حقیقی";"LEGAL"->"شخص حقوقی";"PARTNERSHIP"->"مشارکت مدنی";"FOREIGN"->"اتباع غیرایرانی";else->"شخص حقیقی / حقوقی"}
            fun show(value:String?)=value.orEmpty().ifBlank{"ثبت نشده"}
            fun party(top:Float,title:String,name:String,type:String?,registration:String?,economic:String,national:String,province:String?,county:String?,address:String,phone:String,postal:String?) {
                heading(title,top,top+18)
                val y=top+18
                cell("نام "+kind(type)+": "+show(name),445f,y,right,y+21)
                cell("شماره ثبت: "+show(registration),213f,y,445f,y+21)
                cell("کد اقتصادی: "+show(economic),left,y,213f,y+21)
                cell("استان: "+show(province)+"   شهرستان: "+show(county),445f,y+21,right,y+42)
                cell("تلفن: "+show(phone),213f,y+21,445f,y+42)
                cell("شناسه ملی / کد ملی: "+show(national),left,y+21,213f,y+42)
                cell("نشانی: "+show(address),213f,y+42,right,y+65)
                cell("کدپستی: "+show(postal),left,y+42,213f,y+65)
            }
            party(92f,"مشخصات فروشنده",company.name,sellerProfile?.taxpayerType,sellerProfile?.registrationNumber,company.economicId,company.nationalId,sellerProfile?.province,sellerProfile?.county,company.address,company.phone.ifBlank{sellerProfile?.mobile.orEmpty()},sellerProfile?.postalCode)
            party(175f,"مشخصات خریدار",customer.name,buyerProfile?.accountType,buyerProfile?.registrationNumber,customer.economicId,customer.nationalId,buyerProfile?.province,buyerProfile?.county,customer.address,customer.phone.ifBlank{buyerProfile?.mobile.orEmpty()},customer.postalCode)
            heading("مشخصات کالا یا خدمات",258f,276f)
            val headers=listOf("ردیف","شرح کالا یا خدمات","تعداد","مبلغ واحد\n(ریال)","قبل از تخفیف\n(ریال)","مبلغ تخفیف\n(ریال)","پس از تخفیف\n(ریال)","مالیات و عوارض\n(ریال)","مبلغ نهایی\n(ریال)")
            val edges=mutableListOf(right)
            listOf(30f,240f,58f,78f,80f,75f,80f,82f,79f).forEach{edges.add(edges.last()-it)}
            headers.forEachIndexed{idx,value->cell(value,edges[idx+1],276f,edges[idx],geometry.rowTop,8.5f,true,true,true)}
            var y=geometry.rowTop
            for(index in 0 until geometry.displayRows){
                val line=pageLines.getOrNull(index)
                val rowNumber=pageIndex*InvoicePdfPagination.ROWS_PER_PAGE+index+1
                val cells=if(line==null) listOf(persianDigits(rowNumber.toString()),"","","","","","","","") else {
                    val amounts=rowAmounts[rowNumber-1]
                    val qty=persianDigits(InvoiceLineAmounts.quantityText(line))+"\n"+(MoadianMeasurementUnits.nameOf(line.unitCode)?:"")
                    listOf(persianDigits(rowNumber.toString()),line.description,qty,fmt(line.unitPriceRial),fmt(InvoiceLineAmounts.grossRial(line)),fmt(line.discountRial),fmt(amounts.netAmountRial),persianDigits(InvoiceLineAmounts.vatText(line))+"٪\n"+fmt(amounts.taxAmountRial),fmt(amounts.totalAmountRial))
                }
                val rowHeight=geometry.rowHeightAt(index)
                cells.forEachIndexed{idx,value->cell(value,edges[idx+1],y,edges[idx],y+rowHeight,if(idx==1)9f else 8.5f,idx==8,idx!=1)}
                y+=rowHeight
            }
            if(pageIndex==pages.lastIndex){
                val ty=geometry.totalsTop
                val gross=lines.fold(0L){sum,line->Math.addExact(sum,InvoiceLineAmounts.grossRial(line))}
                val discount=lines.fold(0L){sum,line->Math.addExact(sum,line.discountRial)}
                cell("مبلغ قابل پرداخت: "+fmt(invoice.totalAmountRial)+" ریال\n"+InvoicePdfPersianWords.rials(invoice.totalAmountRial),edges[4],ty,right,geometry.totalsBottom,10f,true,false,true)
                listOf(gross,discount,totals.netAmountRial,invoice.taxAmountRial,invoice.totalAmountRial).forEachIndexed{n,amount->val idx=n+4;cell(fmt(amount),edges[idx+1],ty,edges[idx],geometry.totalsBottom,9f,true,true,true)}
                val pay=geometry.totalsBottom
                cell("روش تسویه: "+InvoiceSettlement.label(invoice.settlementMode)+"\nروش پرداخت: "+show(local.paymentMethod)+"   تاریخ پرداخت: "+show(local.paymentDate),421f,pay,right,pay+34)
                cell("",left,pay,421f,pay+34)
                val shortSubject=when(invoice.subject){InvoiceSubject.CORRECTION->"اصلاحی";InvoiceSubject.RETURN->"برگشت از فروش";InvoiceSubject.CANCELLATION->"ابطالی";else->"اصلی"}
                text(c,"نوع: نوع یک   |   موضوع: "+shortSubject,417f,pay+3f,393,9f,true,ink,1,false)
                text(c,invoiceReceiptLabel(invoice,official),417f,pay+17f,393,9f,true,if(official?.state=="ACCEPTED")p.accent else Color.rgb(135,83,23),1,false)
                val signature=geometry.signatureTop
                cell(if(stamp)"مهر و امضای فروشنده:" else "امضای فروشنده:",421f,signature,right,signature+40)
                cell(if(stamp)"مهر و امضای خریدار:" else "امضای خریدار:",left,signature,421f,signature+40)
                val notes=listOf(local.notes,local.invoiceDescription,local.note1,local.note2).filter{it.isNotBlank()}.joinToString(" | ")
                cell("توضیحات: "+show(notes),left,signature+40,right,signature+63,8.5f)
            }
            val tracking=official?.referenceNumber?.let{"شماره پیگیری رسمی: $it"}
                ?: if(showLegacyTracking && invoice.trackingCode!=null)"کد پیگیری سابقه: ${invoice.trackingCode}" else "شماره پیگیری: دریافت نشده"
            text(c,tracking,822f,geometry.footerTop,590,8f,false,p.muted,1,false)
            text(c,"صفحه "+persianDigits("${pageIndex+1} از ${pages.size}"),220f,geometry.footerTop,200,8f,false,p.muted,1,false)
            if(invoice.referenceTaxId!=null)text(c,"مرجع: "+invoice.referenceTaxId,822f,geometry.footerTop+11,600,8f,false,p.muted,1,false)
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
                          onBack: () -> Unit, onSend: () -> Unit, official: OfficialSubmission? = null,
                          db: SabaDatabase? = null, directPreview: Boolean = false,
                          busy:Boolean = false, onEdit:(() -> Unit)? = null, onDetails:(() -> Unit)? = null,
                          onCorrection:(() -> Unit)? = null, correction:CorrectionLink? = null,
                          onOpenCorrection:(Long)->Unit = {}, localPreview:Boolean = false,
                          onManage:(()->Unit)?=null, onCancellation:(()->Unit)?=null, cancellation:CorrectionLink?=null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var profiles by remember(db,company.id,customer.id) { mutableStateOf<Pair<MoadianDetails?,CustomerProfile?>?>(null) }
    var profileError by remember(db,company.id,customer.id) { mutableStateOf(false) }
    LaunchedEffect(db,company.id,customer.id) {
        profiles=if(db==null) Pair(null,null) else try {
            Pair(db.moadianDetailsDao().byCompany(company.id),db.customerProfileDao().byCustomer(customer.id)?.takeIf{it.companyId==company.id})
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){profileError=true;null}
    }
    val brandLogo=remember(context){BitmapFactory.decodeResource(context.resources,R.drawable.sura_brand_icon)}
    val fonts=remember(context){ val normal=ResourcesCompat.getFont(context,R.font.vazirmatn_regular);val bold=ResourcesCompat.getFont(context,R.font.vazirmatn_bold);if(normal!=null && bold!=null) Pair(normal,bold) else null }
    var template by remember { mutableStateOf("پیش‌فرض") }
    var logo by remember { mutableStateOf(true) }
    var stamp by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val bytes = remember(company, customer, invoice, lines, template, logo, stamp, official, profiles, brandLogo, fonts) {
        profiles?.let { profile -> runCatching { SuraInvoicePdf.generate(company, customer, invoice, lines, template, logo, stamp, true, official, profile.first, profile.second, brandLogo, fonts) }
            .onFailure { error = it.message }.getOrNull() }
    }
    var previewPage by remember(invoice.id) { mutableIntStateOf(0) }
    val pageCount = (lines.size + InvoicePdfPagination.ROWS_PER_PAGE - 1).div(InvoicePdfPagination.ROWS_PER_PAGE).coerceAtLeast(1)
    val preview = remember(bytes, previewPage) {
        bytes?.let { runCatching { SuraInvoicePdf.preview(context, it, previewPage) }.getOrNull() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("پیش‌نمایش و خروجی صورتحساب", style = MaterialTheme.typography.headlineSmall)
        if(localPreview) Text("پیش‌نمایش محلی • ذخیره و ارسال نشده",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        if (preview != null) Image(preview.asImageBitmap(), "پیش‌نمایش واقعی PDF",
            modifier = Modifier.fillMaxWidth().heightIn(max = 570.dp))
        else Text(if(profileError) "خواندن مشخصات طرفین ناموفق بود." else error ?: "در حال آماده‌سازی پیش‌نمایش PDF", color = MaterialTheme.colorScheme.error)
        if (preview != null && pageCount > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = previewPage > 0, onClick = { previewPage-- }) { Text("صفحه قبل") }
                Text("صفحه ${previewPage + 1} از ${pageCount}", style = MaterialTheme.typography.bodyMedium)
                TextButton(enabled = previewPage < pageCount - 1, onClick = { previewPage++ }) { Text("صفحه بعد") }
            }
        }
        if (!directPreview) Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("پیش‌فرض", "ساده", "شرکتی").forEach { option ->
                FilterChip(selected = template == option, onClick = { template = option },
                    label = { Text(option) })
            }
        }
        if (!directPreview) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("لوگو"); Switch(logo, { logo = it })
            Text("مهر"); Switch(stamp, { stamp = it })
        }
        if(localPreview) {
            Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text("مبلغ قابل پرداخت",style=MaterialTheme.typography.titleSmall)
                    Text(rial(invoice.totalAmountRial),style=MaterialTheme.typography.titleLarge)
                    Text("این پیش‌نمایش رایگان است؛ برای دریافت فایل یا ارسال، ابتدا پیش‌نویس را ذخیره کنید.",style=MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("بازگشت به فاکتور")}
        } else {
        if(directPreview && invoiceEventStatus(invoice,official)!=InvoiceEventStatus.SUCCESS) {
            val state=invoiceEventStatus(invoice,official)
            val failed=state==InvoiceEventStatus.REJECTED
            Card(colors=CardDefaults.cardColors(containerColor=if(failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant),
                modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("وضعیت: "+state.label,style=MaterialTheme.typography.titleSmall)
                    Text(invoiceTaxStatus(official),style=MaterialTheme.typography.bodySmall)
                    official?.takeUnless(::isLegacyEvidenceMessage)?.lastError?.takeIf{it.isNotBlank()}?.let {
                        Text(it.take(2000),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(enabled = bytes != null && !busy, onClick = { bytes?.let(onExport) },
            modifier = Modifier.fillMaxWidth()) { Text("دریافت خروجی PDF") }
        if(correction!=null) {
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant),
                modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(correction.label,style=MaterialTheme.typography.bodySmall)
                    TextButton(enabled=!busy,onClick={onOpenCorrection(correction.invoice.id)}) {
                        Text("مشاهده اصلاحیه ${correction.invoice.invoiceNumber}")
                    }
                }
            }
        }
        if(onCorrection!=null) OutlinedButton(enabled=!busy,onClick=onCorrection,modifier=Modifier.fillMaxWidth().testTag("startInvoiceCorrection"),
            shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
            Text("صورتحساب اصلاحی")
        }
        if(cancellation!=null) OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(cancellation.label,style=MaterialTheme.typography.bodySmall)
                TextButton(enabled=!busy,onClick={onOpenCorrection(cancellation.invoice.id)}) { Text("مشاهده ابطالی "+cancellation.invoice.invoiceNumber) }
            }
        }
        if(onCancellation!=null) OutlinedButton(enabled=!busy,onClick=onCancellation,
            modifier=Modifier.fillMaxWidth().testTag("startInvoiceCancellation"),
            shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
            Text("صورتحساب ابطالی",color=MaterialTheme.colorScheme.error)
        }
        val action=invoicePreviewAction(invoice,official)
        if(action!=null) OutlinedButton(enabled=!busy,onClick=onSend,modifier=Modifier.fillMaxWidth().testTag("invoiceDirectSend")) { Text(action) }
        if(onManage!=null) Button(enabled=!busy,onClick=onManage,
            modifier=Modifier.fillMaxWidth().testTag("openSubmissionManagement"),
            shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            colors=ButtonDefaults.buttonColors(containerColor=androidx.compose.ui.graphics.Color(0xFF174B80))) {
            Text("مدیریت و تعیین تکلیف ارسال")
        }
        if(onEdit!=null && canEditInvoicePreview(invoice,official)) {
            OutlinedButton(enabled=!busy,onClick=onEdit,modifier=Modifier.fillMaxWidth()) {
                Text(if(official?.state=="REJECTED") "اصلاح و ویرایش همین فاکتور" else "ویرایش فاکتور")
            }
        }
        if(onDetails!=null && invoiceEventStatus(invoice,official)!=InvoiceEventStatus.SUCCESS) {
            TextButton(enabled=!busy,onClick=onDetails,modifier=Modifier.fillMaxWidth()) { Text("جزئیات فاکتور") }
        }
        TextButton(enabled=!busy,onClick = onBack) { Text("بازگشت") }
        }
    }
}
