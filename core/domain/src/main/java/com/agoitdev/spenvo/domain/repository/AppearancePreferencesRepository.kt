package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow

interface AppearancePreferencesRepository {
    fun observarPreferencias(): Flow<AppearancePreferences>

    suspend fun actualizarTema(theme: ThemePreference)

    suspend fun actualizarColor(color: ColorPreference)
}
