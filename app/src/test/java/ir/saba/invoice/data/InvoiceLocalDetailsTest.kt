package ir.saba.invoice.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InvoiceLocalDetailsTest {
    @Test fun supplementaryFieldsRoundtripWithoutChangingTaxMeaning() {
        val local = InvoiceLocalDetails(
            article17Subject = "مشمول بررسی",
            paymentSwitchNumber = "۰۰۱۲۳",
            paymentMethod = "کارت به کارت",
            paymentDate = "۱۴۰۵/۰۸/۱۸",
            paymentTrackingNumber = "۰۰۹۹",
            sellerBranchCode = "۱۰",
            buyerBranchCode = "۲۰",
            notes = "شرح پرداخت"
        )
        assertEquals(local, InvoiceLocalDetails.fromJson(local.toJson()))
        assertEquals(InvoiceLocalDetails(), InvoiceLocalDetails.fromJson(""))
        try {
            InvoiceLocalDetails(notes = "x".repeat(2001)).toJson()
            fail("Oversized notes must be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun itemTitleAndPaymentFieldsSurviveRoomCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "invoice-local-details-${System.nanoTime()}.db"
        val details = InvoiceLocalDetails(paymentMethod = "واریز بانکی", notes = "شماره رسید").toJson()
        val first = Room.databaseBuilder(context, SabaDatabase::class.java, name).build()
        var companyId = 0L
        var invoiceId = 0L
        try {
            companyId = first.companyDao().insert(
                Company(purchaserId = "owner", name = "فروشنده", nationalId = "123"))
            val buyer = first.customerDao().insert(Customer(companyId = companyId, name = "مشتری"))
            invoiceId = first.invoiceDao().insert(Invoice(
                companyId = companyId, customerId = buyer, invoiceNumber = "001",
                issueDate = "1405/08/18", totalAmountRial = 110L, taxAmountRial = 10L,
                localDetailsJson = details
            ))
            first.invoiceLineDao().insertAll(listOf(InvoiceLine(
                invoiceId = invoiceId, itemCode = "1234567890123",
                itemTitle = "کالا", description = "شرح فنی کالا",
                quantity = 1L, unitPriceRial = 100L, taxRatePercent = 10
            )))
        } finally { first.close() }
        val reopened = Room.databaseBuilder(context, SabaDatabase::class.java, name).build()
        try {
            val invoice = requireNotNull(reopened.invoiceDao().byId(invoiceId))
            assertEquals("واریز بانکی", InvoiceLocalDetails.fromJson(invoice.localDetailsJson).paymentMethod)
            assertEquals("شماره رسید", InvoiceLocalDetails.fromJson(invoice.localDetailsJson).notes)
            val line = reopened.invoiceLineDao().forInvoice(invoiceId).single()
            assertEquals("کالا", line.itemTitle)
            assertEquals("شرح فنی کالا", line.description)
            assertEquals(companyId, invoice.companyId)
            reopened.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { reopened.close(); context.deleteDatabase(name) }
    }

    @Test fun versionThirteenUpgradeRetainsPriorInvoiceValuesAndAddsEmptyMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "invoice-v13-upgrade-${System.nanoTime()}.db"
        context.getDatabasePath(name).parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { raw ->
            raw.execSQL("CREATE TABLE invoices (id INTEGER PRIMARY KEY, invoiceNumber TEXT NOT NULL, totalAmountRial INTEGER NOT NULL)")
            raw.execSQL("CREATE TABLE invoice_lines (id INTEGER PRIMARY KEY, invoiceId INTEGER NOT NULL, description TEXT NOT NULL)")
            raw.execSQL("INSERT INTO invoices VALUES (7, '00012', 7700000)")
            raw.execSQL("INSERT INTO invoice_lines VALUES (9, 7, 'legacy')")
            raw.version = 13
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        assertEquals(13, oldVersion)
                        assertEquals(14, newVersion)
                        SabaDatabase.MIGRATION_13_14.migrate(db)
                    }
                }).build()
        )
        try {
            helper.writableDatabase.query("SELECT invoiceNumber,totalAmountRial,localDetailsJson FROM invoices WHERE id=7").use {
                assertTrue(it.moveToFirst())
                assertEquals("00012", it.getString(0))
                assertEquals(7700000L, it.getLong(1))
                assertEquals("", it.getString(2))
            }
            helper.writableDatabase.query("SELECT description,itemTitle FROM invoice_lines WHERE id=9").use {
                assertTrue(it.moveToFirst())
                assertEquals("legacy", it.getString(0))
                assertEquals("", it.getString(1))
            }
        } finally { helper.close(); context.deleteDatabase(name) }
    }
}
