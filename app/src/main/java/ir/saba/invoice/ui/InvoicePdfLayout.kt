package ir.saba.invoice.ui

/** A4 geometry shared by drawing and bounds tests; coordinates are PDF points. */
internal data class InvoicePdfLayout(val rows: Int) {
    init { require(rows in 1..InvoicePdfPagination.ROWS_PER_PAGE) }
    val rowTop = 298f
    val rowHeight = 24f
    val displayRows get() = InvoicePdfPagination.ROWS_PER_PAGE
    val rowsBottom get() = rowTop + displayRows * rowHeight
    val totalsTop get() = rowsBottom
    val totalsBottom get() = totalsTop + 30f
    val signatureTop get() = totalsBottom + 34f
    val footerTop = 556f
    val pageHeight = 595f
}

/** Deterministic pagination, isolated from android.graphics.pdf for JVM testing. */
internal object InvoicePdfPagination {
    const val ROWS_PER_PAGE = 5
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

internal object InvoicePdfPersianWords {
    private val small=listOf("صفر","یک","دو","سه","چهار","پنج","شش","هفت","هشت","نه","ده","یازده","دوازده","سیزده","چهارده","پانزده","شانزده","هفده","هجده","نوزده")
    private val tens=listOf("","","بیست","سی","چهل","پنجاه","شصت","هفتاد","هشتاد","نود")
    private val hundreds=listOf("","صد","دویست","سیصد","چهارصد","پانصد","ششصد","هفتصد","هشتصد","نهصد")
    private val groups=listOf("","هزار","میلیون","میلیارد","تریلیون","کوادریلیون","کوینتیلیون")
    private fun part(n:Int):String {
        val r=n%100
        return listOf(hundreds[n/100],if(r<20)small[r].takeIf{r!=0}.orEmpty() else listOf(tens[r/10],small[r%10].takeIf{r%10!=0}.orEmpty()).filter{it.isNotBlank()}.joinToString(" و ")).filter{it.isNotBlank()}.joinToString(" و ")
    }
    fun rials(value:Long):String {
        require(value>=0)
        if(value==0L)return "صفر ریال"
        var n=value;var index=0;val chunks=mutableListOf<String>()
        while(n>0){val v=(n%1000).toInt();if(v>0)chunks.add(listOf(part(v),groups[index]).filter{it.isNotBlank()}.joinToString(" "));n/=1000;index++}
        return chunks.asReversed().joinToString(" و ")+" ریال"
    }
}
