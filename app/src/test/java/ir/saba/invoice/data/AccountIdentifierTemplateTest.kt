package ir.saba.invoice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.identifiers.SuraIdentifierInputService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccountIdentifierTemplateTest {
    @Test fun accountExcelExporterReturnsSameTenRowTemplateAsIdentifierImporter() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SabaDatabase::class.java).build()
        try {
            val exporter = ExcelTransferService(
                db, "11111111-1111-4111-8111-111111111111"
            ) { 1L }
            val rows = ExcelWorkbook.read(exporter.template("identifiers"))
            assertEquals(11, rows.size)
            assertEquals(SuraIdentifierInputService.headers, rows.first())
            assertTrue(rows.drop(1).all { row -> row.all(String::isBlank) })
        } finally {
            db.close()
        }
    }
}
