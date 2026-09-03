package com.agoitdev.spenvo.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import com.agoitdev.spenvo.domain.usecase.ActualizarColorUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarTemaUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarAppearanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AjustesUiState(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val color: ColorPreference = ColorPreference.BRAND,
    val errorGuardado: Boolean = false,
)

@HiltViewModel
class AjustesViewModel @Inject constructor(
    observarAppearance: ObservarAppearanceUseCase,
    private val actualizarTema: ActualizarTemaUseCase,
    private val actualizarColor: ActualizarColorUseCase,
) : ViewModel() {

    private val errorGuardado = MutableStateFlow(false)

    val estado: StateFlow<AjustesUiState> = combine(
        observarAppearance(),
        errorGuardado,
    ) { preferencias, error ->
        AjustesUiState(theme = preferencias.theme, color = preferencias.color, errorGuardado = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AjustesUiState())

    fun seleccionarTema(theme: ThemePreference) {
        viewModelScope.launch {
            runCatching { actualizarTema(theme) }.onFailure { errorGuardado.value = true }
        }
    }

    fun seleccionarColor(color: ColorPreference) {
        viewModelScope.launch {
            runCatching { actualizarColor(color) }.onFailure { errorGuardado.value = true }
        }
    }

    fun consumirErrorGuardado() {
        errorGuardado.value = false
    }
}
