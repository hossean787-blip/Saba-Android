package ir.saba.invoice

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import ir.saba.invoice.data.*
import ir.saba.invoice.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Screenshots render actual composables on Android using explicitly synthetic test fixtures. */
@RunWith(AndroidJUnit4::class)
class HomeMoadianVisualInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.getExternalFilesDir("screenshots")!!
        directory.mkdirs()
        File(directory, name + ".png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun homeUsesActualLayoutAndClearlyLocalInvoiceStatus() {
        compose.setContent { MaterialTheme(colorScheme = lightColorScheme(
            primary = Color(0xFF075F91), onPrimary = Color.White,
            background = Color(0xFFF2F9FF), surface = Color.White,
            onSurface = Color(0xFF172C3D)
        )) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HomeDashboard("مودی آزمایشی اراک", 12, 1, 0, 3,
                    listOf(Invoice(id = 1, companyId = 1, customerId = 1, invoiceNumber = "1000000001",
                        issueDate = "1405/08/08", totalAmountRial = 2500000, taxAmountRial = 0, status = "DRAFT")),
                    {}, {}, {}, {}, {})
            }
        } }
        compose.onNodeWithText("ثبت فاکتور جدید").assertExists()
        screenshot("home-emulator")
        // Recent activity is below the fold on compact phones; LazyColumn
        // composes it only after scrolling. Verify real data after scrolling.
        compose.onNode(hasScrollAction()).performScrollToIndex(6)
        compose.onNodeWithText("صورتحساب 1000000001").assertExists()
    }
    @Test fun ownerAccountHubRendersActualTwoColumnCardsOnAndroid() {
        compose.setContent { AuthTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AccountScreen(
                    taxpayerCharge = 12L, ownerCharge = 36L, busy = false,
                    ownerName = "مالک آزمایشی", ownerEmail = "test-owner@example.invalid",
                    onTransferCharge = {}, onLogout = {}
                )
            }
        } }
        compose.onNodeWithTag("personalInfo").assertExists()
        compose.onNodeWithTag("transferCharge").assertExists()
        screenshot("owner-account-emulator")
    }

    @Test fun appSettingsAreSeparateFromOwnerAccountOnAndroid() {
        compose.setContent { AuthTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AppSettingsScreen(
                    fontScale = 1f, busy = false,
                    onFontScale = {}, onCommitFontScale = {}, onResetFont = {},
                    biometricEnabled = true, onDisableBiometrics = {},
                    onBackToAccount = {}
                )
            }
        } }
        compose.onNodeWithText("تنظیمات نمایش").assertExists()
        screenshot("app-settings-emulator")
    }

    @Test fun taxpayerFormAndTableRenderOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, SabaDatabase::class.java).build()
        try {
            runBlocking { db.companyDao().insert(Company(purchaserId = "test", name = "مودی آزمایشی", nationalId = "12345678901")) }
            val rows = runBlocking { db.companyDao().all() }
            compose.setContent { MaterialTheme(colorScheme = lightColorScheme(
            primary = Color(0xFF075F91), onPrimary = Color.White,
            background = Color(0xFFF2F9FF), surface = Color.White,
            onSurface = Color(0xFF172C3D)
        )) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MoadianScreen(db, rows, {}, {}, {}, {})
                }
            } }
            compose.onNodeWithText("مودی آزمایشی").assertExists()
            screenshot("moadian-table-emulator")
            compose.onNodeWithText("جدید").performClick()
            compose.onNodeWithText("نام *").assertExists()
            screenshot("moadian-form-emulator")
        } finally { compose.waitForIdle(); db.close() }
    }
}
