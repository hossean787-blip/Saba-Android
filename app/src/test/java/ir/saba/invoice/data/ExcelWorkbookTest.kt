package ir.saba.invoice.data

import org.junit.Assert.*
import org.junit.Test

class ExcelWorkbookTest {
    @Test fun xlsxRoundTripPreservesPersianTextAndLeadingZeros() {
        val headers = listOf("نام", "شناسه ملی", "موبایل")
        val rows = listOf(listOf("شرکت صبا & گستر", "0012345678", "09120000000"),
            listOf("<مشتری>", "۰۰۱۲۳۴", ""))
        val decoded = ExcelWorkbook.read(ExcelWorkbook.write(headers, rows))
        assertEquals(listOf(headers) + rows, decoded)
    }

    @Test fun rejectsInvalidFilesAndHugeImports() {
        try {
            ExcelWorkbook.read("not a workbook".toByteArray())
            fail("Expected invalid xlsx rejection")
        } catch (_: Exception) { }
        try {
            ExcelWorkbook.write(listOf("نام"), List(10001) { listOf("تکراری") })
            fail("Expected max rows rejection")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun emptyTemplateHasOnlyHeaderRow() {
        val header = listOf("نام", "شناسه ملی")
        assertEquals(listOf(header), ExcelWorkbook.read(ExcelWorkbook.write(header, emptyList())))
    }
}
