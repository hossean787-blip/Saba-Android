package ir.saba.invoice.data

import org.junit.Assert.*
import org.junit.Test

class ExcelWorkbookTest {
    private fun externalWorkbook(xml: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    @Test fun numericScientificCellsAndSparseColumnsAreReadWithoutRounding() {
        val xml = """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1"><c r="A1" t="n"><v>1.234567890123E+12</v></c><c r="C1" t="inlineStr"><is><t>خدمت</t></is></c></row>
            </sheetData></worksheet>"""
        assertEquals(listOf(listOf("1234567890123", "", "خدمت")), ExcelWorkbook.read(externalWorkbook(xml)))
    }

    @Test fun rejectsDtdAndFormulaEvenWithCachedValue() {
        val xml = """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row><c><f>1+1</f><v>2</v></c></row></sheetData></worksheet>"""
        for (bad in listOf(xml, """<!DOCTYPE worksheet [<!ENTITY x SYSTEM "file:///etc/passwd">]>""" + xml)) {
            try {
                ExcelWorkbook.read(externalWorkbook(bad))
                fail("Unsafe workbook accepted")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun outputStylesPreserveTextAndLeadingZeroesInSpreadsheetEditors() {
        val bytes = ExcelWorkbook.write(listOf("شناسه"), listOf(listOf("0012345678901")))
        val parts = mutableMapOf<String, String>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = String(zip.readBytes(), Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        assertTrue(parts.getValue("xl/styles.xml").contains("numFmtId=\"49\""))
        assertTrue(parts.getValue("xl/worksheets/sheet1.xml").contains("r=\"A2\" s=\"1\""))
        assertEquals("0012345678901", ExcelWorkbook.read(bytes)[1][0])
    }

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
