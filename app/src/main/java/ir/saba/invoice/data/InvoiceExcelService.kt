package ir.saba.invoice.data

import androidx.room.withTransaction
import ir.saba.invoice.domain.InvoiceCalculator
import ir.saba.invoice.domain.InvoiceInput
import ir.saba.invoice.domain.InvoiceLineAmounts
import ir.saba.invoice.domain.InvoiceSettlement
import ir.saba.invoice.security.AccountScope
import java.util.UUID

internal data class ExcelBuyer(val national: String, val name: String, val type: String,
    val economic: String, val postal: String, val address: String, val phone: String)
internal data class ExcelInvoice(val number: String, val date: String, val buyer: String,
    val lines: List<InvoiceLine>, val mode: String, val cash: Long?, val total: Long)
internal data class ExcelInvoiceBatch(val invoices: List<ExcelInvoice>, val buyers: Map<String, ExcelBuyer>)
internal data class ExcelInvoicePreview(val invoice: ExcelInvoice, val buyerName: String, val newBuyer: Boolean,
    val complete: Boolean, val pending: Boolean)

/** Never sends tax invoices. Existing durable credit coordinator is the only path to new drafts. */
internal class InvoiceExcelService(private val db: SabaDatabase, private val account: AccountScope,
    private val companyId: Long, private val creation: InvoiceCreationCoordinator) {
    private fun digits(s: String) = InvoiceInput.digits(s).trim()
    private suspend fun company(): Company {
        val c = requireNotNull(db.companyDao().byId(companyId)) { "مودی یافت نشد." }
        require(c.purchaserId == account.ownerId) { "مودی متعلق به این حساب نیست." }
        val details = requireNotNull(db.moadianDetailsDao().byCompany(companyId)) { "مشخصات مؤدی ناقص است." }
        require(!details.inactive) { "مودی غیرفعال است." }
        return c
    }

    suspend fun template(): ByteArray = InvoiceBatchWorkbook.template(company())

    suspend fun parse(bytes: ByteArray): ExcelInvoiceBatch {
        val c = company()
        val sheets = InvoiceBatchWorkbook.read(bytes)
        val taxpayer = sheets[1].singleOrNull() ?: error("کاربرگ مودیان باید فقط یک مؤدی داشته باشد.")
        require(digits(taxpayer[0]) == digits(c.nationalId) && taxpayer[0].isNotBlank()) { "شناسه مؤدی فایل با مؤدی فعال یکسان نیست." }
        require(taxpayer[1] == c.name && digits(taxpayer[2]) == digits(c.economicId)) { "نام یا شماره اقتصادی مؤدی با اطلاعات ثبت‌شده مطابقت ندارد." }
        val buyers = linkedMapOf<String, ExcelBuyer>()
        sheets[2].forEachIndexed { index, row ->
            try {
                val id = digits(row[0])
                val type = when (row[2]) {
                    "حقیقی", "NATURAL" -> "NATURAL"
                    "حقوقی", "LEGAL" -> "LEGAL"
                    "مشارکت مدنی", "PARTNERSHIP" -> "PARTNERSHIP"
                    "اتباع غیرایرانی", "FOREIGN" -> "FOREIGN"
                    else -> error("نوع شخص معتبر نیست.")
                }
                require(id.matches(Regex("[0-9]{10,14}")) &&
                    (type != "LEGAL" || id.length == 11) && (type != "NATURAL" || id.length == 10)) { "شناسه هویتی نامعتبر است." }
                require(row[1].isNotBlank() && row[1].length <= 250) { "نام مشتری الزامی است." }
                val economic = digits(row[3]); val postal = digits(row[4]); val phone = digits(row[6])
                require(economic.isEmpty() || economic.matches(Regex("[0-9]{10,14}"))) { "شماره اقتصادی نامعتبر است." }
                require(postal.isEmpty() || postal.matches(Regex("[0-9]{10}"))) { "کد پستی نامعتبر است." }
                require(phone.isEmpty() || phone.matches(Regex("[0-9]{5,15}"))) { "تلفن نامعتبر است." }
                require(row[5].length <= 1000)
                require(buyers.put(id, ExcelBuyer(id,row[1],type,economic,postal,row[5],phone)) == null) { "شناسه مشتری تکراری است." }
            } catch (e: Exception) { throw IllegalArgumentException("کاربرگ مشتریان، رکورد ${index+1}: ${e.message}", e) }
        }
        data class Group(val date: String,val buyer: String,val mode: String,val cash: String,val lines: MutableList<InvoiceLine>)
        val groups = linkedMapOf<String, Group>()
        val details = requireNotNull(db.moadianDetailsDao().byCompany(companyId))
        val lower = digits(details.invoiceSerialStart).toLongOrNull() ?: 1000000001L
        val upper = (digits(details.referenceSerialStart).toLongOrNull() ?: 2000000001L) - 1
        sheets[0].forEachIndexed { index, row ->
            try {
                val number = InvoiceInput.number(row[0])
                require(number.toLongOrNull()?.let { it in lower..upper } == true) { "شماره صورتحساب باید در بازه اصلی مؤدی باشد." }
                val date = InvoiceInput.date(row[1]); val buyer = digits(row[3])
                require(digits(row[2]) == digits(c.nationalId)) { "شناسه مؤدی این ردیف با مؤدی فعال یکسان نیست." }
                require(buyer.matches(Regex("[0-9]{10,14}"))) { "شناسه مشتری معتبر نیست." }
                val code = digits(row[4])
                require(code.matches(Regex("[0-9]{13}")) && row[5].isNotBlank() && row[5].length <= 500) { "شناسه ۱۳ رقمی و شرح کالا/خدمت الزامی است." }
                require(row[6].isNotBlank()) { "کد واحد سنجش الزامی است." }
                val line = InvoiceLineAmounts.fromUserInput(code,row[5],row[7],
                    InvoiceInput.wholeNumber(row[8],"مبلغ واحد"), InvoiceInput.wholeNumber(row[9].ifBlank{"0"},"تخفیف"),row[10],digits(row[6]))
                val mode = when(row[11]) {
                    "نقدی","CASH" -> InvoiceSettlement.CASH
                    "نسیه","CREDIT" -> InvoiceSettlement.CREDIT
                    "نقدی و نسیه","MIXED" -> InvoiceSettlement.MIXED
                    else -> error("روش تسویه معتبر نیست.")
                }
                val cash = digits(row[12])
                val g = groups.getOrPut(number) { Group(date,buyer,mode,cash,mutableListOf()) }
                require(g.date==date && g.buyer==buyer && g.mode==mode && g.cash==cash) { "اطلاعات سربرگ ردیف‌های یک صورتحساب باید یکسان باشد." }
                require(g.lines.size < 100) { "حداکثر ۱۰۰ قلم در هر صورتحساب مجاز است." }
                g.lines.add(line)
            } catch (e: Exception) { throw IllegalArgumentException("کاربرگ صورتحساب‌ها، رکورد ${index+1}: ${e.message}", e) }
        }
        require(groups.size in 1..100) { "هر فایل باید ۱ تا ۱۰۰ صورتحساب داشته باشد." }
        val invoices = groups.map { (number,g) ->
            try {
                val total = InvoiceCalculator.calculate(g.lines).totalAmountRial
                val cash = InvoiceSettlement.cashFromUserInput(g.mode,total,g.cash)
                if(g.cash.isNotBlank() && g.mode != InvoiceSettlement.MIXED) require(
                    InvoiceInput.wholeNumber(g.cash,"مبلغ نقدی") == (if(g.mode==InvoiceSettlement.CASH) total else 0L)) { "مبلغ نقدی با روش تسویه تطابق ندارد." }
                ExcelInvoice(number,g.date,g.buyer,g.lines.toList(),g.mode,cash,total)
            } catch(e:Exception) { throw IllegalArgumentException("صورتحساب $number: ${e.message}",e) }
        }
        return ExcelInvoiceBatch(invoices,buyers)
    }

    private fun key(number: String): String = UUID.nameUUIDFromBytes(
        "sura-excel-v1:${account.ownerId}:$companyId:$number".toByteArray(Charsets.UTF_8)).toString()
    private fun snapshot(i: ExcelInvoice, customerId: Long) =
        InvoiceCreationSnapshot(customerId,i.number,i.date,i.lines,i.mode,i.cash)

    private suspend fun existingBuyer(id: String): Customer? {
        val matches = db.customerDao().forCompany(companyId).filter { digits(it.nationalId)==id }
        require(matches.size <= 1) { "چند مشتری با شناسه $id ثبت شده‌اند؛ ابتدا تکرار را تعیین تکلیف کنید." }
        return matches.singleOrNull()?.also {
            val p=db.customerProfileDao().byCustomer(it.id)
            require(p==null || (p.companyId==companyId && p.active && p.isCustomer)) { "مشتری $id غیرفعال یا غیرمجاز است." }
        }
    }

    suspend fun preview(batch: ExcelInvoiceBatch): List<ExcelInvoicePreview> = db.withTransaction {
        company()
        val existing = db.invoiceDao().forCompany(companyId).associateBy { InvoiceInput.number(it.invoiceNumber) }
        val rejected = db.rejectedSubmissionHistoryDao().forCompany(companyId).map { InvoiceInput.number(it.invoiceNumber) }.toSet()
        val pending = creation.pending(companyId)
        require(pending==null || batch.invoices.any { key(it.number)==pending.requestId }) {
            "یک درخواست صدور دیگر ناتمام است؛ ابتدا همان درخواست را تکمیل کنید."
        }
        batch.invoices.map { i ->
            val customer = existingBuyer(i.buyer)
            val supplied = batch.buyers[i.buyer]
            require(customer!=null || supplied!=null) { "صورتحساب ${i.number}: اطلاعات مشتری جدید ${i.buyer} در کاربرگ مشتریان نیست." }
            val claim = db.invoiceCreationClaimDao().byKey(key(i.number))
            if(claim!=null) {
                require(claim.ownerId==account.ownerId && claim.companyId==companyId && customer!=null &&
                    creation.snapshot(claim)==snapshot(i,customer.id)) {
                    "صورتحساب ${i.number} قبلاً با اطلاعات دیگری ثبت یا محفوظ شده است."
                }
                if(claim.invoiceId!=null) require(db.invoiceDao().byId(claim.invoiceId)?.companyId==companyId)
                else require(i.number !in existing && i.number !in rejected)
            } else {
                require(i.number !in existing && i.number !in rejected) { "شماره ${i.number} قبلاً استفاده شده است." }
            }
            ExcelInvoicePreview(i,customer?.name ?: requireNotNull(supplied).name,customer==null,
                claim?.invoiceId!=null,claim!=null && claim.invoiceId==null)
        }
    }

    private suspend fun getOrCreateBuyer(id: String, buyer: ExcelBuyer?): Long = db.withTransaction {
        company()
        existingBuyer(id)?.let { return@withTransaction it.id }
        val b = requireNotNull(buyer) { "اطلاعات مشتری جدید کامل نیست." }
        val customerId=db.customerDao().insert(Customer(companyId=companyId,name=b.name,nationalId=b.national,
            economicId=b.economic,postalCode=b.postal,address=b.address,phone=b.phone))
        db.customerProfileDao().upsert(CustomerProfile(customerId=customerId,companyId=companyId,accountType=b.type))
        customerId
    }

    /** A stopped batch can be replayed unchanged without double charging or duplicate drafts. */
    suspend fun register(batch: ExcelInvoiceBatch, kind: InvoiceCreditKind, progress: (Int,Int)->Unit): Int {
        val plan = preview(batch).filterNot{it.complete}.sortedByDescending{it.pending}
        var done = 0
        for (p in plan) {
            val i=p.invoice
            val request=key(i.number)
            val customerId=getOrCreateBuyer(i.buyer,batch.buyers[i.buyer])
            val prior=db.invoiceCreationClaimDao().byKey(request)
            val actualKind=prior?.let{InvoiceCreditKind.valueOf(it.creditKind)} ?: kind
            val claim=creation.prepare(companyId,snapshot(i,customerId),request,actualKind)
            require(claim.requestId==request) { "درخواست صدور دیگری فعال است." }
            creation.finish(request)
            done++
            progress(done,plan.size)
        }
        return done
    }
}
