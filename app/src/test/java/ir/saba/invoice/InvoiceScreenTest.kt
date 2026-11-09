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
import ir.saba.invoice.ui.InvoiceCreditGateway
import ir.saba.invoice.data.identifiers.SuraIdentifier
import ir.saba.invoice.data.identifiers.SuraSavedIdentifierStore
import ir.saba.invoice.security.AccountScope
import org.junit.Assert.assertEquals
import kotlinx.coroutines.runBlocking
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
    private val creditGateway = object : InvoiceCreditGateway {
        var remaining = 10L
        val consumedKeys = mutableSetOf<String>()
        val requestedKeys = mutableListOf<String>()
        var failAfterDebit = false
        override suspend fun paidBalance(token: String, companyId: Long) = remaining
        override suspend fun giftBalance(token: String) = 0
        override suspend fun consumePaid(token: String, companyId: Long, invoiceKey: String): Long {
            requestedKeys += invoiceKey
            if (consumedKeys.add(invoiceKey)) remaining--
            if (failAfterDebit) {
                failAfterDebit = false
                throw java.io.IOException("تأیید مصرف دریافت نشد")
            }
            return remaining
        }
        override suspend fun consumeGift(token: String, invoiceKey: String): Int = error("No gift credit in this fixture")
    }
    private val ownerId = "11111111-1111-4111-8111-111111111111"
    private var company = 0L
    private var customer = 0L
    private var invoices by mutableStateOf(emptyList<Invoice>())
    private val surfacedNotices = mutableListOf<String>()

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SabaDatabase::class.java).build()
        company = db.companyDao().insert(Company(purchaserId = ownerId, name = "شرکت", nationalId = "1"))
        customer = db.customerDao().insert(Customer(companyId = company, name = "خریدار"))
        QuotaRepository(db).initializeWallet(ownerId, 10)
        QuotaRepository(db).transferFromMaster(ownerId, company, 10, "seed")
    }
    // The Compose test rule disposes its composition after @After hooks.
    // Closing this in-memory DB in @After races with still-running LaunchedEffects.
    // Its lifetime is limited to this fixture; no persistent file is created.
    @Composable private fun Screen() {
        MaterialTheme {
            InvoiceScreen(db, ownerId, company, listOf(Customer(id = customer, companyId = company, name = "خریدار")), invoices,
                onChanged = { invoices = db.invoiceDao().forCompany(company) }, onNotice = { surfacedNotices.add(it) }, onEditorActive = {}, availableCharge = 10, creditGateway = creditGateway)
        }
    }
    private fun openNew() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("اعتبار مودی: 10 | اعتبار هدیه: 0").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("صدور فاکتور جدید").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithText("شماره فاکتور").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("شماره فاکتور").performTextInput("۱۲")
        compose.onNodeWithText("تاریخ شمسی، نمونه: ۱۴۰۵/۰۷/۰۲").performTextInput("۱۴۰۵/۰۷/۰۲")
    }
    private fun addLine() {
        compose.onNodeWithText("افزودن کالا یا خدمت").performScrollTo().performClick()
        compose.onNodeWithText("شرح کالا یا خدمت").performTextInput("خدمت آزمایشی")
        compose.onNodeWithText("قیمت واحد به ریال").performScrollTo().performTextInput("۱۰۰۰")
        compose.onNodeWithText("درصد مالیات (تا ۲ رقم اعشار)").performScrollTo().performTextInput("۱۰")
        compose.onNodeWithText("ثبت ردیف").performScrollTo().performClick()
        compose.waitForIdle()
        val row = compose.onAllNodesWithTag("invoiceItemRow0", useUnmergedTree = true).fetchSemanticsNodes()
        if (row.isEmpty()) {
            throw AssertionError("ردیف پس از ثبت اضافه نشد؛ درخت رابط:\n" +
                compose.onRoot(useUnmergedTree = true).printToString().take(13000))
        }
    }

    @Test fun professionalInvoiceEditorKeepsSectionsAndSaveVisible() {
        compose.setContent { Screen() }
        openNew()
        compose.onNodeWithTag("invoiceProfessionalHeader").assertExists()
        compose.onNodeWithTag("invoiceWorkflowProgress").assertExists()
        compose.onNodeWithTag("invoiceSupportedTaxFormat").assertExists()
        compose.onNodeWithTag("invoiceQuickPreview").assertIsNotEnabled()
        compose.onNodeWithTag("invoiceSection۱").assertExists()
        compose.onNodeWithTag("invoiceSection۲").assertExists()
        compose.onNodeWithTag("invoiceSection۳").assertExists()
        compose.onNodeWithTag("invoiceSection۴").assertExists()
        compose.onNodeWithTag("invoiceStickyActionDock").assertExists()
        compose.onNodeWithText("ذخیره پیش‌نویس").assertIsNotEnabled()
        addLine()
        compose.onNodeWithTag("invoiceItemRow0").assertExists()
        compose.onNodeWithTag("invoiceQuickRowActions").assertExists()
        compose.onNodeWithTag("invoiceAccessibleTotals").assertExists()
        compose.onNodeWithTag("invoiceQuickPreview").assertIsEnabled()
        compose.onNodeWithText("ذخیره پیش‌نویس").assertIsEnabled()
        compose.onNodeWithTag("invoiceStickyActionDock").assertExists()
    }

    @Test fun quickPreviewShowsAccountingTotalsWithoutSavingOrConsumingCredit() {
        compose.setContent { Screen() }
        openNew()
        addLine()
        compose.onNodeWithTag("invoiceQuickPreview").performClick()
        compose.onNodeWithText("پیش‌نمایش محلی صورتحساب").assertExists()
        check(compose.onAllNodesWithText("مبلغ قابل پرداخت", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty())
        assertEquals(10L, creditGateway.remaining)
        assertEquals(0, creditGateway.requestedKeys.size)
        runBlocking { assertEquals(0, db.invoiceDao().forCompany(company).size) }
        compose.onNodeWithText("بازگشت به فاکتور").performClick()
        compose.onNodeWithText("شماره فاکتور").assertExists()
    }

    @Test fun repositoryDraftEditPreservesSupplementaryPaymentDetails() = runBlocking {
        val original = InvoiceLocalDetails(
            paymentMethod = "کارتخوان", paymentTrackingNumber = "۱۲۳", notes = "یادداشت پیشین"
        ).toJson()
        val repo = InvoiceRepository(db)
        val line = InvoiceLine(invoiceId = 0, description = "خدمت",
            quantity = 1, unitPriceRial = 1000, taxRatePercent = 10)
        val firstId = repo.saveDraft(company, customer, "150", "1405/08/18",
            listOf(line), localDetailsJson = original)
        val loaded = repo.detailsForCompany(company, firstId)
        val updatedId = repo.saveDraft(
            companyId = company, customerId = customer,
            invoiceNumber = loaded.invoice.invoiceNumber,
            issueDate = loaded.invoice.issueDate, lines = loaded.lines,
            existingInvoiceId = firstId,
            localDetailsJson = loaded.invoice.localDetailsJson
        )
        assertEquals(firstId, updatedId)
        val persisted = repo.detailsForCompany(company, updatedId)
        val metadata = InvoiceLocalDetails.fromJson(persisted.invoice.localDetailsJson)
        assertEquals("یادداشت پیشین", metadata.notes)
        assertEquals("کارتخوان", metadata.paymentMethod)
        assertEquals("۱۲۳", metadata.paymentTrackingNumber)
        assertEquals(1, db.invoiceDao().forCompany(company).size)
        assertEquals(10L, creditGateway.remaining)
    }

    @Test fun createsDraftFromPersianInputAndOpensDetails() {
        compose.setContent { Screen() }
        openNew(); addLine()
        compose.onNodeWithText("ذخیره پیش‌نویس").performClick()
        compose.waitUntil(15000) { invoices.size == 1 }
        compose.waitUntil(15000) { compose.onAllNodesWithText("ویرایش پیش‌نویس").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("فاکتور 12").assertExists()
        assertEquals(9L, creditGateway.remaining)
        assertEquals(1, creditGateway.consumedKeys.size)
        runBlocking {
            check(db.invoiceLineDao().forInvoice(invoices.single().id).single().description == "خدمت آزمایشی")
            check(invoices.single().status == "DRAFT")
        }
    }

    @Test fun switchingAccountDiscardsUnsavedInvoiceFields() {
        var activeOwner by mutableStateOf(ownerId)
        compose.setContent { MaterialTheme {
            InvoiceScreen(db,activeOwner,company,listOf(Customer(id=customer,companyId=company,name="خریدار")),invoices,
                onChanged={},onNotice={},onEditorActive={},creditGateway=creditGateway)
        } }
        openNew();addLine()
        compose.runOnIdle { activeOwner="22222222-2222-4222-8222-222222222222" }
        compose.onNodeWithText("شماره فاکتور").assertDoesNotExist()
        compose.onNodeWithText("1. خدمت آزمایشی").assertDoesNotExist()
        compose.onNodeWithText("صدور فاکتور جدید").assertExists()
    }

    @Test fun zeroCreditAllowsInvoiceScaffoldButCannotCreateOrConsume() {
        creditGateway.remaining = 0
        compose.setContent { Screen() }
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("اعتبار مودی: 0 | اعتبار هدیه: 0").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("صدور فاکتور جدید").performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("شماره فاکتور").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("خلاصه زنده صورتحساب").assertExists()
        compose.onNodeWithText("شماره فاکتور").performTextInput("۱۲")
        compose.onNodeWithText("تاریخ شمسی، نمونه: ۱۴۰۵/۰۷/۰۲").performTextInput("۱۴۰۵/۰۷/۰۲")
        addLine()
        // Totals are intentionally visible both above the wide grid and in the
        // final outline so accountants don't have to swipe to find the sum.
        check(compose.onAllNodesWithText("مبلغ قابل پرداخت", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("ذخیره پیش‌نویس").performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("اعتبار کافی یا اتصال معتبر به سرور موجود نیست؛ فرم فعلی محفوظ است.").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, creditGateway.requestedKeys.size)
        runBlocking { check(db.invoiceDao().forCompany(company).isEmpty()) }
        compose.onNodeWithText("خلاصه زنده صورتحساب").assertExists()
    }

    @Test fun retryAfterAmbiguousDebitPreservesCreditKey() {
        creditGateway.failAfterDebit = true
        compose.setContent { Screen() }
        openNew(); addLine()
        compose.onNodeWithText("ذخیره پیش‌نویس").performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("تأیید مصرف دریافت نشد").fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking { check(db.invoiceDao().forCompany(company).isEmpty()) }
        compose.onNodeWithText("ذخیره پیش‌نویس").performClick()
        compose.waitUntil(15000) { invoices.size == 1 }
        assertEquals(2, creditGateway.requestedKeys.size)
        assertEquals(1, creditGateway.requestedKeys.toSet().size)
        assertEquals(9L, creditGateway.remaining)
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
        compose.onNodeWithText("انصراف از ویرایش").performClick()
        compose.onNodeWithText("خروج از ویرایش؟").assertExists()
        compose.onNodeWithText("تأیید").performClick()
        compose.onNodeWithText("صدور فاکتور جدید").assertExists()
        runBlocking { check(db.invoiceDao().forCompany(company).isEmpty()) }
    }

    @Test fun finalizedInvoiceHasDetailsWithoutEditOrDeleteActions() {
        runBlocking {
            val id = InvoiceRepository(db).saveDraft(company, customer, "FINAL", "1405/07/02", listOf(InvoiceLine(invoiceId = 0, description = "کالا", quantity = 1, unitPriceRial = 100, taxRatePercent = 10)))
            InvoiceRepository(db).finalizeDraft(company, id)
            invoices = db.invoiceDao().forCompany(company)
        }
        compose.setContent { Screen() }
        compose.onNodeWithText("مشاهده جزئیات").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("سند نهایی‌شده قابل ویرایش یا حذف نیست.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ویرایش پیش‌نویس").assertDoesNotExist()
        compose.onNodeWithText("حذف پیش‌نویس").assertDoesNotExist()
    }
    @Test fun invoiceFormOpensWithoutRegisteredCustomerToAllowScaffolding() {
        compose.setContent { MaterialTheme {
            InvoiceScreen(db,ownerId,company,emptyList(),invoices,
                onChanged={},onNotice={},onEditorActive={},creditGateway=creditGateway)
        } }
        compose.onNodeWithText("صدور فاکتور جدید").performClick()
        // Opening the editor reads the pending-credit claim from Room on a
        // coroutine; wait for the actual rendered state rather than racing it.
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("تنظیم صورتحساب جدید").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("مشتری: انتخاب کنید").assertExists()
        compose.onNodeWithText("افزودن کالا یا خدمت").assertExists()
        assertEquals(10L,creditGateway.remaining)
    }


    @Test fun invoiceItemSelectionUsesOnlyCurrentTaxpayersSavedIdentifier() {
        val code = "1234567890123"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val saved = SuraSavedIdentifierStore(context, AccountScope.of(ownerId))
        try {
            runBlocking {
                saved.save(company, SuraIdentifier(
                    id = code,
                    description = "کالای انتخاب‌شده مؤدی",
                    vat = "9",
                    taxable = "",
                    kind = "کالا",
                    expirationDate = "",
                    source = "ورود از اکسل"
                ))
            }
        } finally {
            saved.close()
        }

        compose.setContent { Screen() }
        openNew()
        compose.onNodeWithText("افزودن کالا یا خدمت").performScrollTo().performClick()
        compose.onNodeWithTag("invoiceSavedIdentifierButton").performScrollTo().performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("invoiceSelectIdentifier_" + code).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("invoiceSelectIdentifier_" + code).performScrollTo().performClick()
        compose.onNodeWithText("شناسه کالا و خدمت").assertTextContains(code)
        compose.onNodeWithText("قیمت واحد به ریال").performScrollTo().performTextInput("1000")
        compose.onNodeWithText("ثبت ردیف").performScrollTo().performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("invoiceItemRow0").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("ذخیره پیش‌نویس").performClick()
        compose.waitUntil(15000) { invoices.size == 1 || surfacedNotices.isNotEmpty() }
        assertEquals("Invoice save failed; UI notices: ${surfacedNotices.joinToString(" | ")}", 1, invoices.size)
        runBlocking {
            val row = db.invoiceLineDao().forInvoice(invoices.single().id).single()
            assertEquals(code, row.itemCode)
            assertEquals("کالای انتخاب‌شده مؤدی", row.description)
            assertEquals(9, row.taxRatePercent)
        }
    }


    @Test fun finalizedInvoiceCanNavigateToOfficialSubmissionOnlyAfterLocalFinalization() {
        runBlocking {
            val id = InvoiceRepository(db).saveDraft(
                company, customer, "15", "1405/07/02",
                listOf(InvoiceLine(
                    invoiceId = 0, itemCode = "1234567890123",
                    description = "کالا", quantity = 1,
                    unitPriceRial = 1000, taxRatePercent = 10
                ))
            )
            InvoiceRepository(db).finalizeDraft(company, id)
            invoices = db.invoiceDao().forCompany(company)
        }
        var navigations = 0
        compose.setContent { MaterialTheme {
            InvoiceScreen(db, ownerId, company,
                listOf(Customer(id = customer, companyId = company, name = "خریدار")),
                invoices, onChanged = {}, onNotice = {}, onEditorActive = {},
                creditGateway = creditGateway,
                onOpenOfficialSubmission = { navigations++ })
        } }
        compose.onNodeWithText("مشاهده جزئیات").performScrollTo().performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("آماده‌سازی و ارسال رسمی به سامانه مؤدیان")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("آماده‌سازی و ارسال رسمی به سامانه مؤدیان")
            .performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, navigations) }
    }


    @Test fun invoiceEditorSavesMixedCashShareAndShowsItOnDetails() {
        compose.setContent { Screen() }
        openNew()
        addLine()
        compose.onNodeWithText("ترکیبی").performScrollTo().performClick()
        compose.onNodeWithText("سهم نقدی از مبلغ کل (ریال)")
            .performScrollTo().performTextInput("۴۰۰")
        compose.onNodeWithText("ذخیره پیش‌نویس").performClick()
        compose.waitUntil(15000) { invoices.size == 1 }
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("تسویه: نقدی و نسیه").fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking {
            val stored = db.invoiceDao().byId(invoices.single().id)!!
            assertEquals("MIXED", stored.settlementMode)
            assertEquals(400L, stored.cashPaidRial)
            assertEquals(1100L, stored.totalAmountRial)
        }
    }

    @Test fun correctiveInvoiceIsDisabledWithoutAnAcceptedOriginalReference() {
        compose.setContent { Screen() }
        openNew()
        compose.onNodeWithText("اصلاحی (ثبت محلی)").performScrollTo()
            .assertExists().assertIsNotEnabled()
        compose.onNodeWithText("اصلی").assertExists()
    }


    @Test fun copyLineOpensEditableDuplicateAndPersistsTwoRowsWithOneCreditDebit() {
        compose.setContent { Screen() }
        openNew()
        addLine()
        compose.onNodeWithTag("invoiceSection۳").performScrollTo()
        compose.onNodeWithText("کپی ردیف").performScrollTo().performClick()
        compose.onNodeWithText("شرح کالا یا خدمت")
            .performScrollTo().assertTextContains("خدمت آزمایشی")
        compose.onNodeWithText("ثبت ردیف").performScrollTo().performClick()
        compose.onNodeWithText("2. خدمت آزمایشی").assertExists()
        compose.onNodeWithText("ذخیره پیش‌نویس").performClick()
        compose.waitUntil(15000) { invoices.size == 1 }
        runBlocking {
            val rows = db.invoiceLineDao().forInvoice(invoices.single().id)
            assertEquals(2, rows.size)
            assertEquals(rows[0].itemCode, rows[1].itemCode)
            assertEquals(rows[0].description, rows[1].description)
            assertEquals(2200L, invoices.single().totalAmountRial)
        }
        assertEquals(1, creditGateway.consumedKeys.size)
    }

    @Test fun copyAcceptedReferenceItemsIntoCorrectionDoesNotCreateInvoiceOrDebitCredit() {
        val referenceTaxId = "A11216" + "0".repeat(16)
        runBlocking {
            val id = InvoiceRepository(db).saveDraft(
                company, customer, "11", "1405/07/02",
                listOf(InvoiceLine(
                    invoiceId = 0, itemCode = "1234567890123",
                    description = "کالای مرجع", quantity = 2,
                    unitPriceRial = 1000, taxRatePercent = 10, unitCode = "1627"
                ))
            )
            InvoiceRepository(db).finalizeDraft(company, id)
            db.officialSubmissionDao().insert(OfficialSubmission(
                requestId = java.util.UUID.randomUUID().toString(),
                ownerId = ownerId, companyId = company, invoiceId = id,
                fiscalId = "A11216", taxId = referenceTaxId,
                payload = "{}", payloadHash = "fixture", preparedAt = 100,
                state = "ACCEPTED", referenceNumber = "accepted",
                signedReceipt = "verified-receipt-fixture"
            ))
        }
        compose.setContent { Screen() }
        openNew()
        compose.onNodeWithText("اصلاحی (ثبت محلی)").performScrollTo()
            .assertIsEnabled().performClick()
        compose.onNodeWithText("شناسه مالیاتی مرجع: انتخاب از صورتحساب‌های پذیرفته‌شده")
            .performScrollTo().performClick()
        compose.onNodeWithText(referenceTaxId).performClick()
        compose.onNodeWithText("کپی اقلام صورتحساب مرجع برای اصلاح")
            .performScrollTo().performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("1. کالای مرجع").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, creditGateway.consumedKeys.size)
        runBlocking {
            assertEquals(1, db.invoiceDao().forCompany(company).size)
        }
    }


}
