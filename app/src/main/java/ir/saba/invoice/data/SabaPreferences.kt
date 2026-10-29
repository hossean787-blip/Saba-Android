package ir.saba.invoice.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sabaPreferences by preferencesDataStore(name = "saba_settings")

/** Non-secret UI settings only. Do not use DataStore for private signing keys. */
class SabaPreferences(private val appContext: Context) {
    private val fontScaleKey = floatPreferencesKey("font_scale")
    val fontScale: Flow<Float> = appContext.sabaPreferences.data.map { prefs ->
        (prefs[fontScaleKey] ?: 1.0f).coerceIn(0.85f, 1.35f)
    }

    suspend fun setFontScale(value: Float) {
        require(value.isFinite() && value in 0.85f..1.35f)
        appContext.sabaPreferences.edit { it[fontScaleKey] = value }
    }
}
