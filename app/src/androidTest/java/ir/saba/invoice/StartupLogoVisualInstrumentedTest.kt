package ir.saba.invoice

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.saba.invoice.auth.AuthTheme
import ir.saba.invoice.ui.StartupLogoMotion
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Collects real emulator frames, without recording credentials or real taxpayers. */
@RunWith(AndroidJUnit4::class)
class StartupLogoVisualInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private fun screenshot(filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = context.getExternalFilesDir("screenshots")!!
        assertTrue(dir.mkdirs() || dir.isDirectory)
        val file = File(dir, "$filename.png")
        file.outputStream().use { output ->
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        assertTrue(file.length() > 1000L)
    }

    @Test fun particleConvergenceAndFinalFramelessLogoRenderOnDevice() {
        compose.mainClock.autoAdvance = false
        compose.setContent { AuthTheme { StartupLogoMotion(onFinished = {}) } }
        compose.onNodeWithText("رد کردن معرفی").assertExists()
        compose.mainClock.advanceTimeBy(1550L)
        compose.waitForIdle()
        screenshot("sura-intro-particles-emulator")
        compose.mainClock.advanceTimeBy(1500L)
        compose.waitForIdle()
        screenshot("sura-intro-mark-emulator")
        compose.onNodeWithText("رد کردن معرفی").assertExists()
    }
}
