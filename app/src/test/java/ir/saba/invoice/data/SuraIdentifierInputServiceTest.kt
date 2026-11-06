package ir.saba.invoice.data

import ir.saba.invoice.data.identifiers.SuraIdentifierInputService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SuraIdentifierInputServiceTest {
    @Test fun tenRowTemplateHasFiveExactColumnsAndNoDummyData() {
        val values = ExcelWorkbook.read(SuraIdentifierInputService.template())
        assertEquals(11, values.size)
        assertEquals(SuraIdentifierInputService.headers, values[0])
        assertTrue(values.drop(1).all { row -> row.all(String::isBlank) })
    }

    @Test fun importsTenValidItemsWithoutAnyInstalledLargeDatabase() {
        val rows = (0 until 10).map { i ->
            listOf((1_000_000_000_000L + i).toString(),
                "شرح کالا " + (i + 1), "", "کالا", "اختصاصی")
        }
        val content = ExcelWorkbook.write(SuraIdentifierInputService.headers, rows)
        val output = SuraIdentifierInputService.parseXlsx(content)
        assertEquals(10, output.size)
        assertEquals("", output.first().vat)
        assertEquals("اختصاصی کالا", output.first().kind)
        assertEquals("ورود از اکسل", output.first().source)
    }

    @Test fun acceptsPersianDigitsAndOptionalVatWithoutGuessingTheTaxRate() {
        val item = SuraIdentifierInputService.item("۱۲۳۴۵۶۷۸۹۰۱۲۳", "  خدمت آزمایشی  ", "", "خدمت", "عمومی")
        assertEquals("1234567890123", item.id)
        assertEquals("خدمت آزمایشی", item.description)
        assertEquals("", item.vat)
        assertEquals("عمومی خدمت", item.kind)
        val taxed = SuraIdentifierInputService.item("1234567890123", "تست", "۹٫۵", "", "")
        assertEquals("9.5", taxed.vat)
    }

    @Test fun rejectsDuplicateOrMalformedExcelBeforeSavingAnything() {
        val row = listOf("1234567890123", "خدمت", "10", "خدمت", "اختصاصی")
        val dup = ExcelWorkbook.write(SuraIdentifierInputService.headers, listOf(row, row))
        try {
            SuraIdentifierInputService.parseXlsx(dup)
            fail("Repeated identifier should not be imported")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("تکراری"))
        }
        val malformed = ExcelWorkbook.write(SuraIdentifierInputService.headers,
            listOf(listOf("no-code", "کالا", "", "کالا", "")))
        try {
            SuraIdentifierInputService.parseXlsx(malformed)
            fail("Invalid identifier should not be imported")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("ردیف 2"))
        }
        try {
            SuraIdentifierInputService.item("1234567890123", "نام", "110", "کالا", "")
            fail("VAT above 100 should be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun acceptsPrefixedXlsxXmlProducedBySpreadsheetEditors() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
            <x:worksheet xmlns:x="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <x:sheetData><x:row r="1"><x:c r="A1" t="str"><x:v>شناسه</x:v></x:c></x:row>
              <x:row r="2"><x:c r="A2" t="str"><x:v>0012345678901</x:v></x:c></x:row>
              </x:sheetData></x:worksheet>""".trimIndent()
        val bytes = ByteArrayOutputStream().use { stream ->
            ZipOutputStream(stream).use { zip ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
            stream.toByteArray()
        }
        val output = ExcelWorkbook.read(bytes)
        assertEquals(listOf("شناسه"), output[0])
        assertEquals("0012345678901", output[1][0])
    }
}
