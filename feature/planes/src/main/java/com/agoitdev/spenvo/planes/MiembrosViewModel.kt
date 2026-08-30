package com.agoitdev.spenvo.planes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.model.MiembroResuelto
import com.agoitdev.spenvo.domain.model.Rol
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import com.agoitdev.spenvo.domain.usecase.InvitarMiembroUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MiembrosViewModel @Inject constructor(
    private val accesosRepository: AccesoPlanRepository,
    private val invitarMiembro: InvitarMiembroUseCase,
    private val usuarioRepository: UsuarioRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

fun miembrosResueltos(planId: String): StateFlow<List<MiembroResuelto>> =
        accesosRepository.observarAccesosDelPlan(planId)
            .map { accesos ->
                val usuarios = runCatching { usuarioRepository.obtenerVarios(accesos.map { it.usuarioId }) }
                    .getOrDefault(emptyList())
                    .associateBy { it.id }
                accesos.map { acceso -> MiembroResuelto(acceso, usuarios[acceso.usuarioId]) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), emptyList())

    private val _estadoInvitar = MutableStateFlow(InvitarEstado())
    val estadoInvitar: StateFlow<InvitarEstado> = _estadoInvitar.asStateFlow()

    fun invitar(planId: String, identificador: String, rol: Rol) {
        if (identificador.isBlank()) {
            _estadoInvitar.value = InvitarEstado(error = "El nombre de usuario o email es obligatorio")
            return
        }
        _estadoInvitar.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val invitadoPor = authRepository.observeSesion().first().uid.orEmpty()
            runCatching {
                invitarMiembro(
                    planId = planId,
                    identificador = identificador.trim(),
                    rol = rol,
                    invitadoPor = invitadoPor,
                )
            }
                // Generic confirmation regardless of resolution outcome (anti-enumeration): the use
                // case itself never distinguishes "resolved" from "not resolved" via an exception,
                // only real Firestore failures land in onFailure below.
                .onSuccess { _estadoInvitar.value = InvitarEstado(invitado = true) }
                .onFailure { e -> _estadoInvitar.value = InvitarEstado(error = e.message) }
        }
    }

    fun consumirError() {
        _estadoInvitar.update { it.copy(error = null) }
    }

fun consumirInvitado() {
        _estadoInvitar.update { it.copy(invitado = false) }
    }

    private companion object {
        const val WHILE_SUBSCRIBED_TIMEOUT_MS = 5_000L
    }
}

data class InvitarEstado(
    val cargando: Boolean = false,
    val invitado: Boolean = false,
    val error: String? = null,
)
