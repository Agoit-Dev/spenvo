package com.agoitdev.spenvo.planes

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.model.MiembroResuelto
import com.agoitdev.spenvo.domain.model.Rol
import com.agoitdev.spenvo.domain.model.esAlMenos
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
import kotlinx.coroutines.flow.combine
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

    /**
     * FEAT-U701: whether the current session's own role in this plan is admin+ — gates the
     * "Invite" action in the UI. Server-side, `firestore.rules` already rejects the write for
     * anyone below admin; this only controls whether the button is offered at all. Reuses the
     * same `observarAccesosDelPlan` list `miembrosResueltos` already collects, no extra query.
     * Defaults to `false` (hidden) until the current uid resolves in that list — deny-by-default,
     * matching this app's general security posture.
     */
    fun puedeInvitar(planId: String): StateFlow<Boolean> =
        combine(accesosRepository.observarAccesosDelPlan(planId), authRepository.observeSesion()) { accesos, sesion ->
            accesos.firstOrNull { it.usuarioId == sesion.uid }?.rol?.esAlMenos(Rol.ADMIN) == true
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), false)

    private val _estadoInvitar = MutableStateFlow(InvitarEstado())
    val estadoInvitar: StateFlow<InvitarEstado> = _estadoInvitar.asStateFlow()

    fun invitar(planId: String, identificador: String, rol: Rol) {
        if (identificador.isBlank()) {
            _estadoInvitar.value = InvitarEstado(errorRes = R.string.members_invite_identificador_requerido)
            return
        }
        _estadoInvitar.update { it.copy(cargando = true, error = null, errorRes = null) }
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
        _estadoInvitar.update { it.copy(error = null, errorRes = null) }
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
    /** Mensaje ya resuelto de un fallo real (Firestore/red), no una string de UI propia. */
    val error: String? = null,
    /**
     * Error de validación propio de la UI, como recurso traducible: un ViewModel no puede
     * llamar a `stringResource()`, así que lo resuelve `MiembrosScreen`. Mantiene el mensaje
     * en `values/`+`values-en/` como exige AGENTS.md (lint solo audita XML).
     */
    @param:StringRes val errorRes: Int? = null,
)
