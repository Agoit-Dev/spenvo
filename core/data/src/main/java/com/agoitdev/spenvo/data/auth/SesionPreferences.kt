package com.agoitdev.spenvo.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.sesionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sesion")

@Singleton
class SesionPreferences internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.sesionDataStore)

    val sesionCerradaExplicitamente: Flow<Boolean> = dataStore.data
        // A corrupted or unreadable preferences file throws instead of emitting. Falling back to
        // empty preferences (the standard DataStore pattern) degrades to "no explicit logout
        // recorded" rather than killing the flow the root session gate collects — which would
        // otherwise leave the app stuck on the gate's loading placeholder forever.
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_LOGOUT_EXPLICITO] ?: false }

    suspend fun marcarLogout() {
        dataStore.edit { it[KEY_LOGOUT_EXPLICITO] = true }
    }

    suspend fun limpiarLogout() {
        dataStore.edit { it[KEY_LOGOUT_EXPLICITO] = false }
    }

    private companion object {
        val KEY_LOGOUT_EXPLICITO = booleanPreferencesKey("sesion_cerrada_explicitamente")
    }
}
