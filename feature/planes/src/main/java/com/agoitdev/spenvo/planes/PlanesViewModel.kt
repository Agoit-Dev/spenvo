package com.agoitdev.spenvo.planes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.InvitacionEstado
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.usecase.AceptarInvitacionUseCase
import com.agoitdev.spenvo.domain.usecase.CrearPlanRequest
import com.agoitdev.spenvo.domain.usecase.CrearPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanesDelUsuarioUseCase
import com.agoitdev.spenvo.data.remote.sync.PlanSincronizador
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanesViewModel @Inject constructor(
    private val crearPlan: CrearPlanUseCase,
    private val observarPlanes: ObservarPlanesDelUsuarioUseCase,
    private val aceptarInvitacion: AceptarInvitacionUseCase,
    private val sincronizador: PlanSincronizador,
    private val accesosRepository: AccesoPlanRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val sesion: StateFlow<Sesion> = authRepository.observeSesion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sesion.Anonima)

    val planes: StateFlow<List<PlanFinanciero>> = sesion.flatMapLatest { s ->
        val uid = s.uid
        if (uid == null) flowOf(emptyList()) else observarPlanes(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val invitacionesPendientes: StateFlow<List<AccesoPlan>> = sesion.flatMapLatest { s ->
        val uid = s.uid
        if (uid == null) {
            flowOf(emptyList())
        } else {
            accesosRepository.observarAccesosDelUsuario(uid).map { accesos ->
                accesos.filter { it.invitacionEstado == InvitacionEstado.PENDIENTE }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _estadoCrear = MutableStateFlow(CrearPlanEstado())
    val estadoCrear: StateFlow<CrearPlanEstado> = _estadoCrear.asStateFlow()

init {
        viewModelScope.launch {
            while (true) {
                runCatching { authRepository.iniciarSesionAnonima() }
                    .onSuccess { break }
                delay(RETRY_DELAY_MS)
            }
        }
        viewModelScope.launch {
            sesion.filter { it.uid != null }.flatMapLatest { s ->
                val uid = s.uid
                if (uid == null) flowOf(Unit) else sincronizador.sincronizar(uid)
            }
                .catch { /* best-effort sync: un error de red/rules no debe tumbar la app */ }
                .collect { }
        }
    }

    fun crearPlan(nombre: String, moneda: String) {
        if (nombre.isBlank() || moneda.isBlank()) {
            _estadoCrear.value = CrearPlanEstado(error = "Nombre y moneda son obligatorios")
            return
        }
        val uid = sesion.value.uid ?: return
        _estadoCrear.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            runCatching {
                crearPlan(CrearPlanRequest(nombre = nombre, moneda = moneda, creadoPor = uid))
            }
                .onSuccess { _estadoCrear.value = CrearPlanEstado(creado = true) }
                .onFailure { e -> _estadoCrear.value = CrearPlanEstado(error = e.message) }
        }
    }

    fun aceptarInvitacion(planId: String) {
        val uid = sesion.value.uid ?: return
        viewModelScope.launch {
            runCatching { aceptarInvitacion(usuarioId = uid, planId = planId) }
        }
    }

    fun consumirError() {
        _estadoCrear.update { it.copy(error = null) }
    }

    fun consumirCreado() {
        _estadoCrear.update { it.copy(creado = false) }
    }

    private companion object {
        const val RETRY_DELAY_MS = 30_000L
    }
}

data class CrearPlanEstado(
    val cargando: Boolean = false,
    val creado: Boolean = false,
    val error: String? = null,
)
