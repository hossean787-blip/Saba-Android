package ir.saba.moadian

import java.math.BigDecimal

/** Strict local subset of a type-one sales invoice, NOT a complete government schema. */
data class TaxInvoiceHeader(
    val taxId: String,
    val fiscalMemoryId: String,
    val sellerEconomicId: String,
    val buyerEconomicId: String,
    val issuedAtEpochMillis: Long,
    val invoiceType: Int = 1,
    val pattern: Int = 1,
    val subject: Int = 1
)
data class TaxInvoiceItem(
    val productServiceId: String,
    val description: String,
    val quantity: BigDecimal,
    val unitPriceRial: BigDecimal,
    val discountRial: BigDecimal,
    val vatPercent: BigDecimal,
    val unitOfMeasureCode: String? = null
)
data class TaxInvoiceContract(val header: TaxInvoiceHeader, val items: List<TaxInvoiceItem>)

object TaxInvoiceValidation {
    fun validate(invoice: TaxInvoiceContract): TaxInvoiceContract {
        val h = invoice.header
        require(h.fiscalMemoryId.matches(Regex("[A-Za-z0-9]{6}")))
        val taxId = TaxIdGenerator.validate(h.taxId)
        require(taxId.memoryId == h.fiscalMemoryId)
        require(taxId.dayHex.toLong(16) == h.issuedAtEpochMillis / 86_400_000L) {
            "تاریخ شماره مالیاتی با تاریخ صدور صورتحساب مطابقت ندارد."
        }
        require(h.sellerEconomicId.matches(Regex("[0-9]{11,14}")))
        require(h.buyerEconomicId.matches(Regex("[0-9]{11,14}")))
        require(h.issuedAtEpochMillis > 0L && h.invoiceType == 1 && h.pattern == 1 && h.subject == 1)
        require(invoice.items.isNotEmpty())
        val lines = invoice.items.map {
            require(it.productServiceId.matches(Regex("[0-9]{13}")) && it.description.isNotBlank())
            require(it.unitPriceRial > BigDecimal.ZERO)
            require(it.unitOfMeasureCode == null || MoadianMeasurementUnits.nameOf(it.unitOfMeasureCode) != null) {
                "کد واحد اندازه‌گیری در جدول مرجع موجود نیست."
            }
            MoadianLine(it.productServiceId, it.description,
                it.quantity, it.unitPriceRial, it.discountRial, it.vatPercent)
        }
        MoadianCalculator.total(lines)
        return invoice
    }
}
