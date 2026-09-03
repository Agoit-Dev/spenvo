package com.agoitdev.spenvo.data.appearance

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePreferencesTest {

    private fun crearPreferences(nombre: String = "appearance_test_${System.nanoTime()}"): ThemePreferences {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob()),
        ) { context.preferencesDataStoreFile(nombre) }
        return ThemePreferences(dataStore)
    }

    @Test
    fun sinClavesDevuelveSystemMasBrand() = runBlocking {
        val prefs = crearPreferences()

        val actual = prefs.observarPreferencias().first()

        assertEquals(ThemePreference.SYSTEM, actual.theme)
        assertEquals(ColorPreference.BRAND, actual.color)
    }

    @Test
    fun actualizarTemaPersisteSinAfectarElColor() = runBlocking {
        val prefs = crearPreferences()
        prefs.actualizarColor(ColorPreference.DYNAMIC)

        prefs.actualizarTema(ThemePreference.DARK)

        val actual = prefs.observarPreferencias().first()
        assertEquals(ThemePreference.DARK, actual.theme)
        assertEquals(ColorPreference.DYNAMIC, actual.color)
    }

    @Test
    fun actualizarColorPersisteSinAfectarElTema() = runBlocking {
        val prefs = crearPreferences()
        prefs.actualizarTema(ThemePreference.LIGHT)

        prefs.actualizarColor(ColorPreference.DYNAMIC)

        val actual = prefs.observarPreferencias().first()
        assertEquals(ThemePreference.LIGHT, actual.theme)
        assertEquals(ColorPreference.DYNAMIC, actual.color)
    }

    @Test
    fun valorDesconocidoDecodificaAlDefault() = runBlocking {
        val nombre = "appearance_unknown_${System.nanoTime()}"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rawStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob()),
        ) { context.preferencesDataStoreFile(nombre) }
        rawStore.edit { it[stringPreferencesKey("theme")] = "NOT_A_REAL_VALUE" }

        val prefs = ThemePreferences(rawStore)
        val actual = prefs.observarPreferencias().first()

        assertEquals(ThemePreference.SYSTEM, actual.theme)
    }

    @Test
    fun dynamicPersistidoPorDebajoDeApi31SeNormalizaABrandEnElStore() = runBlocking {
        val nombre = "appearance_dynamic_${System.nanoTime()}"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rawStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob()),
        ) { context.preferencesDataStoreFile(nombre) }
        rawStore.edit { it[stringPreferencesKey("color")] = ColorPreference.DYNAMIC.name }

        val primeraLectura = ThemePreferences(rawStore).observarPreferencias().first()
        assertEquals(ColorPreference.BRAND, primeraLectura.color)

        // The correction must have been written back, not just presented in memory.
        val segundaInstancia = ThemePreferences(rawStore).observarPreferencias().first()
        assertEquals(ColorPreference.BRAND, segundaInstancia.color)
    }

    @Test
    fun persisteEntreInstancias() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val nombreArchivo = "appearance_persistencia_test"
        fun nuevaInstancia(scope: CoroutineScope) = ThemePreferences(
            PreferenceDataStoreFactory.create(scope = scope) {
                context.preferencesDataStoreFile(nombreArchivo)
            },
        )

        val primerScope = CoroutineScope(SupervisorJob())
        nuevaInstancia(primerScope).actualizarTema(ThemePreference.DARK)
        primerScope.cancel()

        val segundoScope = CoroutineScope(SupervisorJob())
        val persistido = nuevaInstancia(segundoScope).observarPreferencias().first()
        segundoScope.cancel()
        context.preferencesDataStoreFile(nombreArchivo).delete()

        assertEquals(ThemePreference.DARK, persistido.theme)
    }
}
