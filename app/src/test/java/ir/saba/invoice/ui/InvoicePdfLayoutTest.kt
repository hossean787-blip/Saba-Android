package ir.saba.invoice.ui

import org.junit.Assert.*
import org.junit.Test

class InvoicePdfLayoutTest {
    @Test fun everySupportedRowCountKeepsTableTotalsAndSignatureInsideA4() {
        for(count in 1..InvoicePdfPagination.ROWS_PER_PAGE) {
            val layout=InvoicePdfLayout(count)
            assertEquals(layout.rowsBottom,layout.totalsTop,0f)
            assertEquals(5,layout.displayRows)
            assertTrue(layout.totalsBottom < layout.signatureTop)
            assertTrue(layout.signatureTop + 62 < layout.footerTop)
            assertTrue(layout.footerTop + 22 < layout.pageHeight)
        }
    }
    @Test fun payableWordsAreExact() {
        assertEquals("هفت میلیون و هفتصد هزار ریال",InvoicePdfPersianWords.rials(7700000))
        assertEquals("صفر ریال",InvoicePdfPersianWords.rials(0))
    }
}
