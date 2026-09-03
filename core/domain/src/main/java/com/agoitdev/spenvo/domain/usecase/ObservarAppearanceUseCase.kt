package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository
import kotlinx.coroutines.flow.Flow

class ObservarAppearanceUseCase(
    private val appearanceRepository: AppearancePreferencesRepository,
) {
    operator fun invoke(): Flow<AppearancePreferences> = appearanceRepository.observarPreferencias()
}
