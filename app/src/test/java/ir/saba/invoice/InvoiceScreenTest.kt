package ir.saba.invoice

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.*
import ir.saba.invoice.ui.InvoiceScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InvoiceScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: SabaDatabase
    private var company = 0L
    private var customer = 0L
    private var invoices by mutableStateOf(emptyList<Invoice>())

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SabaDatabase::class.java).build()
        company = db.companyDao().insert(Company(purchaserId = "test", name = "شرکت", nationalId = "1"))
        customer = db.customerDao().insert(Customer(companyId = company, name = "خریدار"))
    }
    @After fun close() { db.close() }
    @Composable private fun Screen() {
        MaterialTheme {
            InvoiceScreen(db, company, listOf(Customer(id = customer, companyId = company, name = "خریدار")), invoices,
                onChanged = { invoices = db.invoiceDao().forCompany(company) }, onNotice = {}, onEditorActive = {})
        }
    }
    private fun openNew() {
        compose.onNodeWithText("صدور فاکتور جدید").performClick()
        compose.onNodeWithText("شماره فاکتور").performTextInput("۱۲")
        compose.onNodeWithText("تاریخ شمسی، نمونه: ۱۴۰۵/۰۷/۰۲").performTextInput("۱۴۰۵/۰۷/۰۲")
    }
    private fun addLine() {
        compose.onNodeWithText("افزودن کالا یا خدمت").performScrollTo().performClick()
        compose.onNodeWithText("شرح کالا یا خدمت").performTextInput("خدمت آزمایشی")
        compose.onNodeWithText("قیمت واحد به ریال").performScrollTo().performTextInput("۱۰۰۰")
        compose.onNodeWithText("ثبت ردیف").performClick()
    }

    @Test fun createsDraftFromPersianInputAndOpensDetails() {
        compose.setContent { Screen() }
        openNew(); addLine()
        compose.onNodeWithText("ذخیره پیش‌نویس").performScrollTo().performClick()
        compose.waitUntil(15000) { invoices.size == 1 }
        compose.waitUntil(15000) { compose.onAllNodesWithText("ویرایش پیش‌نویس").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("فاکتور 12").assertExists()
        runBlocking {
            check(db.invoiceLineDao().forInvoice(invoices.single().id).single().description == "خدمت آزمایشی")
            check(invoices.single().status == "DRAFT")
        }
    }

    @Test fun restoresUnsavedHeaderAndLinesAfterRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Screen() }
        openNew(); addLine()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("شماره فاکتور").assertTextContains("۱۲")
        compose.onNodeWithText("1. خدمت آزمایشی").assertExists()
    }

    @Test fun cancellationRequiresConfirmationAndDoesNotCreateDraft() {
        compose.setContent { Screen() }
        openNew()
        compose.onNodeWithText("انصراف از ویرایش").performScrollTo().performClick()
        compose.onNodeWithText("خروج از ویرایش؟").assertExists()
        compose.onNodeWithText("تأیید").performClick()
        compose.onNodeWithText("صدور فاکتور جدید").assertExists()
        runBlocking { check(db.invoiceDao().forCompany(company).isEmpty()) }
    }

    @Test fun finalizedInvoiceHasDetailsWithoutEditOrDeleteActions() {
        runBlocking {
            val id = InvoiceRepository(db).saveDraft(company, customer, "FINAL", "1405/07/02", listOf(InvoiceLine(invoiceId = 0, description = "کالا", quantity = 1, unitPriceRial = 100, taxRatePercent = 10)))
            val quota = QuotaRepository(db)
            quota.initializeWallet("test", 1); quota.transferFromMaster("test", company, 1, "q")
            InvoiceRepository(db).finalizeDraft(company, id)
            invoices = db.invoiceDao().forCompany(company)
        }
        compose.setContent { Screen() }
        compose.onNodeWithText("مشاهده جزئیات").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("سند نهایی‌شده قابل ویرایش یا حذف نیست.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ویرایش پیش‌نویس").assertDoesNotExist()
        compose.onNodeWithText("حذف پیش‌نویس").assertDoesNotExist()
    }
}
