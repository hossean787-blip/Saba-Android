package ir.saba.invoice

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.*
import ir.saba.invoice.ui.CompaniesScreen
import ir.saba.invoice.ui.CustomersScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PartyScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: SabaDatabase
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SabaDatabase::class.java).build()
    }
    @After fun close() { db.close() }

    @Test fun retryAfterCustomerRefreshFailureUpdatesTheInsertedCustomer() {
        val company = runBlocking { db.companyDao().insert(Company(purchaserId = "owner", name = "شرکت", nationalId = "1")) }
        val refreshes = AtomicInteger()
        compose.setContent { MaterialTheme {
            CustomersScreen(db, company, emptyList(), onChanged = { refreshes.incrementAndGet(); throw IOException("بازخوانی ناموفق") }, onNotice = {}, onEditing = {})
        } }
        compose.onNodeWithText("افزودن مشتری").performClick()
        compose.onNodeWithText("نام").performTextInput("خریدار")
        compose.onNodeWithText("ذخیره اطلاعات").performScrollTo().performClick()
        compose.waitUntil(15000) { refreshes.get() == 1 }
        compose.onNodeWithText("نام").performTextReplacement("نام اصلاح‌شده")
        compose.onNodeWithText("ذخیره اطلاعات").performScrollTo().performClick()
        compose.waitUntil(15000) { refreshes.get() == 2 }
        runBlocking {
            val rows = db.customerDao().forCompany(company)
            assertEquals(1, rows.size)
            assertEquals("نام اصلاح‌شده", rows.single().name)
        }
    }

    @Test fun retryAfterCompanyRefreshFailureKeepsTheInsertedCompanyIdentity() {
        val refreshes = AtomicInteger()
        compose.setContent { MaterialTheme {
            CompaniesScreen(db, emptyList(), onChanged = { refreshes.incrementAndGet(); throw IOException("بازخوانی ناموفق") }, onSelect = {}, onNotice = {}, onEditing = {})
        } }
        compose.onNodeWithText("افزودن شرکت / مؤدی").performClick()
        compose.onNodeWithText("نام").performTextInput("شرکت")
        compose.onNodeWithText("شناسه ملی").performTextInput("12345678901")
        compose.onNodeWithText("ذخیره اطلاعات").performScrollTo().performClick()
        compose.waitUntil(15000) { refreshes.get() == 1 }
        compose.onNodeWithText("نام").performTextReplacement("شرکت اصلاح‌شده")
        compose.onNodeWithText("ذخیره اطلاعات").performScrollTo().performClick()
        compose.waitUntil(15000) { refreshes.get() == 2 }
        runBlocking { assertEquals("شرکت اصلاح‌شده", db.companyDao().all().single().name) }
    }
}
