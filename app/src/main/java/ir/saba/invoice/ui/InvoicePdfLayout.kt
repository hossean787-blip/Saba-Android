package ir.saba.invoice.ui

/** A4 geometry shared by drawing and bounds tests; coordinates are PDF points. */
internal data class InvoicePdfLayout(val rows: Int) {
    init { require(rows in 1..InvoicePdfPagination.ROWS_PER_PAGE) }
    val rowTop = 303f
    val rowHeight = 24f
    val rowsBottom get() = rowTop + rows * rowHeight
    val totalsTop get() = maxOf(rowsBottom + 16f,605f)
    val totalsBottom get() = totalsTop + 105f
    val signatureTop get() = maxOf(750f,totalsBottom + 10f)
    val footerTop = 801f
    val pageHeight = 842f
}

/** Deterministic pagination, isolated from android.graphics.pdf for JVM testing. */
internal object InvoicePdfPagination {
    const val ROWS_PER_PAGE = 13
    const val MAX_ROWS = 500

    fun <T> paginate(items: List<T>): List<List<T>> {
        require(items.size in 1..MAX_ROWS) { "تعداد اقلام PDF باید بین ۱ تا ۵۰۰ باشد." }
        return items.chunked(ROWS_PER_PAGE)
    }
}

/** Preserve the exact stored rounding convention, including older mixed-rate invoices.
 * Allocate legacy fractional rial differences by cumulative rounding so printed rows sum exactly. */
internal object InvoicePdfRowAmounts {
    fun calculate(lines: List<ir.saba.invoice.data.InvoiceLine>): List<ir.saba.invoice.domain.InvoiceTotals> {
        val precise = lines.any(ir.saba.invoice.domain.InvoiceLineAmounts::isPrecise)
        var cumulativeTax = java.math.BigDecimal.ZERO
        var previousTax = 0L
        return lines.map { line ->
            if (precise) {
                val exact = if (ir.saba.invoice.domain.InvoiceLineAmounts.isPrecise(line)) line
                    else line.copy(quantityExact = line.quantity.toString())
                ir.saba.invoice.domain.InvoiceCalculator.calculate(listOf(exact))
            } else {
                val net = Math.subtractExact(Math.multiplyExact(line.quantity,line.unitPriceRial),line.discountRial)
                cumulativeTax = cumulativeTax.add(java.math.BigDecimal.valueOf(net)
                    .multiply(java.math.BigDecimal.valueOf(line.taxRatePercent.toLong())).movePointLeft(2))
                val rounded = cumulativeTax.setScale(0,java.math.RoundingMode.HALF_UP).longValueExact()
                val tax = Math.subtractExact(rounded,previousTax)
                previousTax = rounded
                ir.saba.invoice.domain.InvoiceTotals(net,tax,Math.addExact(net,tax))
            }
        }
    }
}
