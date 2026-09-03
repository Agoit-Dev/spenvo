package com.agoitdev.spenvo.ajustes

import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository
import com.agoitdev.spenvo.domain.usecase.ActualizarColorUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarTemaUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarAppearanceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AjustesViewModelTest {

    private val flow = MutableStateFlow(AppearancePreferences())
    private lateinit var repo: FakeAppearanceRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repo = FakeAppearanceRepository(flow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel() = AjustesViewModel(
        observarAppearance = ObservarAppearanceUseCase(repo),
        actualizarTema = ActualizarTemaUseCase(repo),
        actualizarColor = ActualizarColorUseCase(repo),
    )

    @Test
    fun `el estado refleja la preferencia actual`() = runTest {
        flow.value = AppearancePreferences(ThemePreference.DARK, ColorPreference.DYNAMIC)
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }

        advanceUntilIdle()

        assertEquals(ThemePreference.DARK, viewModel.estado.value.theme)
        assertEquals(ColorPreference.DYNAMIC, viewModel.estado.value.color)
        job.cancel()
    }

    @Test
    fun `seleccionarTema invoca solo el caso de uso de tema`() = runTest {
        val viewModel = crearViewModel()

        viewModel.seleccionarTema(ThemePreference.DARK)
        advanceUntilIdle()

        assertEquals(listOf(ThemePreference.DARK), repo.temasActualizados)
        assertTrue(repo.coloresActualizados.isEmpty())
    }

    @Test
    fun `seleccionarColor invoca solo el caso de uso de color`() = runTest {
        val viewModel = crearViewModel()

        viewModel.seleccionarColor(ColorPreference.DYNAMIC)
        advanceUntilIdle()

        assertEquals(listOf(ColorPreference.DYNAMIC), repo.coloresActualizados)
        assertTrue(repo.temasActualizados.isEmpty())
    }

    @Test
    fun `escritura fallida marca errorGuardado sin tocar la preferencia`() = runTest {
        repo.fallarProximaEscritura = true
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }

        viewModel.seleccionarTema(ThemePreference.DARK)
        advanceUntilIdle()

        assertTrue(viewModel.estado.value.errorGuardado)
        assertEquals(ThemePreference.SYSTEM, viewModel.estado.value.theme)
        job.cancel()
    }

    @Test
    fun `consumirErrorGuardado limpia el error`() = runTest {
        repo.fallarProximaEscritura = true
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }
        viewModel.seleccionarTema(ThemePreference.DARK)
        advanceUntilIdle()

        assertTrue(viewModel.estado.value.errorGuardado)

        viewModel.consumirErrorGuardado()
        advanceUntilIdle()

        assertFalse(viewModel.estado.value.errorGuardado)
        job.cancel()
    }
}

private class FakeAppearanceRepository(
    private val preferencias: Flow<AppearancePreferences>,
) : AppearancePreferencesRepository {
    val temasActualizados = mutableListOf<ThemePreference>()
    val coloresActualizados = mutableListOf<ColorPreference>()
    var fallarProximaEscritura = false

    override fun observarPreferencias(): Flow<AppearancePreferences> = preferencias

    override suspend fun actualizarTema(theme: ThemePreference) {
        if (fallarProximaEscritura) throw java.io.IOException("boom")
        temasActualizados += theme
    }

    override suspend fun actualizarColor(color: ColorPreference) {
        if (fallarProximaEscritura) throw java.io.IOException("boom")
        coloresActualizados += color
    }
}
