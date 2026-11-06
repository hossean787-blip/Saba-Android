package ir.saba.invoice.domain

import ir.saba.invoice.data.InvoiceLine
import ir.saba.moadian.MoadianCalculator
import ir.saba.moadian.MoadianLine
import java.math.BigDecimal
import java.math.RoundingMode

data class InvoiceTotals(val netAmountRial: Long, val taxAmountRial: Long, val totalAmountRial: Long)

object InvoiceCalculator {
    fun calculate(lines: List<InvoiceLine>): InvoiceTotals {
        require(lines.isNotEmpty()) { "فاکتور باید حداقل یک ردیف داشته باشد." }
        // New precise lines follow the official per-item truncated-rial math.
        // Previously saved whole-number lines alone retain their original local rounding
        // so existing PDFs, totals and financial records cannot be silently rewritten.
        if (lines.any(InvoiceLineAmounts::isPrecise)) {
            val taxLines = lines.map { line ->
                MoadianLine(
                    productId = line.itemCode.ifBlank { "LOCAL_ONLY" },
                    description = line.description,
                    quantity = InvoiceLineAmounts.effectiveQuantity(line),
                    unitPriceRial = BigDecimal.valueOf(line.unitPriceRial),
                    discountRial = BigDecimal.valueOf(line.discountRial),
                    vatPercent = InvoiceLineAmounts.effectiveTaxPercent(line)
                )
            }
            val totals = MoadianCalculator.total(taxLines)
            return InvoiceTotals(
                totals.netRial.longValueExact(), totals.vatRial.longValueExact(),
                totals.payableRial.longValueExact()
            )
        }
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
