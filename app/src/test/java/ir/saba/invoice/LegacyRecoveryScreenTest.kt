package ir.saba.invoice

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.*
import ir.saba.invoice.security.AccountScope
import ir.saba.invoice.ui.LegacyRecoveryScreen
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class LegacyRecoveryScreenTest {
    @get:Rule val compose=createComposeRule()
    private val account=AccountScope.of("11111111-1111-4111-8111-111111111111")
    private lateinit var source:SabaDatabase
    private lateinit var target:SabaDatabase
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    @Before fun setup()=runBlocking {
        source=SabaDatabase.getInstance(context)
        target=Room.inMemoryDatabaseBuilder(context,SabaDatabase::class.java).build()
        source.companyDao().insert(Company(id=17,purchaserId=account.ownerId,name="پرونده مجاز",nationalId="old"))
        source.companyDao().insert(Company(id=18,purchaserId="owner",name="پرونده محرمانه بدون مالک",nationalId="unknown"))
        Unit
    }
    @After fun close(){source.close();target.close();context.deleteDatabase("saba_local.db")}
    @Test fun loadedEligibleCompanyCanBeRecoveredWithoutRevealingUnknownOwners() {
        compose.setContent {MaterialTheme {LegacyRecoveryScreen(target,account,{})}}
        compose.waitUntil(15000){compose.onAllNodesWithText("بازیابی پرونده مجاز").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("بازیابی پرونده محرمانه بدون مالک").assertDoesNotExist()
        compose.onNodeWithText("بازیابی پرونده مجاز").performScrollTo().performClick()
        compose.onNodeWithText("تأیید بازیابی").performClick()
        compose.waitUntil(15000){runBlocking {target.legacyImportReceiptDao().byCompany(17) != null}}
        runBlocking {
            assertEquals(account.ownerId,target.companyDao().byId(17)!!.purchaserId)
            assertNull(target.companyDao().byId(18));assertNotNull(source.companyDao().byId(17))
        }
    }
}
