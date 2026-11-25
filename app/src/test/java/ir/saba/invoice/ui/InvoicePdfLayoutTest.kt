package ir.saba.invoice.ui

import org.junit.Assert.*
import org.junit.Test

class InvoicePdfLayoutTest {
    @Test fun everySupportedRowCountKeepsTableTotalsAndSignatureInsideA4() {
        for(count in 1..InvoicePdfPagination.ROWS_PER_PAGE) {
            val layout=InvoicePdfLayout(count)
            assertEquals(layout.rowsBottom,layout.totalsTop,0f)
            assertEquals(5,layout.displayRows)
            assertEquals(303f,layout.rowTop,0f)
            assertEquals(423f,layout.rowsBottom,0f)
            assertEquals(457f,layout.totalsBottom,0f)
            assertTrue(layout.totalsBottom < layout.signatureTop)
            assertTrue(layout.signatureTop + 63 < layout.footerTop)
            assertTrue(layout.footerTop + 22 < layout.pageHeight)
        }
    }
    @Test fun payableWordsAreExact() {
        assertEquals("هفت میلیون و هفتصد هزار ریال",InvoicePdfPersianWords.rials(7700000))
        assertEquals("صفر ریال",InvoicePdfPersianWords.rials(0))
    }
}
