package ir.saba.invoice.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ActivityNotFoundException
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SuraTelegramTest {
    @Test fun opensExactChannelAndFallsBackToHttps() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val started = mutableListOf<Intent>()
        val wrapper = object : ContextWrapper(ctx) {
            override fun startActivity(intent: Intent) {
                started.add(intent)
                if (intent.data?.scheme == "tg") throw ActivityNotFoundException()
            }
        }
        assertTrue(SuraTelegram.open(wrapper))
        assertEquals(listOf("tg://resolve?domain=sura_app", "https://t.me/sura_app"), started.map { it.dataString })
        assertTrue(started.all { it.action == Intent.ACTION_VIEW })
    }
    @Test fun opensTelegramDirectlyWhenAvailable() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val started = mutableListOf<Intent>()
        val wrapper = object : ContextWrapper(ctx) {
            override fun startActivity(intent: Intent) { started.add(intent) }
        }
        assertTrue(SuraTelegram.open(wrapper))
        assertEquals(1, started.size)
        assertEquals("tg://resolve?domain=sura_app", started.single().dataString)
    }
    @Test fun noHandlerReturnsFalseWithoutCrash() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val wrapper = object : ContextWrapper(ctx) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        assertFalse(SuraTelegram.open(wrapper))
    }
}
