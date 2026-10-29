package ir.saba.invoice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verify unauthenticated app startup is gated by owner login. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun unauthenticatedLaunchShowsOwnerLoginInsteadOfCompanyDashboard() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("ورود به سورا").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("ورود به سورا").assertExists()
        compose.onNodeWithText("ثبت شرکت").assertDoesNotExist()
        compose.onNodeWithText("ایجاد حساب کاربری").assertExists()
    }
}
