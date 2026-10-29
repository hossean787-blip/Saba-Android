package ir.saba.invoice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Executes the real activity and the real Snackbar host, without external services. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun savingCompanyShowsSuccessAndReturnsToTheCompanyList() {
        compose.waitUntil(15000) { compose.onAllNodesWithText("ثبت شرکت").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ثبت شرکت").performScrollTo().performClick()
        compose.onNodeWithText("افزودن شرکت / مؤدی").performClick()
        compose.onNodeWithText("نام").performTextInput("شرکت آزمایشی صبا")
        compose.onNodeWithText("شناسه ملی").performTextInput("۱۲۳۴۵۶۷۸۹۰۱")
        compose.onNodeWithText("ذخیره اطلاعات").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("اطلاعات شرکت ذخیره شد.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("اطلاعات شرکت ذخیره شد.").assertExists()
        compose.onNodeWithText("شناسه ملی: 12345678901").assertExists()
    }
}
