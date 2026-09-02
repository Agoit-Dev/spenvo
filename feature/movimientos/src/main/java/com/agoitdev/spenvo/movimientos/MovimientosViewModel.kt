package com.agoitdev.spenvo.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.data.remote.sync.MovimientoSincronizacion
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.Movimiento
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.usecase.ActualizarGastoUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.AplicarGastoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.AplicarIngresoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearGastoRequest
import com.agoitdev.spenvo.domain.usecase.CrearGastoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearIngresoRequest
import com.agoitdev.spenvo.domain.usecase.CrearIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarGastoUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasPorTipoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarMovimientosUseCase
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoGastoUsandoLocalUseCase
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoGastoUsandoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoIngresoUsandoLocalUseCase
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoIngresoUsandoRemotoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
@Suppress("TooManyFunctions")
class MovimientosViewModel @Suppress("LongParameterList") @Inject constructor(
    private val observarMovimientos: ObservarMovimientosUseCase,
    private val observarCategoriasPorTipo: ObservarCategoriasPorTipoUseCase,
    private val observarCategorias: ObservarCategoriasUseCase,
    private val crearGasto: CrearGastoUseCase,
    private val crearIngreso: CrearIngresoUseCase,
    private val actualizarGasto: ActualizarGastoUseCase,
    private val eliminarGasto: EliminarGastoUseCase,
    private val actualizarIngreso: ActualizarIngresoUseCase,
    private val eliminarIngreso: EliminarIngresoUseCase,
    // Superseded by resolverConflictoGastoUsandoRemoto/resolverConflictoIngresoUsandoRemoto below
    // for conflict resolution; kept as constructor params (ARCH-M501, Task 8) since Hilt still
    // wires them and no other call site in this ViewModel needs them removed out of sequence.
    @Suppress("UnusedPrivateProperty") private val aplicarGastoRemoto: AplicarGastoRemotoUseCase,
    @Suppress("UnusedPrivateProperty") private val aplicarIngresoRemoto: AplicarIngresoRemotoUseCase,
    private val resolverConflictoGastoUsandoLocal: ResolverConflictoGastoUsandoLocalUseCase,
    private val resolverConflictoIngresoUsandoLocal: ResolverConflictoIngresoUsandoLocalUseCase,
    private val resolverConflictoGastoUsandoRemoto: ResolverConflictoGastoUsandoRemotoUseCase,
    private val resolverConflictoIngresoUsandoRemoto: ResolverConflictoIngresoUsandoRemotoUseCase,
    private val sincronizador: MovimientoSincronizacion,
    private val authRepository: AuthRepository,
    private val registroConflictosPendientes: RegistroConflictosPendientes,
) : ViewModel() {

    private val planIdActivo = MutableStateFlow<String?>(null)

    private val _estadoForm = MutableStateFlow(MovimientoFormEstado())
    val estadoForm: StateFlow<MovimientoFormEstado> = _estadoForm.asStateFlow()

    /** Row-level conflict badges (Slice 5b): keyed `"$coleccion:$id"`, see [RegistroConflictosPendientes]. */
    val conflictos: StateFlow<Map<String, ConflictoEdicion>> = registroConflictosPendientes.conflictos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), emptyMap())

    private val _conflictoVisible = MutableStateFlow<ConflictoEdicion?>(null)

    /** Non-null only after [mostrarConflicto] — the modal is deferred until the user reopens the record. */
    val conflictoVisible: StateFlow<ConflictoEdicion?> = _conflictoVisible.asStateFlow()

    init {
        viewModelScope.launch {
            planIdActivo.filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest { planId -> sincronizador.sincronizar(planId) }
                .catch { /* best-effort sync: un error de red/rules no debe tumbar la app */ }
                .collect { }
        }
    }

    fun sincronizar(planId: String) {
        planIdActivo.value = planId
    }

    fun movimientos(planId: String): StateFlow<List<Movimiento>> =
        observarMovimientos(planId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), emptyList())

    fun categorias(planId: String, tipo: TipoCategoria): StateFlow<List<Categoria>> =
        observarCategoriasPorTipo(planId, tipo)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), emptyList())

    fun categoriasTodas(planId: String): StateFlow<List<Categoria>> =
        observarCategorias(planId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), emptyList())

    fun guardar(datos: MovimientoFormDatos) {
        _estadoForm.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            val creadoPor = authRepository.observeSesion().first().uid.orEmpty()
            runCatching {
                when (datos.tipo) {
                    TipoCategoria.GASTO -> crearGasto(
                        CrearGastoRequest(
                            datos.planId,
                            datos.categoriaId,
                            datos.monto,
                            datos.fecha,
                            datos.descripcion,
                            creadoPor,
                        ),
                    )
                    TipoCategoria.INGRESO -> crearIngreso(
                        CrearIngresoRequest(
                            datos.planId,
                            datos.categoriaId,
                            datos.monto,
                            datos.fecha,
                            datos.descripcion,
                            creadoPor,
                        ),
                    )
                }
            }
                .onSuccess { _estadoForm.value = MovimientoFormEstado(guardado = true) }
                .onFailure { e -> _estadoForm.value = MovimientoFormEstado(error = e.message) }
        }
    }

    fun actualizar(movimiento: Movimiento, alTerminar: () -> Unit = {}) {
        _estadoForm.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            val editorId = authRepository.observeSesion().first().uid.orEmpty()
            runCatching {
                when (movimiento) {
                    is Gasto -> actualizarGasto(movimiento, editorId)
                    is Ingreso -> actualizarIngreso(movimiento, editorId)
                }
            }
                .onSuccess {
                    _estadoForm.value = MovimientoFormEstado(guardado = true)
                    alTerminar()
                }
                .onFailure { e -> _estadoForm.value = MovimientoFormEstado(error = e.message) }
        }
    }

    fun eliminar(movimiento: Movimiento) {
        _estadoForm.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            val editorId = authRepository.observeSesion().first().uid.orEmpty()
            runCatching {
                when (movimiento) {
                    is Gasto -> eliminarGasto(movimiento, editorId)
                    is Ingreso -> eliminarIngreso(movimiento, editorId)
                }
            }
                .onSuccess { _estadoForm.value = MovimientoFormEstado(guardado = true) }
                .onFailure { e -> _estadoForm.value = MovimientoFormEstado(error = e.message) }
        }
    }

    /** Opens the conflict dialog for [movimiento] if one is pending, or closes it when null. Never an interrupt. */
    fun mostrarConflicto(movimiento: Movimiento?) {
        _conflictoVisible.value = movimiento?.let { m ->
            claveVisible(m)?.let { conflictos.value[it] }
        }
    }

    /**
     * Resolves the conflict for [movimiento]. `usarLocal` re-issues it as a fresh edit ("usar la mía" /
     * "restaurar mi edición"); otherwise the remote version wins verbatim ("usar la suya" / "mantener
     * borrado"). Only clears the dialog on success — a failed resolution (e.g. the remote document was
     * deleted in the meantime) surfaces through the same `_estadoForm.error` channel every other
     * action already uses, and the dialog stays open so the user can retry.
     */
    fun resolverConflicto(movimiento: Movimiento, usarLocal: Boolean) {
        val clave = claveVisible(movimiento) ?: return
        viewModelScope.launch {
            val editorId = authRepository.observeSesion().first().uid.orEmpty()
            runCatching {
                if (usarLocal) {
                    when (movimiento) {
                        is Gasto -> resolverConflictoGastoUsandoLocal(movimiento, editorId, clave)
                        is Ingreso -> resolverConflictoIngresoUsandoLocal(movimiento, editorId, clave)
                    }
                } else {
                    when (movimiento) {
                        is Gasto -> resolverConflictoGastoUsandoRemoto(movimiento.id, clave)
                        is Ingreso -> resolverConflictoIngresoUsandoRemoto(movimiento.id, clave)
                    }
                }
            }
                .onSuccess { _conflictoVisible.value = null }
                .onFailure { e -> _estadoForm.update { it.copy(error = e.message) } }
        }
    }

    /**
     * By-`(tipo, registroId)` lookup, unambiguous unlike the retired `conflictoPorRegistro`/
     * `resolverPorRegistro` (which resolved by `registroId` alone — ambiguous if a Gasto and an
     * Ingreso ever shared an id).
     */
    private fun claveVisible(movimiento: Movimiento): String? {
        val tipo = if (movimiento is Gasto) TipoRegistro.GASTO else TipoRegistro.INGRESO
        return conflictos.value.entries
            .firstOrNull { it.value.registroId == movimiento.id && it.value.tipo == tipo }
            ?.key
    }

    /** Clears one-shot form flags after they've been consumed (snackbar shown, navigation triggered). */
    fun consumir(error: Boolean = false, guardado: Boolean = false) {
        if (error) _estadoForm.update { it.copy(error = null) }
        if (guardado) _estadoForm.update { it.copy(guardado = false) }
    }

    private companion object {
        const val WHILE_SUBSCRIBED_TIMEOUT_MS = 5_000L
    }
}

data class MovimientoFormEstado(
    val guardando: Boolean = false,
    val guardado: Boolean = false,
    val error: String? = null,
)

data class MovimientoFormDatos(
    val planId: String,
    val tipo: TipoCategoria,
    val categoriaId: String,
    val monto: Monto,
    val fecha: LocalDate,
    val descripcion: String?,
)

internal data class MovimientoFormAcciones(
    val onGuardar: (MovimientoFormDatos) -> Unit,
    val onDismiss: () -> Unit,
    val onEliminar: (() -> Unit)? = null,
)
