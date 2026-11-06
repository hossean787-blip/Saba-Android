package ir.saba.invoice

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.saba.invoice.ui.HomeDashboard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HomeDashboardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun taxKeysShortcutIsClearlyForKeyGenerationAndCallsOnlyKeyAction() {
        var keyActionCount = 0
        var selectTaxpayerCount = 0
        var invoiceCount = 0
        compose.setContent {
            MaterialTheme {
                HomeDashboard(
                    taxpayerName = "مودی نمونه",
                    remainingCharge = 0,
                    draftCount = 0,
                    queueCount = 0,
                    customerCount = 0,
                    invoices = emptyList(),
                    onSelectTaxpayer = { selectTaxpayerCount++ },
                    onNewInvoice = { invoiceCount++ },
                    onTaxKeys = { keyActionCount++ },
                    onInvoices = {},
                    onCharge = {},
                    taxpayerOptions = listOf(1L to "مودی نمونه")
                )
            }
        }
        compose.onNodeWithText("ساخت کلید مالیاتی").assertExists().performClick()
        assertEquals(1, keyActionCount)
        assertEquals(0, selectTaxpayerCount)
        assertEquals(0, invoiceCount)
    }
}
