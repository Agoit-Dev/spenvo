package com.agoitdev.spenvo.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sesionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sesion")

@Singleton
class SesionPreferences internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.sesionDataStore)

    val sesionCerradaExplicitamente: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LOGOUT_EXPLICITO] ?: false
    }

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
