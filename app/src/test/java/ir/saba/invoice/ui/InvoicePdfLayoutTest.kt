package ir.saba.invoice.ui

import org.junit.Assert.*
import org.junit.Test

class InvoicePdfLayoutTest {
    @Test fun everySupportedRowCountKeepsTableTotalsAndSignatureInsideA4() {
        for(count in 1..InvoicePdfPagination.ROWS_PER_PAGE) {
            val layout=InvoicePdfLayout(count)
            assertTrue(layout.rowsBottom + 12f <= layout.totalsTop)
            assertTrue(layout.rowTop >= 300f)
            assertTrue(layout.totalsBottom < layout.signatureTop)
            assertTrue(layout.signatureTop + 28 <= 779f)
            assertTrue(779f < layout.footerTop)
            assertTrue(layout.footerTop + 30 < layout.pageHeight)
        }
    }
}
