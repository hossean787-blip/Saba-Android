package ir.saba.invoice.ui

import androidx.compose.ui.test.assertExists
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.R
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.saba.invoice.auth.AuthTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StartupLogoMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun skipIntroductionCallsFinishExactlyOnce() {
        var count = 0
        compose.setContent { AuthTheme { StartupLogoMotion { count++ } } }
        compose.onNodeWithText("رد کردن معرفی").assertExists().performClick()
        compose.onNodeWithText("رد کردن معرفی").performClick()
        compose.runOnIdle { assertEquals(1, count) }
    }

    @Test fun introUsesTransparentFramelessMarkWithoutChangingLauncherArtwork() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val original = BitmapFactory.decodeResource(context.resources, R.drawable.sura_brand_icon)
        val mark = cutoutSuraSymbol(original)
        assertTrue(mark.width < original.width)
        assertEquals(0, AndroidColor.alpha(mark.getPixel(0, 0)))
        assertTrue(AndroidColor.alpha(mark.getPixel(mark.width / 2, mark.height / 2)) > 100)
    }

    @Test fun logoTimelineBeginsAsParticlesAndEndsAsSolidBrand() {
        assertEquals(0f, logoGather(0f), 0.00001f)
        assertEquals(0f, logoReveal(0.60f), 0.00001f)
        assertTrue(logoGather(0.45f) > 0f)
        assertTrue(logoGather(0.45f) < 1f)
        assertEquals(1f, logoGather(0.9f), 0.00001f)
        assertEquals(1f, logoReveal(1f), 0.00001f)
    }
}
