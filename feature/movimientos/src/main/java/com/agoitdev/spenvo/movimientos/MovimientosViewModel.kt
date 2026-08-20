package com.agoitdev.spenvo.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.usecase.IniciarSesionAnonimaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MovimientosViewModel @Inject constructor(
    private val iniciarSesionAnonima: IniciarSesionAnonimaUseCase,
    authRepository: AuthRepository,
) : ViewModel() {

    val sesion: StateFlow<Sesion> = authRepository.observeSesion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sesion.Anonima)

init {
        viewModelScope.launch {
            while (true) {
                runCatching { iniciarSesionAnonima() }
                    .onSuccess { return@launch }
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private companion object {
        const val RETRY_DELAY_MS = 30_000L
    }
}

