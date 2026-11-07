package ir.saba.invoice.security

import ir.saba.invoice.data.*
import ir.saba.invoice.domain.InvoiceInput
import ir.saba.invoice.domain.InvoiceSettlement
import ir.saba.invoice.domain.InvoiceSubject
import ir.saba.invoice.domain.InvoiceLineAmounts
import ir.saba.moadian.*
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.*

/** Explicitly supported: original type-one, pattern-one, domestic rial cash sale only. */
object MoadianInvoiceMapper {
    fun defaultIssueTime(jalali: String): String {
        val parts = InvoiceInput.date(jalali).split('/').map(String::toInt)
        fun leap(y: Int) = (((y - 1210 + 1) % 33) - 1) % 4 == 0
        var days = 0L
        if (parts[0] >= 1404) for (y in 1404 until parts[0]) days += if (leap(y)) 366 else 365
        else for (y in parts[0] until 1404) days -= if (leap(y)) 366 else 365
        for (m in 1 until parts[1]) days += if (m <= 6) 31 else 30
        return LocalDate.of(2025,3,21).plusDays(days + parts[2] - 1)
            .atTime(12,0).atZone(ZoneId.of("Asia/Tehran")).toOffsetDateTime().toString()
    }

    fun map(company: Company, customer: Customer, buyerProfile: CustomerProfile?, details: MoadianDetails,
            invoice: Invoice, lines: List<InvoiceLine>, issueTime: String, unit: String, now: Long): String {
        require(invoice.status == "FINALIZED" && invoice.companyId == company.id && customer.id == invoice.customerId && customer.companyId == company.id)
        // The current official mapper encodes setm=1. Never send a noncash invoice as cash.
        InvoiceSettlement.requireSupportedForOfficialCash(invoice)
        InvoiceSubject.requireSupportedForOfficialSubmission(invoice)
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
        require(total.netRial > BigDecimal.ZERO) { "در مسیر فروش نقدی، مبلغ پس از تخفیف باید مثبت باشد." }
        val body = JSONArray()
        calculated.forEachIndexed { index, line ->
            val t = MoadianCalculator.calculate(line)
            body.put(JSONObject().put("sstid",line.productId).put("sstt",line.description)
                .put("mu",lines[index].unitCode.ifBlank { unit })
                .put("am",line.quantity).put("fee",line.unitPriceRial).put("prdis",t.grossRial).put("dis",t.discountRial)
                .put("adis",t.netRial).put("vra",line.vatPercent).put("vam",t.vatRial).put("cop",t.payableRial).put("vop",t.vatRial).put("tsstam",t.payableRial))
        }
        val header = JSONObject().put("taxid",taxId).put("indatim",issued).put("inty",1).put("inno",serial.toString(16).uppercase(java.util.Locale.ROOT).padStart(10,'0'))
            .put("inp",1).put("ins",1).put("tins",company.economicId).put("tob",if(legal) 2 else 1)
            .put("bid",customer.nationalId).put("tinb",customer.economicId).put("setm",1)
            .put("tprdis",total.grossRial).put("tdis",total.discountRial).put("tadis",total.netRial).put("tvam",total.vatRial)
            .put("todam",0).put("tvop",total.vatRial).put("tbill",total.payableRial).put("cap",total.netRial) // V7.9 table 25: cap = tbill - VAT - other taxes - credit amount.
        return JSONObject().put("header",header).put("body",body).put("payments",JSONArray()).toString()
    }
}
