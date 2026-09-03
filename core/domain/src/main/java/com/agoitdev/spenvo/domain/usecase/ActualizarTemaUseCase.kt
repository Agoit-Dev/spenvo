package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.ThemePreference
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository

class ActualizarTemaUseCase(
    private val appearanceRepository: AppearancePreferencesRepository,
) {
    suspend operator fun invoke(theme: ThemePreference) = appearanceRepository.actualizarTema(theme)
}
