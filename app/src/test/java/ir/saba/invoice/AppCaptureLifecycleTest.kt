package ir.saba.invoice

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Prevent private content from appearing in a background task snapshot. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppCaptureLifecycleTest {
    @Test fun screenshotsAllowedOnlyWhileForegrounded() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val secureFlag = WindowManager.LayoutParams.FLAG_SECURE
        assertEquals(0, activity.window.attributes.flags and secureFlag)
        controller.pause()
        assertTrue(activity.window.attributes.flags and secureFlag != 0)
        controller.resume()
        assertEquals(0, activity.window.attributes.flags and secureFlag)
        controller.pause().stop().destroy()
    }
}
