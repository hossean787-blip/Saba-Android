package ir.saba.invoice

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.*
import ir.saba.invoice.security.AccountScope
import ir.saba.invoice.ui.TaxpayerConnectionScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class TaxpayerConnectionScreenTest {
    @get:Rule val compose=createComposeRule()
    private val account=AccountScope.of("11111111-1111-4111-8111-111111111111")
    private lateinit var db:SabaDatabase
    private var first=0L;private var foreign=0L
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),SabaDatabase::class.java).build()
        first=db.companyDao().insert(Company(purchaserId=account.ownerId,name="مودی حساب اول",nationalId="1"))
        foreign=db.companyDao().insert(Company(purchaserId="22222222-2222-4222-8222-222222222222",name="مودی حساب دیگر",nationalId="2"))
        db.moadianDetailsDao().upsert(MoadianDetails(companyId=first,fiscalMemoryId="A11216"))
    }
    @After fun close(){db.close()}
    @Test fun unconfiguredProfileCannotProbeOrSaveCredentials() {
        compose.setContent {MaterialTheme{TaxpayerConnectionScreen(db,account,first,{},{},{})}}
        compose.waitUntil(15000){compose.onAllNodesWithText("مودی: مودی حساب اول").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("بررسی اتصال رسمی بدون ارسال صورتحساب").assertIsNotEnabled()
        compose.onNodeWithText("ثبت امن کلید و گواهی برای این مؤدی").assertIsNotEnabled()
        compose.onNodeWithText("انتخاب گواهی CER / CRT / PEM").assertExists()
    }
    @Test fun changingToForeignTaxpayerClearsOldProfileAndRejectsAccess() {
        var selected by mutableLongStateOf(first)
        compose.setContent {MaterialTheme{TaxpayerConnectionScreen(db,account,selected,{},{},{})}}
        compose.waitUntil(15000){compose.onAllNodesWithText("شناسه حافظه: A11216").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnIdle{selected=foreign}
        compose.waitUntil(15000){compose.onAllNodesWithText("مودی متعلق به این حساب نیست.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("شناسه حافظه: A11216").assertDoesNotExist()
        compose.onNodeWithText("مودی: مودی حساب دیگر").assertDoesNotExist()
        compose.onNodeWithText("بررسی اتصال رسمی بدون ارسال صورتحساب").assertIsNotEnabled()
    }
}
