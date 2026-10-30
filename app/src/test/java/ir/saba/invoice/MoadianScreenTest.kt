package ir.saba.invoice

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.*
import ir.saba.invoice.ui.MoadianScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MoadianScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: SabaDatabase
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SabaDatabase::class.java).build()
    }
    @After fun close() { db.close() }

    @Test fun selectingThenCreatingDoesNotCopyExistingTaxpayer() {
        val id = runBlocking { db.companyDao().insert(Company(purchaserId = "owner", name = "مودی اول", nationalId = "12345678901")) }
        val rows = runBlocking { db.companyDao().all() }
        compose.setContent { MaterialTheme {
            MoadianScreen(db, rows, {}, {}, {}, {})
        } }
        compose.onNodeWithText("مودی اول").performClick()
        compose.onNodeWithText("جدید").performClick()
        compose.onNodeWithText("مودی اول").assertDoesNotExist()
    }

    @Test fun allFourTaxpayerTypesRenderInOneVisualRow() {
        val rows = runBlocking { db.companyDao().all() }
        compose.setContent { MaterialTheme {
            MoadianScreen(db, rows, {}, {}, {}, {})
        } }
        compose.onNodeWithText("جدید").performClick()
        val kinds = listOf("شخص حقیقی", "شخص حقوقی", "مشارکت مدنی", "اتباع غیرایرانی")
        val y = kinds.map { label ->
            compose.onNodeWithText(label).assertExists()
                .fetchSemanticsNode().boundsInRoot.center.y
        }
        assertTrue("Taxpayer kinds must share a single row", y.maxOrNull()!! - y.minOrNull()!! < 4f)
        compose.onNodeWithContentDescription("راهنمای شماره اقتصادی *").assertExists()
    }

    @Test fun archiveRequiresConfirmationAndKeepsCompany() {
        val id = runBlocking { db.companyDao().insert(Company(purchaserId = "owner", name = "مودی اول", nationalId = "12345678901")) }
        val rows = runBlocking { db.companyDao().all() }
        val changes = AtomicInteger()
        compose.setContent { MaterialTheme {
            MoadianScreen(db, rows, { changes.incrementAndGet(); Unit }, {}, {}, {})
        } }
        compose.onNodeWithText("مودی اول").performClick()
        compose.onNodeWithText("حذف").performClick()
        runBlocking { assertNull(db.moadianDetailsDao().byCompany(id)) }
        compose.onNodeWithText("تأیید غیرفعال‌سازی").performClick()
        compose.waitUntil(15000) { changes.get() == 1 }
        runBlocking {
            assertNotNull(db.companyDao().byId(id))
            assertTrue(db.moadianDetailsDao().byCompany(id)!!.inactive)
        }
    }
}
