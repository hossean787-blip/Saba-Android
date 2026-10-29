package ir.saba.invoice.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

internal fun displayPreferencesMigration(context: Context) =
    SharedPreferencesMigration(context, "display", keysToMigrate = setOf("font_scale"))

private val Context.sabaPreferences by preferencesDataStore(
    name = "saba_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    produceMigrations = { context -> listOf(displayPreferencesMigration(context)) }
)

/** Non-secret UI settings only. Do not use DataStore for private signing keys. */
class SabaPreferences internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.sabaPreferences)
    private val fontScaleKey = floatPreferencesKey("font_scale")
    val fontScale: Flow<Float> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val value = prefs[fontScaleKey] ?: 1f
            if (value.isFinite()) value.coerceIn(0.85f, 1.35f) else 1f
        }

    suspend fun setFontScale(value: Float) {
        require(value.isFinite() && value in 0.85f..1.35f)
        store.edit { it[fontScaleKey] = value }
    }
}
