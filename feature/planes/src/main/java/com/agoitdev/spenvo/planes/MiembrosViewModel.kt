package com.agoitdev.spenvo.planes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.Rol
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.usecase.InvitarMiembroUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MiembrosViewModel @Inject constructor(
    private val accesosRepository: AccesoPlanRepository,
    private val invitarMiembro: InvitarMiembroUseCase,
) : ViewModel() {

fun observarMiembros(planId: String): StateFlow<List<AccesoPlan>> =
        accesosRepository.observarAccesosDelPlan(planId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), emptyList())

    private val _estadoInvitar = MutableStateFlow(InvitarEstado())
    val estadoInvitar: StateFlow<InvitarEstado> = _estadoInvitar.asStateFlow()

    fun invitar(planId: String, usuarioId: String, rol: Rol) {
        if (usuarioId.isBlank()) {
            _estadoInvitar.value = InvitarEstado(error = "El UID del usuario es obligatorio")
            return
        }
        _estadoInvitar.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            runCatching { invitarMiembro(planId = planId, usuarioId = usuarioId.trim(), rol = rol) }
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
