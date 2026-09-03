package com.agoitdev.spenvo

import com.agoitdev.spenvo.designsystem.theme.ColorMode
import com.agoitdev.spenvo.designsystem.theme.ThemeMode
import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `estado inicial es Loading`() = runTest {
        val repo = FakeAppearanceRepository(MutableStateFlow(AppearancePreferences()))
        val viewModel = AppearanceViewModel(ObservarAppearanceUseCase(repo))
        val job = launch { viewModel.estado.collect {} }

        assertEquals(AppearanceUiState.Loading, viewModel.estado.value)
        job.cancel()
    }

    @Test
    fun `primera emision produce Ready con el mapeo correcto`() = runTest {
        val repo = FakeAppearanceRepository(
            MutableStateFlow(AppearancePreferences(ThemePreference.DARK, ColorPreference.DYNAMIC)),
        )
        val viewModel = AppearanceViewModel(ObservarAppearanceUseCase(repo))
        val job = launch { viewModel.estado.collect {} }

        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertTrue(estado is AppearanceUiState.Ready)
        estado as AppearanceUiState.Ready
        assertEquals(ThemeMode.DARK, estado.themeMode)
        assertEquals(ColorMode.DYNAMIC, estado.colorMode)
        job.cancel()
    }

    @Test
    fun `mapea los seis valores de dominio a design system`() = runTest {
        val flow = MutableStateFlow(AppearancePreferences(ThemePreference.SYSTEM, ColorPreference.BRAND))
        val repo = FakeAppearanceRepository(flow)
        val viewModel = AppearanceViewModel(ObservarAppearanceUseCase(repo))
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()

        val casos = listOf(
            ThemePreference.SYSTEM to ThemeMode.SYSTEM,
            ThemePreference.LIGHT to ThemeMode.LIGHT,
            ThemePreference.DARK to ThemeMode.DARK,
        )
        for ((dominio, esperado) in casos) {
            flow.value = AppearancePreferences(dominio, ColorPreference.BRAND)
            advanceUntilIdle()
            assertEquals(esperado, (viewModel.estado.value as AppearanceUiState.Ready).themeMode)
        }

        val coloresCasos = listOf(
            ColorPreference.BRAND to ColorMode.BRAND,
            ColorPreference.DYNAMIC to ColorMode.DYNAMIC,
        )
        for ((dominio, esperado) in coloresCasos) {
            flow.value = AppearancePreferences(ThemePreference.SYSTEM, dominio)
            advanceUntilIdle()
            assertEquals(esperado, (viewModel.estado.value as AppearanceUiState.Ready).colorMode)
        }
        job.cancel()
    }
}

private class FakeAppearanceRepository(
    private val preferencias: Flow<AppearancePreferences>,
) : AppearancePreferencesRepository {
    override fun observarPreferencias(): Flow<AppearancePreferences> = preferencias
    override suspend fun actualizarTema(theme: ThemePreference) = Unit
    override suspend fun actualizarColor(color: ColorPreference) = Unit
}
