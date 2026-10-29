package ir.saba.invoice.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SabaPreferencesTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    @Before fun setup() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = temporary.newFolder().resolve("settings.preferences_pb")
        store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }
    @After fun close() { scope.cancel() }

    @Test fun persistedScaleIsReadByANewRepository() = runBlocking {
        SabaPreferences(store).setFontScale(1.25f)
        assertEquals(1.25f, SabaPreferences(store).fontScale.first(), 0f)
    }

    @Test fun invalidStoredScaleCannotReachComposeDensity() = runBlocking {
        store.edit { it[floatPreferencesKey("font_scale")] = Float.NaN }
        assertEquals(1f, SabaPreferences(store).fontScale.first(), 0f)
    }

    @Test fun finiteLegacyScaleIsClampedToSupportedRange() = runBlocking {
        store.edit { it[floatPreferencesKey("font_scale")] = 10f }
        assertEquals(1.35f, SabaPreferences(store).fontScale.first(), 0f)
    }

    @Test fun invalidWriteLeavesExistingValueUnchanged() = runBlocking {
        val settings = SabaPreferences(store)
        settings.setFontScale(1.1f)
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, 2f)) {
            try { settings.setFontScale(bad); fail("Invalid scale accepted") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(1.1f, settings.fontScale.first(), 0f)
    }

    @Test fun ioFailureUsesSafeDefault() = runBlocking {
        val unavailable = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("unavailable") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("unavailable")
        }
        assertEquals(1f, SabaPreferences(unavailable).fontScale.first(), 0f)
    }

    @Test fun migratesExistingDisplayPreferenceBeforeFirstRead() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("display", Context.MODE_PRIVATE).edit().putFloat("font_scale", 1.2f).commit()
        // A migration is a first-open operation. Use a fresh file rather than the
        // application singleton already opened by Activity tests in this JVM.
        val file = temporary.newFolder().resolve("migrated.preferences_pb")
        val migrated = PreferenceDataStoreFactory.create(
            migrations = listOf(displayPreferencesMigration(context)),
            scope = scope,
            produceFile = { file }
        )
        assertEquals(1.2f, SabaPreferences(migrated).fontScale.first(), 0f)
        assertFalse(context.getSharedPreferences("display", Context.MODE_PRIVATE).contains("font_scale"))
    }
}
