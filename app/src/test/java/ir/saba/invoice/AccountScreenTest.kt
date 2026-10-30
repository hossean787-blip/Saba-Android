package ir.saba.invoice

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import ir.saba.invoice.ui.AccountScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccountScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun settingsExpandAndChargeAndLogoutRemainReachable() {
        var transfers = 0
        var logouts = 0
        compose.setContent { MaterialTheme {
            AccountScreen(3, 7, 1f, false, { transfers++ }, {}, {}, {}, { logouts++ })
        } }
        compose.onNodeWithText("تنظیمات نمایش").assertDoesNotExist()
        compose.onNodeWithText("خروج از حساب").performClick()
        assertEquals(1, logouts)
        compose.onNodeWithText("تنظیمات برنامه").performClick()
        compose.onNodeWithText("تنظیمات نمایش").assertExists()
        compose.onAllNodesWithText("انتقال شارژ")[1].performScrollTo().performClick()
        assertEquals(1, transfers)
        compose.onNodeWithText("بستن تنظیمات برنامه").performScrollTo().performClick()
        compose.onNodeWithText("تنظیمات نمایش").assertDoesNotExist()
    }
}
