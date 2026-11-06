package ir.saba.invoice.data

import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.identifiers.SuraBundledCatalogInstaller
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SuraBundledCatalogInstallerTest {
    @Test fun noInstallTimeAssetsLeavesLightweightBuildUnchanged() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val installer = SuraBundledCatalogInstaller(context)
        // Normal CI omits this huge, separately built install-time asset package.
        assertNull(installer.installIfNeeded())
    }

    @Test fun completeBankManifestUsesTheBuiltAndVerifiedDataset() {
        assertEquals(4_227_218L, SuraBundledCatalogInstaller.FULL_RECORD_COUNT)
        assertEquals(1_830_764_544L, SuraBundledCatalogInstaller.FULL_DATABASE_BYTES)
        assertEquals(
            "567fbba0e54a863879c790a848866a7e16d48ad272c66e286172f87b67fd0e9f",
            SuraBundledCatalogInstaller.FULL_DATABASE_SHA256
        )
    }
}
