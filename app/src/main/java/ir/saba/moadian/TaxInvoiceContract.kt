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
    val vatPercent: BigDecimal
)
data class TaxInvoiceContract(val header: TaxInvoiceHeader, val items: List<TaxInvoiceItem>)

object TaxInvoiceValidation {
    fun validate(invoice: TaxInvoiceContract): TaxInvoiceContract {
        val h = invoice.header
        require(h.fiscalMemoryId.matches(Regex("[A-Za-z0-9]{6}")))
        require(h.taxId.length == 22 && h.taxId.startsWith(h.fiscalMemoryId))
        require(h.sellerEconomicId.matches(Regex("[0-9]{11,14}")))
        require(h.buyerEconomicId.matches(Regex("[0-9]{11,14}")))
        require(h.issuedAtEpochMillis > 0L && h.invoiceType == 1 && h.pattern == 1 && h.subject == 1)
        require(invoice.items.isNotEmpty())
        invoice.items.forEach {
            require(it.productServiceId.isNotBlank() && it.description.isNotBlank())
            MoadianCalculator.calculate(MoadianLine(it.productServiceId, it.description,
                it.quantity, it.unitPriceRial, it.discountRial, it.vatPercent))
        }
        return invoice
    }
}
