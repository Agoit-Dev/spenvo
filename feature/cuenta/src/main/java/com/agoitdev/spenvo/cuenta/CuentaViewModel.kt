package com.agoitdev.spenvo.cuenta

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import com.agoitdev.spenvo.domain.usecase.AsegurarUsuarioUseCase
import com.agoitdev.spenvo.domain.usecase.EnviarRecuperacionPasswordUseCase
import com.agoitdev.spenvo.domain.usecase.IniciarSesionConEmailUseCase
import com.agoitdev.spenvo.domain.usecase.RenombrarUsuarioUseCase
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
class CuentaViewModel @Suppress("LongParameterList") @Inject constructor(
    private val vincularCredencial: VincularCredencialUseCase,
    private val iniciarSesionConEmail: IniciarSesionConEmailUseCase,
    private val enviarRecuperacionPassword: EnviarRecuperacionPasswordUseCase,
    private val authRepository: AuthRepository,
    private val usuarioRepository: UsuarioRepository,
    private val subirAvatarUseCase: SubirAvatarUseCase,
    private val asegurarUsuario: AsegurarUsuarioUseCase,
    private val renombrarUsuario: RenombrarUsuarioUseCase,
) : ViewModel() {

    val sesion: StateFlow<Sesion> = authRepository.observeSesion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sesion.Anonima)

    private val _estado = MutableStateFlow(RegistroEstado())
    val estado: StateFlow<RegistroEstado> = _estado.asStateFlow()

    private val _perfilEstado = MutableStateFlow(PerfilEstado())
    val perfilEstado: StateFlow<PerfilEstado> = _perfilEstado.asStateFlow()

    private val _recoveryEstado = MutableStateFlow(RecoveryEstado())
    val recoveryEstado: StateFlow<RecoveryEstado> = _recoveryEstado.asStateFlow()

    init {
        viewModelScope.launch {
            sesion.collect { sesionActual ->
                val uid = sesionActual.uid
                if (!sesionActual.esAnonima && uid != null) {
                    // A failed lookup (network/permission) must not crash the app nor kill this
                    // collector for the ViewModel's lifetime — just skip this emission; a future
                    // sesion re-emission gets another chance.
                    runCatching { usuarioRepository.obtener(uid) }
                        .onSuccess { usuario ->
                            _perfilEstado.update { it.copy(nombreUsuario = usuario?.nombreUsuario) }
                        }
                } else {
                    _perfilEstado.update { it.copy(nombreUsuario = null, nombreUsuarioError = null) }
                }
            }
        }
    }

    fun registrar(nombre: String, email: String, password: String) {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            runCatching {
                vincularCredencial(email = email, password = password, nombre = nombre)
                val uid = sesion.value.uid ?: error("sin uid tras vincular")
                asegurarUsuario.paraVincularEmail(usuarioId = uid, nombre = nombre, email = email)
            }
                .onSuccess { _estado.value = RegistroEstado(completado = true) }
                .onFailure { error ->
                    _estado.value = RegistroEstado(error = error.message)
                }
        }
    }

    fun consumirError() {
        _estado.update { it.copy(error = null, errorRes = null) }
    }

    fun iniciarSesion(email: String, password: String) {
        _estado.update { it.copy(cargando = true, error = null, errorRes = null) }
        viewModelScope.launch {
            runCatching { iniciarSesionConEmail(email, password) }
                .onSuccess { _estado.value = RegistroEstado(completado = true) }
                .onFailure { error ->
                    _estado.value = RegistroEstado(errorRes = mapearErrorAuth(error))
                }
        }
    }

    /**
     * El mismo resultado visible tanto si el email existe como si no — evita que la UI
     * permita enumerar cuentas registradas probando direcciones.
     */
    fun recuperarPassword(email: String) {
        viewModelScope.launch {
            runCatching { enviarRecuperacionPassword(email) }
            _recoveryEstado.value = RecoveryEstado(exito = true)
        }
    }

    fun consumirRecoveryEstado() {
        _recoveryEstado.value = RecoveryEstado()
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

    /**
     * [nuevo] se recorta antes de nada: el valor visible que termina en
     * `usuarios/{uid}.nombreUsuario` tiene que coincidir con la reserva
     * `nombres_usuario/{normalizado}` salvo por mayúsculas, porque la regla de Firestore
     * re-deriva el id de la reserva con `.lower()` y el lenguaje de reglas no tiene `trim()`.
     */
    fun editarNombreUsuario(nuevo: String) {
        val uid = sesion.value.uid ?: return
        val anterior = _perfilEstado.value.nombreUsuario ?: return
        val nuevoVisible = nuevo.trim()
        if (nuevoVisible.isEmpty()) {
            _perfilEstado.update { it.copy(nombreUsuarioError = R.string.account_profile_nombre_usuario_vacio) }
        } else {
            viewModelScope.launch {
                runCatching {
                    renombrarUsuario(
                        usuarioId = uid,
                        nombreUsuarioAnterior = anterior,
                        nombreUsuarioNuevo = nuevoVisible,
                    )
                }
                    .onSuccess { exito ->
                        _perfilEstado.update {
                            if (exito) it.copy(nombreUsuario = nuevoVisible, nombreUsuarioError = null)
                            else it.copy(nombreUsuarioError = R.string.account_profile_nombre_usuario_en_uso)
                        }
                    }
                    .onFailure {
                        _perfilEstado.update {
                            it.copy(nombreUsuarioError = R.string.account_profile_nombre_usuario_error)
                        }
                    }
            }
        }
    }
}

data class RegistroEstado(
    val cargando: Boolean = false,
    val completado: Boolean = false,
    val error: String? = null,
    @param:StringRes val errorRes: Int? = null,
)

data class RecoveryEstado(
    val exito: Boolean = false,
)

data class PerfilEstado(
    val subiendoAvatar: Boolean = false,
    val avatarError: String? = null,
    val nombreUsuario: String? = null,
    /**
     * Recurso traducible, no texto: un ViewModel no es un Composable, así que no puede
     * llamar a `stringResource()` — resuelve `CuentaScreen`. Mantiene los mensajes de UI
     * dentro de `values/`+`values-en/` como exige AGENTS.md (lint solo audita XML).
     */
    @param:StringRes val nombreUsuarioError: Int? = null,
)
