package com.agoitdev.spenvo.cuenta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.usecase.VincularCredencialUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CuentaViewModel @Inject constructor(
    private val vincularCredencial: VincularCredencialUseCase,
    authRepository: AuthRepository,
) : ViewModel() {

    val sesion: StateFlow<Sesion> = authRepository.observeSesion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sesion.Anonima)

    private val _estado = MutableStateFlow(RegistroEstado())
    val estado: StateFlow<RegistroEstado> = _estado.asStateFlow()

    fun registrar(nombre: String, email: String, password: String) {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            runCatching { vincularCredencial(email = email, password = password, nombre = nombre) }
                .onSuccess { _estado.value = RegistroEstado(completado = true) }
                .onFailure { error ->
                    _estado.value = RegistroEstado(error = error.message)
                }
        }
    }

    fun consumirError() {
        _estado.update { it.copy(error = null) }
    }
}

data class RegistroEstado(
    val cargando: Boolean = false,
    val completado: Boolean = false,
    val error: String? = null,
)
