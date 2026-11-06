package ir.saba.invoice.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.identifiers.SuraIdentifierInputService
import ir.saba.invoice.data.identifiers.SuraSavedIdentifierStore
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SuraIdentifierSavedImportTest {
    @Test fun batchImportIsIdempotentAndIsolatedPerTaxpayerAndAccount() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ownerA = AccountScope.of("7b6a9f44-96c6-4ad9-a423-1f735ab93110")
        val ownerB = AccountScope.of("28bc73da-85f2-4bd5-8213-7f3fc4365f64")
        val dbA = "sura_identifier_saved_" + ownerA.storageKey + ".db"
        val dbB = "sura_identifier_saved_" + ownerB.storageKey + ".db"
        context.deleteDatabase(dbA)
        context.deleteDatabase(dbB)
        val rows = (0 until 10).map { i ->
            SuraIdentifierInputService.item(
                (1_500_000_000_000L + i).toString(),
                "شناسه آزمایشی " + i, "", "کالا", "عمومی", "ورود از اکسل"
            )
        }
        try {
            val a = SuraSavedIdentifierStore(context, ownerA)
            val b = SuraSavedIdentifierStore(context, ownerB)
            try {
                assertEquals(10, a.importBatch(24L, rows))
                assertEquals(0, a.importBatch(24L, rows))
                assertEquals(10, a.forTaxpayer(24L).size)
                assertTrue(a.forTaxpayer(25L).isEmpty())
                assertTrue(b.forTaxpayer(24L).isEmpty())
                a.save(24L, rows.first().copy(description = "شرح اصلاح‌شده"))
                assertEquals(0, a.importBatch(24L, rows))
                assertEquals("شرح اصلاح‌شده",
                    a.forTaxpayer(24L).single { it.id == rows.first().id }.description)
            } finally {
                a.close()
                b.close()
            }
            val reopened = SuraSavedIdentifierStore(context, ownerA)
            try {
                assertEquals(10, reopened.forTaxpayer(24L).size)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(dbA)
            context.deleteDatabase(dbB)
        }
    }
}
