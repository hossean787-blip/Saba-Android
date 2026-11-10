package ir.saba.invoice.security

import ir.saba.invoice.data.*
import ir.saba.invoice.domain.InvoiceInput
import ir.saba.invoice.domain.InvoiceSettlement
import ir.saba.invoice.domain.InvoiceSubject
import ir.saba.invoice.domain.InvoiceLineAmounts
import ir.saba.invoice.domain.JalaliCalendar
import ir.saba.moadian.*
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.*

/** Type-one pattern-one original sales, rial settlement 1/2/3 with strict amount reconciliation. */
object MoadianInvoiceMapper {
    fun defaultIssueTime(jalali: String): String =
        JalaliCalendar.toGregorian(jalali).atTime(12, 0)
            .atZone(ZoneId.of("Asia/Tehran")).toOffsetDateTime().toString()

    fun map(company: Company, customer: Customer, buyerProfile: CustomerProfile?, details: MoadianDetails,
            invoice: Invoice, lines: List<InvoiceLine>, issueTime: String, unit: String, now: Long): String {
        require(invoice.status == "FINALIZED" && invoice.companyId == company.id && customer.id == invoice.customerId && customer.companyId == company.id)
        // Never disguise a referred invoice as a new original invoice.
        val officialSubject = InvoiceSubject.officialSubjectCode(invoice)
        val local = InvoiceLocalDetails.fromJson(invoice.localDetailsJson)
        require(!local.article9Flag) {
            "ارسال رسمی صورتحساب موضوع ماده ۹ در این نسخه اعتبارسنجی نشده است؛ ارسال معمولی ممنوع است."
        }
        require(local.note1.length <= 30 && local.note2.length <= 30) {
            "برای ارسال رسمی، هر یک از یادداشت‌های ۱ و ۲ طبق سند ۷.۹ حداکثر ۳۰ نویسه است."
        }
        require(!details.inactive && details.companyId == company.id)
        require(buyerProfile == null || (buyerProfile.customerId == customer.id && buyerProfile.companyId == company.id && buyerProfile.active && buyerProfile.isCustomer && buyerProfile.nationality == "IR"))
        require(lines.all { MoadianMeasurementUnits.nameOf(it.unitCode.ifBlank { unit }) != null }) {
            "برای هر ردیف واحد سنجش معتبر انتخاب کنید؛ برای فاکتورهای قدیمی می‌توان واحد مشترک وارد کرد."
        }
        require(lines.all { it.invoiceId == invoice.id })
        val issued = OffsetDateTime.parse(issueTime).toInstant().toEpochMilli()
        val expectedDay = OffsetDateTime.parse(defaultIssueTime(invoice.issueDate)).atZoneSameInstant(ZoneId.of("Asia/Tehran")).toLocalDate()
        require(Instant.ofEpochMilli(issued).atZone(ZoneId.of("Asia/Tehran")).toLocalDate() == expectedDay) { "زمان صدور باید در تاریخ شمسی فاکتور باشد." }
        require(issued > 0 && issued <= now) { "زمان صدور نباید در آینده باشد." }
        val serial = InvoiceInput.wholeNumber(invoice.invoiceNumber,"شماره فاکتور")
        val taxId = TaxIdGenerator.generate(details.fiscalMemoryId,serial,issued)
        val contract = TaxInvoiceContract(TaxInvoiceHeader(taxId,details.fiscalMemoryId,company.economicId,customer.economicId,issued),
            lines.map { TaxInvoiceItem(it.itemCode,it.description,
                InvoiceLineAmounts.effectiveQuantity(it),BigDecimal.valueOf(it.unitPriceRial),
                BigDecimal.valueOf(it.discountRial),InvoiceLineAmounts.effectiveTaxPercent(it),
                it.unitCode.ifBlank { unit }) })
        TaxInvoiceValidation.validate(contract)
        val legal = when (buyerProfile?.accountType ?: if (customer.nationalId.length == 11) "LEGAL" else "NATURAL") {
            "LEGAL" -> true
            "NATURAL" -> false
            else -> error("در این نسخه فقط خریدار حقیقی یا حقوقی ایرانی پشتیبانی می‌شود.")
        }
        require(company.economicId.matches(Regex("(?:[0-9]{11}|[0-9]{14})"))) { "شماره اقتصادی فروشنده باید ۱۱ یا ۱۴ رقم باشد." }
        require(customer.economicId.matches(if (legal) Regex("[0-9]{11}") else Regex("[0-9]{14}"))) { "شماره اقتصادی خریدار با نوع شخص مطابقت ندارد." }
        require(lines.all { it.description.length <= 400 }) { "شرح کالا نباید بیش از ۴۰۰ نویسه باشد." }
        require(customer.nationalId.matches(if (legal) Regex("[0-9]{11}") else Regex("[0-9]{10}"))) { "شناسه خریدار با نوع شخص مطابقت ندارد." }
        val calculated = contract.items.map { MoadianLine(it.productServiceId,it.description,it.quantity,it.unitPriceRial,it.discountRial,it.vatPercent) }
        val total = MoadianCalculator.total(calculated)
        require(total.payableRial.longValueExact() == invoice.totalAmountRial && total.vatRial.longValueExact() == invoice.taxAmountRial) { "جمع فاکتور با قواعد مالیاتی تطابق ندارد؛ ارسال متوقف شد." }
        require(total.netRial > BigDecimal.ZERO) { "مبلغ پس از تخفیف باید مثبت باشد." }
        val settlement = MoadianRialSettlementCalculator.calculate(
            invoice, calculated.map(MoadianCalculator::calculate)
        )
        val body = JSONArray()
        calculated.forEachIndexed { index, line ->
            val t = MoadianCalculator.calculate(line)
            body.put(JSONObject().put("sstid",line.productId).put("sstt",line.description)
                .put("mu",lines[index].unitCode.ifBlank { unit })
                .put("am",line.quantity).put("fee",line.unitPriceRial).put("prdis",t.grossRial).put("dis",t.discountRial)
                .put("adis",t.netRial).put("vra",line.vatPercent).put("vam",t.vatRial)
                .put("cop",settlement.lineCashInclusive[index]).put("vop",settlement.linePaidVat[index])
                .put("tsstam",t.payableRial))
        }
        val header = JSONObject().put("taxid",taxId).put("indatim",issued).put("inty",1).put("inno",serial.toString(16).uppercase(java.util.Locale.ROOT).padStart(10,'0'))
            .put("inp",1).put("ins",officialSubject).put("tins",company.economicId).put("tob",if(legal) 2 else 1)
            .put("bid",customer.nationalId).put("tinb",customer.economicId).put("setm",settlement.method)
            .put("tprdis",total.grossRial).put("tdis",total.discountRial).put("tadis",total.netRial).put("tvam",total.vatRial)
            .put("todam",0).put("tvop",settlement.totalPaidVat).put("tbill",total.payableRial)
            .put("cap",settlement.cashBase)
        if (officialSubject != 1) header.put("irtaxid", requireNotNull(invoice.referenceTaxId))
        if (settlement.method != 1) header.put("insp", settlement.creditBase)
        if (local.note1.isNotBlank()) header.put("nti1", local.note1)
        if (local.note2.isNotBlank()) header.put("nti2", local.note2)
        return JSONObject().put("header",header).put("body",body).put("payments",JSONArray()).toString()
    }
}
