package com.agoitdev.spenvo.cuenta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.usecase.SubirAvatarUseCase
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
    private val authRepository: AuthRepository,
    private val subirAvatarUseCase: SubirAvatarUseCase,
) : ViewModel() {

    val sesion: StateFlow<Sesion> = authRepository.observeSesion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sesion.Anonima)

    private val _estado = MutableStateFlow(RegistroEstado())
    val estado: StateFlow<RegistroEstado> = _estado.asStateFlow()

    private val _perfilEstado = MutableStateFlow(PerfilEstado())
    val perfilEstado: StateFlow<PerfilEstado> = _perfilEstado.asStateFlow()

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

    /** Uploads the linked user's avatar; bytes/contentType are read by the caller. */
    fun subirAvatar(bytes: ByteArray, contentType: String) {
        val uid = sesion.value.uid ?: return
        _perfilEstado.update { it.copy(subiendoAvatar = true, avatarError = null) }
        viewModelScope.launch {
            runCatching {
                val url = subirAvatarUseCase(uid, bytes, contentType)
                authRepository.actualizarPerfil(photoUrl = url)
            }.onSuccess {
                _perfilEstado.update { it.copy(subiendoAvatar = false) }
            }.onFailure { error ->
                _perfilEstado.update { it.copy(subiendoAvatar = false, avatarError = error.message) }
            }
        }
    }

    fun consumirAvatarError() {
        _perfilEstado.update { it.copy(avatarError = null) }
    }

    /** [AuthRepository.cerrarSesion] already re-establishes an anonymous session. */
    fun logout() {
        viewModelScope.launch { authRepository.cerrarSesion() }
    }
}

data class RegistroEstado(
    val cargando: Boolean = false,
    val completado: Boolean = false,
    val error: String? = null,
)

data class PerfilEstado(
    val subiendoAvatar: Boolean = false,
    val avatarError: String? = null,
)
