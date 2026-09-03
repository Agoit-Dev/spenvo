package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceUseCaseTest {

    @Test
    fun `AppearancePreferences por defecto es SYSTEM mas BRAND`() {
        assertEquals(
            AppearancePreferences(ThemePreference.SYSTEM, ColorPreference.BRAND),
            AppearancePreferences(),
        )
    }

    @Test
    fun `ObservarAppearanceUseCase delega en el repositorio`() = runTest {
        val esperado = AppearancePreferences(ThemePreference.DARK, ColorPreference.DYNAMIC)
        val repo = FakeAppearanceRepository(flowOf(esperado))

        val resultado = ObservarAppearanceUseCase(repo)()

        assertEquals(esperado, resultado.first())
    }

    @Test
    fun `ActualizarTemaUseCase delega en el repositorio`() = runTest {
        val repo = FakeAppearanceRepository(flowOf(AppearancePreferences()))

        ActualizarTemaUseCase(repo)(ThemePreference.DARK)

        assertEquals(listOf(ThemePreference.DARK), repo.temasActualizados)
    }

    @Test
    fun `ActualizarColorUseCase delega en el repositorio`() = runTest {
        val repo = FakeAppearanceRepository(flowOf(AppearancePreferences()))

        ActualizarColorUseCase(repo)(ColorPreference.DYNAMIC)

        assertEquals(listOf(ColorPreference.DYNAMIC), repo.coloresActualizados)
    }
}

private class FakeAppearanceRepository(
    private val preferencias: Flow<AppearancePreferences>,
) : AppearancePreferencesRepository {
    override fun observarPreferencias(): Flow<AppearancePreferences> = preferencias
    val temasActualizados = mutableListOf<ThemePreference>()
    val coloresActualizados = mutableListOf<ColorPreference>()

    override suspend fun actualizarTema(theme: ThemePreference) {
        temasActualizados += theme
    }

    override suspend fun actualizarColor(color: ColorPreference) {
        coloresActualizados += color
    }
}
