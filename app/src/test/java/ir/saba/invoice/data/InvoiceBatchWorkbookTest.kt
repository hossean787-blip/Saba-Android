package ir.saba.invoice.data

import org.junit.Assert.*
import org.junit.Test

class InvoiceBatchWorkbookTest {
    @Test fun threeSheetsRoundTripAndPreserveBuyerIdentity() {
        val rows=listOf(
            listOf(listOf("1000000001","1405/09/05","0012345678","0123456789","1234567890123","خدمت","1627","2","1000","0","10","نقدی","")),
            listOf(listOf("0012345678","مؤدی","")),
            listOf(listOf("0123456789","مشتری","حقیقی","","","","")))
        assertEquals(rows, InvoiceBatchWorkbook.read(InvoiceBatchWorkbook.write(rows)))
    }
    @Test fun templateContainsPrefilledTaxpayerAndEmptyData() {
        val rows=InvoiceBatchWorkbook.read(InvoiceBatchWorkbook.template(Company(purchaserId="owner",name="شرکت",nationalId="0012345678")))
        assertEquals(listOf(listOf("0012345678","شرکت","")), rows[1])
        assertTrue(rows[0].isEmpty());assertTrue(rows[2].isEmpty())
    }
    @Test fun singleSheetIsRejected() {
        try {
            InvoiceBatchWorkbook.read(ExcelWorkbook.write(InvoiceBatchWorkbook.invoiceHeaders, emptyList()))
            fail("Single sheet accepted")
        } catch (_:IllegalStateException) {}
    }
}
