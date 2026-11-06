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
    @Test fun preparingNeverSendsAndRealSubmissionNeedsExplicitConfirmation() {
        compose.setContent {MaterialTheme {OfficialSubmissionScreen(db,account,company,{})}}
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
        val service=OfficialSubmissionService(db,account,TaxpayerCredentialRepository(ApplicationProvider.getApplicationContext(),db,account))
        val row=service.prepare(company,invoice,"2025-03-21T12:00:00+03:30","1627")
        db.officialSubmissionDao().claim(row.requestId)
        db.officialSubmissionDao().resolve(row.requestId,"UNKNOWN",null,"نتیجه نامشخص")
        compose.setContent {MaterialTheme {OfficialSubmissionScreen(db,account,company,{})}}
        waitForReady()
        scrollTo("استعلام همان درخواست")
        compose.onNodeWithText("بررسی و ارسال رسمی").assertDoesNotExist()
        compose.onNodeWithText("پذیرفته‌شده؛ امضای رسید سازمان بررسی شد").assertDoesNotExist()
    }
    @Test fun corruptPersistedPayloadCannotCrashOrOpenSubmissionConfirmation()=runBlocking {
        val service=OfficialSubmissionService(db,account,TaxpayerCredentialRepository(ApplicationProvider.getApplicationContext(),db,account))
        val row=service.prepare(company,invoice,"2025-03-21T12:00:00+03:30","1627")
        db.openHelper.writableDatabase.execSQL("UPDATE official_submissions SET payload='{' WHERE requestId=?",arrayOf(row.requestId))
        compose.setContent {MaterialTheme {OfficialSubmissionScreen(db,account,company,{})}}
        waitForReady();scrollTo("بررسی و ارسال رسمی")
        compose.onNodeWithText("بررسی و ارسال رسمی").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithText("محتوای محفوظ صورتحساب تغییر کرده یا خراب است؛ ارسال متوقف شد.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("تأیید ارسال واقعی به سامانه مؤدیان").assertDoesNotExist()
        assertEquals("PREPARED",db.officialSubmissionDao().byId(row.requestId)!!.state)
    }

}
