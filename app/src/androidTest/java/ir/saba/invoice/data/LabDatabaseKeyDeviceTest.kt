package ir.saba.invoice.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.saba.invoice.security.AccountScope
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LabDatabaseKeyDeviceTest {
    @Test fun keyPersistsAndFailsClosedWhenEnvelopeOrKeystoreIsLost() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val a=AccountScope.of(UUID.randomUUID().toString())
        val b=AccountScope.of(UUID.randomUUID().toString())
        val one=LabDatabaseKey(ctx,a)
        val two=LabDatabaseKey(ctx,b)
        try {
            assertTrue(runCatching {one.openOrCreate(true)}.isFailure)
            val first=one.openOrCreate(false)
            val second=two.openOrCreate(false)
            val again=LabDatabaseKey(ctx,a).openOrCreate(true)
            try {
                assertArrayEquals(first,again)
                assertFalse(first.contentEquals(second))
                val wrapped=File(ctx.noBackupFilesDir,"sqlcipher-lab-keys/"+a.storageKey+".wrapped")
                val original=wrapped.readBytes()
                assertEquals(61,original.size)
                assertFalse(String(original,Charsets.ISO_8859_1).contains(String(first,Charsets.US_ASCII)))
                val tampered=original.copyOf().apply {this[lastIndex]=(last().toInt() xor 1).toByte()}
                wrapped.writeBytes(tampered)
                assertTrue(runCatching {one.openOrCreate(true)}.isFailure)
                assertArrayEquals(tampered,wrapped.readBytes())
                wrapped.writeBytes(original)
                KeyStore.getInstance("AndroidKeyStore").apply {load(null);deleteEntry("sura.sqlcipher.lab."+a.storageKey)}
                assertTrue(runCatching {one.openOrCreate(true)}.isFailure)
                assertArrayEquals(original,wrapped.readBytes())
            } finally {first.fill(0);second.fill(0);again.fill(0)}
        } finally {one.cleanupSyntheticFixture();two.cleanupSyntheticFixture()}
    }
}
