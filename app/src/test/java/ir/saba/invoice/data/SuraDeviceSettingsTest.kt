package ir.saba.invoice.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class SuraDeviceSettingsTest {
    @Test fun deviceOptionsPersistIndependentlyAndDefaultSafely() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefs=ctx.getSharedPreferences("sura_device_settings",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val first=SuraDeviceSettings(ctx)
            assertFalse(first.protectScreen);assertTrue(first.playIntro)
            assertTrue(first.setProtectScreen(true));assertTrue(first.setPlayIntro(false))
            val reopened=SuraDeviceSettings(ctx)
            assertTrue(reopened.protectScreen);assertFalse(reopened.playIntro)
            assertTrue(reopened.setPlayIntro(true))
            assertTrue(SuraDeviceSettings(ctx).protectScreen)
        }finally{prefs.edit().clear().commit()}
    }
}
