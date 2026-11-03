package ir.saba.invoice.data

import androidx.test.core.app.ApplicationProvider
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
}
