package ir.saba.invoice.ui

/** A4 geometry shared by drawing and bounds tests; coordinates are PDF points. */
internal data class InvoicePdfLayout(val rows: Int) {
    init { require(rows in 1..14) }
    val rowTop = 309f
    val rowHeight = 24f
    val rowsBottom get() = rowTop + rows * rowHeight
    val totalsTop get() = maxOf(rowsBottom + 12f,605f)
    val totalsBottom get() = totalsTop + 105f
    val signatureTop get() = maxOf(750f,totalsBottom + 10f)
    val footerTop = 801f
    val pageHeight = 842f
}

/** Deterministic pagination, isolated from android.graphics.pdf for JVM testing. */
internal object InvoicePdfPagination {
    const val ROWS_PER_PAGE = 14
    const val MAX_ROWS = 500

    fun <T> paginate(items: List<T>): List<List<T>> {
        require(items.size in 1..MAX_ROWS) { "تعداد اقلام PDF باید بین ۱ تا ۵۰۰ باشد." }
        return items.chunked(ROWS_PER_PAGE)
    }
}
