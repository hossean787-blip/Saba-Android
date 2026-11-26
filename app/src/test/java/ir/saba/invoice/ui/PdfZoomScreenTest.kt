package ir.saba.invoice.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PdfZoomScreenTest {
    @get:Rule val compose=createComposeRule()

    @Test fun zoomButtonsResetAndExpandSamePage() {
        val bitmap=Bitmap.createBitmap(842,595,Bitmap.Config.ARGB_8888)
        compose.setContent { MaterialTheme { ZoomableInvoicePdf(bitmap) } }
        compose.onNodeWithTag("pdfZoomPercent").assertTextEquals("۱۰۰٪")
        compose.onNodeWithTag("pdfZoomOut").assertIsNotEnabled()
        compose.onNodeWithTag("pdfZoomIn").performClick()
        compose.onNodeWithTag("pdfZoomPercent").assertTextEquals("۱۵۰٪")
        compose.onNodeWithTag("pdfZoomReset").performClick()
        compose.onNodeWithTag("pdfZoomPercent").assertTextEquals("۱۰۰٪")
        compose.onNodeWithTag("pdfExpand").performClick()
        compose.onNodeWithTag("pdfCloseExpanded").assertExists().performClick()
        compose.onNodeWithTag("pdfExpand").assertExists()
    }
}
