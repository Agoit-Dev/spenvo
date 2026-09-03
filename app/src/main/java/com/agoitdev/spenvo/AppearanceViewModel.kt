package com.agoitdev.spenvo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.designsystem.theme.ColorMode
import com.agoitdev.spenvo.designsystem.theme.ThemeMode
import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import com.agoitdev.spenvo.domain.usecase.ObservarAppearanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface AppearanceUiState {
    data object Loading : AppearanceUiState

    data class Ready(val themeMode: ThemeMode, val colorMode: ColorMode) : AppearanceUiState
}

private fun AppearancePreferences.aDesignSystem(): AppearanceUiState.Ready {
    val themeMode = when (theme) {
        ThemePreference.SYSTEM -> ThemeMode.SYSTEM
        ThemePreference.LIGHT -> ThemeMode.LIGHT
        ThemePreference.DARK -> ThemeMode.DARK
    }
    val colorMode = when (color) {
        ColorPreference.BRAND -> ColorMode.BRAND
        ColorPreference.DYNAMIC -> ColorMode.DYNAMIC
    }
    return AppearanceUiState.Ready(themeMode, colorMode)
}

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    observarAppearance: ObservarAppearanceUseCase,
) : ViewModel() {

    val estado: StateFlow<AppearanceUiState> = observarAppearance()
        .map { it.aDesignSystem() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceUiState.Loading)
}
