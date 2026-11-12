package ir.saba.invoice

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.*
import ir.saba.invoice.security.*
import ir.saba.invoice.ui.OfficialSubmissionScreen
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class OfficialSubmissionScreenTest {
    @get:Rule val compose=createComposeRule()
    private val account=AccountScope.of("11111111-1111-4111-8111-111111111111")
    // Test-only JWT-shaped session, standing in for an already authenticated gateway token.
    // Production signatures and revocation are verified by the trusted backend.
    private val testSessionToken: String = run {
        val claims = org.json.JSONObject().put("sub", account.ownerId)
            .put("exp", System.currentTimeMillis() / 1000 + 86_400L)
        "e30." + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(claims.toString().toByteArray(Charsets.UTF_8)) + ".testsignature"
    }
    private lateinit var db:SabaDatabase
    private var company=0L;private var invoice=0L
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),SabaDatabase::class.java).build()
        company=db.companyDao().insert(Company(purchaserId=account.ownerId,name="مؤدی آزمایش",nationalId="1",economicId="12345678901"))
        db.moadianDetailsDao().upsert(MoadianDetails(companyId=company,fiscalMemoryId="A11216"))
        val customer=db.customerDao().insert(Customer(companyId=company,name="خریدار",nationalId="12345678901",economicId="12345678901"))
        invoice=db.invoiceDao().insert(Invoice(companyId=company,customerId=customer,invoiceNumber="12",issueDate="1404/01/01",totalAmountRial=110,taxAmountRial=10,status="FINALIZED"))
        db.invoiceLineDao().insertAll(listOf(InvoiceLine(invoiceId=invoice,itemCode="2710000138624",description="کالا",quantity=1,unitPriceRial=100,taxRatePercent=10)))
        Unit
    }
    @After fun close(){db.close()}
    private fun waitForReady() {
        try {
            compose.waitUntil(15000) {
                compose.onAllNodes(hasTestTag("officialSubmissionList") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"پایان پردازش")).fetchSemanticsNodes().isNotEmpty()
            }
        } catch(e:Exception){throw AssertionError(compose.onRoot().printToString(),e)}
    }
    private fun scrollTo(text:String) {
        try { compose.onNodeWithTag("officialSubmissionList").performScrollToNode(hasText(text)) }
        catch(e:AssertionError){throw AssertionError(compose.onRoot().printToString(),e)}
    }
    @Test fun existingHistoryDoesNotForceCertificateRouteWhenPrivateKeyIsStored() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ctx.getSharedPreferences("sura_tax_credentials_${account.storageKey}", 0)
        prefs.edit().putString("pending_company_$company", "synthetic-staged-key-marker").commit()
        try {
            // An existing V2 submission must not make the next invoice default to V2.
            val service = OfficialSubmissionService(db, account,
                TaxpayerCredentialRepository(ctx, db, account),
                sessionCheck={AccountSession.requireOwner(testSessionToken, account)})
            service.prepare(company, invoice, "2025-03-21T12:00:00+03:30", "1627")
            val next = db.invoiceDao().insert(Invoice(companyId=company,
                customerId=requireNotNull(db.invoiceDao().byId(invoice)).customerId,
                invoiceNumber="13", issueDate="1404/01/02", totalAmountRial=110,
                taxAmountRial=10, status="FINALIZED"))
            db.invoiceLineDao().insertAll(listOf(InvoiceLine(invoiceId=next,
                itemCode="2710000138624", description="کالا", quantity=1,
                unitPriceRial=100, taxRatePercent=10)))

            compose.setContent {MaterialTheme {
                OfficialSubmissionScreen(db,account,company,{},accessToken=testSessionToken)
            }}
            waitForReady()
            scrollTo("آماده‌سازی فاکتور 13")
            compose.onNodeWithText("آماده‌سازی فاکتور 13").performClick()
            scrollTo("شناسه یکتا + کلید خصوصی")
            compose.onNodeWithText("شناسه یکتا + کلید خصوصی").assertIsSelected()
            compose.onNodeWithText("اتصال نسخه ۲ با گواهی").performClick()
            compose.onNodeWithText("اتصال نسخه ۲ با گواهی").assertIsSelected()
            // A subsequent user-triggered refresh must preserve the explicit selection.
            scrollTo("بازخوانی وضعیت محلی")
            compose.onNodeWithText("بازخوانی وضعیت محلی").performClick()
            waitForReady()
            scrollTo("اتصال نسخه ۲ با گواهی")
            compose.onNodeWithText("اتصال نسخه ۲ با گواهی").assertIsSelected()
        } finally {
            prefs.edit().remove("pending_company_$company").commit()
        }
    }
    @Test fun preparingNeverSendsAndRealSubmissionNeedsExplicitConfirmation() {
        compose.setContent {MaterialTheme {OfficialSubmissionScreen(db,account,company,{},accessToken=testSessionToken)}}
        waitForReady()
        scrollTo("آماده‌سازی فاکتور 12")
        compose.onNodeWithText("آماده‌سازی فاکتور 12").performClick()
        scrollTo("واحد سنجش مشترک فقط برای ردیف‌های قدیمی (اختیاری)")
        compose.onNodeWithText("واحد سنجش مشترک فقط برای ردیف‌های قدیمی (اختیاری)").performScrollTo().performTextInput("۱۶۲۷")
        compose.onNodeWithText("اعتبارسنجی و ثبت محتوای ثابت").performScrollTo().performClick()
        waitForReady()
        scrollTo("بررسی و ارسال رسمی")
        compose.onNodeWithText("بررسی و ارسال رسمی").performScrollTo().performClick()
        compose.waitUntil(15000){compose.onAllNodesWithText("تأیید ارسال واقعی به سامانه مؤدیان").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("تأیید ارسال واقعی به سامانه مؤدیان").assertExists()
        compose.onNodeWithText("انصراف").performClick()
        runBlocking {assertEquals("PREPARED",db.officialSubmissionDao().forInvoice(invoice)!!.state)}
    }
    @Test fun unknownRequestShowsInquiryAndNoSubmitButton()=runBlocking {
        val service=OfficialSubmissionService(db,account,
            TaxpayerCredentialRepository(ApplicationProvider.getApplicationContext(),db,account),
            sessionCheck={AccountSession.requireOwner(testSessionToken,account)})
        val row=service.prepare(company,invoice,"2025-03-21T12:00:00+03:30","1627")
        db.officialSubmissionDao().claim(row.requestId)
        db.officialSubmissionDao().resolve(row.requestId,"UNKNOWN",null,"نتیجه نامشخص")
        compose.setContent {MaterialTheme {OfficialSubmissionScreen(db,account,company,{},accessToken=testSessionToken)}}
        waitForReady()
        scrollTo("استعلام همان درخواست")
        compose.onNodeWithText("بررسی و ارسال رسمی").assertDoesNotExist()
        compose.onNodeWithText("پذیرفته‌شده؛ امضای رسید سازمان بررسی شد").assertDoesNotExist()
    }
    @Test fun corruptPersistedPayloadCannotCrashOrOpenSubmissionConfirmation()=runBlocking {
        val service=OfficialSubmissionService(db,account,
            TaxpayerCredentialRepository(ApplicationProvider.getApplicationContext(),db,account),
            sessionCheck={AccountSession.requireOwner(testSessionToken,account)})
        val row=service.prepare(company,invoice,"2025-03-21T12:00:00+03:30","1627")
        db.openHelper.writableDatabase.execSQL("UPDATE official_submissions SET payload='{' WHERE requestId=?",arrayOf(row.requestId))
        compose.setContent {MaterialTheme {OfficialSubmissionScreen(db,account,company,{},accessToken=testSessionToken)}}
        waitForReady();scrollTo("بررسی و ارسال رسمی")
        compose.onNodeWithText("بررسی و ارسال رسمی").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithText("محتوای محفوظ صورتحساب تغییر کرده یا خراب است؛ ارسال متوقف شد.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("تأیید ارسال واقعی به سامانه مؤدیان").assertDoesNotExist()
        assertEquals("PREPARED",db.officialSubmissionDao().byId(row.requestId)!!.state)
    }

}
