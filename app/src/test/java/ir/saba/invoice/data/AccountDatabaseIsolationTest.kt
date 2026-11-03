package ir.saba.invoice.data

import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AccountDatabaseIsolationTest {
    @Test fun equalLocalIdsAndNationalIdsRemainSeparateAcrossAccounts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val a = AccountScope.of("11111111-1111-4111-8111-111111111111")
        val b = AccountScope.of("22222222-2222-4222-8222-222222222222")
        val first = SabaDatabase.openAccount(context,a)
        val second = SabaDatabase.openAccount(context,b)
        try {
            val idA = first.companyDao().insert(Company(id=41, purchaserId=a.ownerId,name="اول",nationalId="12345678901"))
            assertTrue(second.companyDao().all().isEmpty())
            val idB = second.companyDao().insert(Company(id=41, purchaserId=b.ownerId,name="دوم",nationalId="12345678901"))
            assertEquals(idA,idB)
            first.openHelper.writableDatabase.query("PRAGMA synchronous").use { assertTrue(it.moveToFirst());assertEquals(2,it.getInt(0)) }
            first.openHelper.writableDatabase.query("PRAGMA journal_mode").use { assertTrue(it.moveToFirst());assertEquals("truncate",it.getString(0)) }
            assertEquals("اول",first.companyDao().byId(idA)!!.name)
            assertEquals("دوم",second.companyDao().byId(idB)!!.name)
            try {first.companyDao().insert(Company(id=99,purchaserId=b.ownerId,name="بیگانه",nationalId="foreign"));fail("Expected owner isolation trigger")}catch(_:RuntimeException){}
            try {first.companyDao().update(first.companyDao().byId(idA)!!.copy(purchaserId=b.ownerId));fail("Expected immutable owner trigger")}catch(_:RuntimeException){}
            try {first.companyDao().insert(Company(purchaserId=a.ownerId,name="شناسه خودکار",nationalId="auto"));fail("Expected global ID allocation requirement")}catch(_:RuntimeException){}
        } finally { first.close(); second.close(); context.deleteDatabase(a.databaseName); context.deleteDatabase(b.databaseName) }
    }
}
