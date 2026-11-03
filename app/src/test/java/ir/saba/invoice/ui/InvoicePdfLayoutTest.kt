package ir.saba.invoice.ui

import org.junit.Assert.*
import org.junit.Test

class InvoicePdfLayoutTest {
    @Test fun everySupportedRowCountKeepsTableTotalsAndSignatureInsideA4() {
        for(count in 1..14) {
            val layout=InvoicePdfLayout(count)
            assertTrue(layout.rowsBottom < layout.totalsTop)
            assertTrue(layout.totalsBottom < layout.signatureTop)
            assertTrue(layout.signatureTop + 28 <= layout.footerTop)
            assertTrue(layout.footerTop + 30 < layout.pageHeight)
        }
    }
}
