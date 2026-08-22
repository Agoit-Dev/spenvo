package com.agoitdev.spenvo.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.data.remote.sync.MovimientoSincronizacion
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.Movimiento
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.usecase.CrearGastoRequest
import com.agoitdev.spenvo.domain.usecase.CrearGastoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearIngresoRequest
import com.agoitdev.spenvo.domain.usecase.CrearIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasPorTipoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarMovimientosUseCase
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
class MovimientosViewModel @Inject constructor(
    private val observarMovimientos: ObservarMovimientosUseCase,
    private val observarCategoriasPorTipo: ObservarCategoriasPorTipoUseCase,
    private val observarCategorias: ObservarCategoriasUseCase,
    private val crearGasto: CrearGastoUseCase,
    private val crearIngreso: CrearIngresoUseCase,
    private val sincronizador: MovimientoSincronizacion,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val planIdActivo = MutableStateFlow<String?>(null)

    private val _estadoForm = MutableStateFlow(MovimientoFormEstado())
    val estadoForm: StateFlow<MovimientoFormEstado> = _estadoForm.asStateFlow()

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

    fun consumirError() {
        _estadoForm.update { it.copy(error = null) }
    }

    fun consumirGuardado() {
        _estadoForm.update { it.copy(guardado = false) }
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
)
