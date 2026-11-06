package ir.saba.invoice.data

import androidx.test.core.app.ApplicationProvider
import androidx.room.withTransaction
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class TaxpayerIdAllocatorTest {
    @Test fun reservesAfterLegacyIdsAndSerializesConcurrentAccounts()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase("sura_local_taxpayer_ids.db")
        context.openOrCreateDatabase("saba_local.db",0,null).use {
            it.execSQL("CREATE TABLE IF NOT EXISTS companies(id INTEGER PRIMARY KEY)")
            it.execSQL("INSERT INTO companies(id) VALUES(700)")
        }
        try {
            assertEquals(701L,TaxpayerIdAllocator(context).use{it.reserve()})
            val ids=(1..8).map{async(Dispatchers.IO){TaxpayerIdAllocator(context).use{it.reserve()}}}.awaitAll()
            assertEquals(8,ids.toSet().size)
            assertEquals((702L..709L).toSet(),ids.toSet())
        } finally { context.deleteDatabase("sura_local_taxpayer_ids.db");context.deleteDatabase("saba_local.db") }
    }

    @Test fun newAccountCanPersistTaxpayerAfterGlobalIdReservation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val account = AccountScope.of("33333333-3333-4333-8333-333333333333")
        context.deleteDatabase(account.databaseName)
        val db = SabaDatabase.openAccount(context, account)
        try {
            assertTrue(db.companyDao().all().isEmpty())
            // Initializes the global allocator while the account database is open,
            // but not inside the account database's exclusive write transaction.
            val reservedId = withContext(Dispatchers.IO) {
                TaxpayerIdAllocator(context).use { it.reserve() }
            }
            withTimeout(8000L) {
                db.withTransaction {
                    db.companyDao().insert(Company(
                        id = reservedId, purchaserId = account.ownerId,
                        name = "مودی آزمون", nationalId = "12345678901234",
                        economicId = "12345678901234"
                    ))
                    db.moadianDetailsDao().upsert(MoadianDetails(companyId = reservedId, taxpayerType = "NATURAL"))
                }
            }
            assertEquals(reservedId, db.companyDao().all().single().id)
            assertEquals("NATURAL", db.moadianDetailsDao().byCompany(reservedId)?.taxpayerType)
        } finally {
            db.close()
            context.deleteDatabase(account.databaseName)
        }
    }
}
