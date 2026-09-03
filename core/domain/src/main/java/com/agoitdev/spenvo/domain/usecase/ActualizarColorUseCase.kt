package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository

class ActualizarColorUseCase(
    private val appearanceRepository: AppearancePreferencesRepository,
) {
    suspend operator fun invoke(color: ColorPreference) = appearanceRepository.actualizarColor(color)
}
