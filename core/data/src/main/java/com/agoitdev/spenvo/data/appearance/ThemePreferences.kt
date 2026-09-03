package com.agoitdev.spenvo.data.appearance

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "appearance")

@Singleton
class ThemePreferences internal constructor(
    private val dataStore: DataStore<Preferences>,
) : AppearancePreferencesRepository {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context.appearanceDataStore)

    override fun observarPreferencias(): Flow<AppearancePreferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> normalizar(prefs) }

    override suspend fun actualizarTema(theme: ThemePreference) {
        dataStore.edit { it[KEY_THEME] = theme.name }
    }

    override suspend fun actualizarColor(color: ColorPreference) {
        dataStore.edit { it[KEY_COLOR] = color.name }
    }

    /**
     * Reads decode defensively (unknown/corrupted value -> default). A persisted `DYNAMIC` on an
     * API below 31 (restored backup, OS downgrade) is corrected in the store itself, not just in
     * the value handed back — the write only fires when the anomaly is actually present, so it is
     * not a per-emission write.
     */
    private suspend fun normalizar(prefs: Preferences): AppearancePreferences {
        val theme = prefs[KEY_THEME]
            ?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
            ?: ThemePreference.SYSTEM
        val colorCrudo = prefs[KEY_COLOR]
            ?.let { runCatching { ColorPreference.valueOf(it) }.getOrNull() }
            ?: ColorPreference.BRAND
        val dynamicSoportado = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (colorCrudo == ColorPreference.DYNAMIC && !dynamicSoportado) {
            // A write failure here must not kill the flow: the UI still gets the corrected value
            // for this emission, and the anomaly (still DYNAMIC in the store) triggers a retry on
            // the next natural read.
            runCatching { dataStore.edit { it[KEY_COLOR] = ColorPreference.BRAND.name } }
            return AppearancePreferences(theme, ColorPreference.BRAND)
        }
        return AppearancePreferences(theme, colorCrudo)
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_COLOR = stringPreferencesKey("color")
    }
}
