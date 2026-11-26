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
    @Test fun allDisplayDigitsArePersianWithoutChangingLettersOrLeadingZeroes() {
        assertEquals("A۰۱۲۳۴۵۶۷۸۹/۰۰۱",invoicePdfDisplayDigits("A0123456789/٠٠١"))
        assertEquals("۱۴۰۵/۰۹/۰۴ — ۱۲۳.۵٪",invoicePdfDisplayDigits("1405/٠٩/۰۴ — 123.5٪"))
        assertEquals("۰۰۱۲",invoicePdfDisplayDigits(invoicePdfDisplayDigits("0012")))
    }
    @Test fun zoomClampsPanAndReturnsToExactFit() {
        val size=androidx.compose.ui.unit.IntSize(800,600)
        val offset=androidx.compose.ui.geometry.Offset(10000f,-10000f)
        val zoom=PdfZoom().change(10f,offset,size)
        assertEquals(5f,zoom.scale,0f)
        assertEquals(1600f,zoom.x,0f)
        assertEquals(-1200f,zoom.y,0f)
        assertEquals(PdfZoom(),zoom.change(.01f,offset,size))
        assertEquals(PdfZoom(),PdfZoom().change(1f,offset,size))
    }
    @Test fun payableWordsAreExact() {
        assertEquals("هفت میلیون و هفتصد هزار ریال",InvoicePdfPersianWords.rials(7700000))
        assertEquals("صفر ریال",InvoicePdfPersianWords.rials(0))
    }
}
