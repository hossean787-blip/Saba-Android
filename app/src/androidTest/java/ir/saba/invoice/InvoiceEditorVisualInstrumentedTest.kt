package ir.saba.invoice

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.saba.invoice.data.*
import ir.saba.invoice.ui.InvoiceCreditGateway
import ir.saba.invoice.ui.InvoiceScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Android screenshots of the professional invoice editor; all fixtures are synthetic. */
@RunWith(AndroidJUnit4::class)
class InvoiceEditorVisualInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val owner = "11111111-1111-4111-8111-111111111111"

    private val credits = object : InvoiceCreditGateway {
        override suspend fun paidBalance(token: String, companyId: Long) = 5L
        override suspend fun giftBalance(token: String) = 0
        override suspend fun consumePaid(token: String, companyId: Long, invoiceKey: String) =
            error("The visual test must not consume credit")
        override suspend fun consumeGift(token: String, invoiceKey: String): Int =
            error("The visual test must not consume credit")
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.getExternalFilesDir("screenshots")!!
        assertTrue(directory.mkdirs() || directory.isDirectory)
        val file = File(directory, "$name.png")
        file.outputStream().use { stream ->
            assertTrue(compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        assertTrue("Screenshot is not empty", file.length() > 2000L)
    }

    @Test fun professionalEditorShowsPopulatedLinesAndPersistentSaveOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, SabaDatabase::class.java).build()
        var companyId = 0L
        lateinit var customer: Customer
        lateinit var existing: List<Invoice>
        runBlocking {
            companyId = db.companyDao().insert(Company(
                purchaserId = owner, name = "شرکت نمونه سورا",
                nationalId = "12345678901", economicId = "12345678901"
            ))
            val customerId = db.customerDao().insert(Customer(
                companyId = companyId, name = "خریدار نمونه",
                nationalId = "1234567890", economicId = "12345678901234"
            ))
            customer = requireNotNull(db.customerDao().byId(customerId))
            QuotaRepository(db).initializeWallet(owner, 5)
            QuotaRepository(db).transferFromMaster(owner, companyId, 5, "visual-seed")
            InvoiceRepository(db).saveDraft(
                companyId, customerId, "1000000001", "1405/08/17",
                listOf(InvoiceLine(
                    invoiceId = 0, itemCode = "1234567890123",
                    description = "خدمات آزمایشی", quantity = 2,
                    unitPriceRial = 850000, discountRial = 100000,
                    taxRatePercent = 10, unitCode = "1627"
                ))
            )
            existing = db.invoiceDao().forCompany(companyId)
        }
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF086C7D), onPrimary = Color.White,
                background = Color(0xFFF4F9FC), surface = Color.White,
                onSurface = Color(0xFF132D43)
            )) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    InvoiceScreen(
                        db = db, ownerId = owner, companyId = companyId,
                        customers = listOf(customer), invoices = existing,
                        onChanged = {}, onNotice = {}, onEditorActive = {},
                        availableCharge = 5, creditGateway = credits
                    )
                }
            }
        }
        compose.onNodeWithText("مشاهده جزئیات").performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("ویرایش پیش‌نویس").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("ویرایش پیش‌نویس").performScrollTo().performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("invoiceProfessionalHeader").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("invoiceStickyActionDock").assertExists()
        screenshot("invoice-editor-header-emulator")
        compose.onNodeWithTag("invoiceItemRow0").performScrollTo()
        compose.onNodeWithText("1. خدمات آزمایشی").assertExists()
        screenshot("invoice-editor-lines-emulator")
        compose.onNodeWithText("ویرایش ردیف").performScrollTo().performClick()
        compose.onNodeWithText("قیمت واحد به ریال").assertExists()
        screenshot("invoice-editor-line-dialog-emulator")
    }
}
