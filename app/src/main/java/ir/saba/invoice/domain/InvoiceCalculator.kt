package ir.saba.invoice.domain

import ir.saba.invoice.data.InvoiceLine
import java.math.BigDecimal
import java.math.RoundingMode

data class InvoiceTotals(val netAmountRial: Long, val taxAmountRial: Long, val totalAmountRial: Long)

object InvoiceCalculator {
    fun calculate(lines: List<InvoiceLine>): InvoiceTotals {
        require(lines.isNotEmpty()) { "فاکتور باید حداقل یک ردیف داشته باشد." }
        var net = BigDecimal.ZERO
        var tax = BigDecimal.ZERO
        lines.forEach { line ->
            require(line.description.isNotBlank()) { "شرح کالا یا خدمت الزامی است." }
            require(line.quantity > 0) { "تعداد باید بیشتر از صفر باشد." }
            require(line.unitPriceRial >= 0 && line.discountRial >= 0) { "مبلغ و تخفیف نمی‌توانند منفی باشند." }
            require(line.discountRial <= Math.multiplyExact(line.quantity, line.unitPriceRial)) { "تخفیف از مبلغ ردیف بیشتر است." }
            require(line.taxRatePercent in 0..100) { "نرخ مالیات خارج از محدوده است." }
            val rowNet = BigDecimal.valueOf(Math.multiplyExact(line.quantity, line.unitPriceRial) - line.discountRial)
            net = net.add(rowNet)
            tax = tax.add(rowNet.multiply(BigDecimal.valueOf(line.taxRatePercent.toLong())).divide(BigDecimal.valueOf(100)))
        }
        val netLong = net.setScale(0, RoundingMode.HALF_UP).longValueExact()
        val taxLong = tax.setScale(0, RoundingMode.HALF_UP).longValueExact()
        return InvoiceTotals(netLong, taxLong, Math.addExact(netLong, taxLong))
    }
}
