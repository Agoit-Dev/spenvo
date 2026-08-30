package com.agoitdev.spenvo.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SesionPreferencesTest {

    private fun crearPreferences(): SesionPreferences {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob()),
        ) { context.preferencesDataStoreFile("sesion_test_${System.nanoTime()}") }
        return SesionPreferences(dataStore)
    }

    @Test
    fun defaultEsFalse() = runBlocking {
        val preferences = crearPreferences()

        assertFalse(preferences.sesionCerradaExplicitamente.first())
    }

    @Test
    fun marcarLogoutPonePendienteEnTrue() = runBlocking {
        val preferences = crearPreferences()

        preferences.marcarLogout()

        assertTrue(preferences.sesionCerradaExplicitamente.first())
    }

    @Test
    fun limpiarLogoutVuelveAFalse() = runBlocking {
        val preferences = crearPreferences()
        preferences.marcarLogout()

        preferences.limpiarLogout()

        assertFalse(preferences.sesionCerradaExplicitamente.first())
    }

    @Test
    fun marcarLogoutPersisteEntreInstancias() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val nombreArchivo = "sesion_persistencia_test"
        fun nuevaInstancia(scope: CoroutineScope) = SesionPreferences(
            PreferenceDataStoreFactory.create(
                scope = scope,
            ) { context.preferencesDataStoreFile(nombreArchivo) },
        )

        val primerScope = CoroutineScope(SupervisorJob())
        nuevaInstancia(primerScope).marcarLogout()
        primerScope.cancel()

        val segundoScope = CoroutineScope(SupervisorJob())
        val segundaInstancia = nuevaInstancia(segundoScope)
        val persistido = segundaInstancia.sesionCerradaExplicitamente.first()
        segundoScope.cancel()
        context.preferencesDataStoreFile(nombreArchivo).delete()

        assertTrue(persistido)
    }
}
