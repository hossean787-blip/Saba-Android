package ir.saba.invoice.ui

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class SuraContactTest {
    @Test fun contactOpensDialerWithExactNumberWithoutAutomaticCall() {
        val intent=SuraContact.dialIntent()
        assertEquals(Intent.ACTION_DIAL,intent.action)
        assertEquals("tel:09364382859",intent.dataString)
        assertNotEquals(Intent.ACTION_CALL,intent.action)
    }
}
