package com.agoitdev.spenvo.categorias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.data.remote.sync.CategoriaSincronizacion
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.usecase.ActualizarCategoriaUseCase
import com.agoitdev.spenvo.domain.usecase.CrearCategoriaRequest
import com.agoitdev.spenvo.domain.usecase.CrearCategoriaUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarCategoriaUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasPorTipoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
class CategoriasViewModel @Suppress("LongParameterList") @Inject constructor(
    private val observarCategoriasPorTipo: ObservarCategoriasPorTipoUseCase,
    private val crearCategoria: CrearCategoriaUseCase,
    private val actualizarCategoria: ActualizarCategoriaUseCase,
    private val eliminarCategoria: EliminarCategoriaUseCase,
    private val sincronizador: CategoriaSincronizacion,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val planIdActivo = MutableStateFlow<String?>(null)

    private val _estadoForm = MutableStateFlow(CategoriaFormEstado())
    val estadoForm: StateFlow<CategoriaFormEstado> = _estadoForm.asStateFlow()

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

    fun categorias(planId: String, tipo: TipoCategoria): StateFlow<List<Categoria>> =
        observarCategoriasPorTipo(planId, tipo)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), emptyList())

    fun crear(planId: String, nombre: String, tipo: TipoCategoria, icono: String) {
        _estadoForm.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            runCatching {
                crearCategoria(CrearCategoriaRequest(planId = planId, nombre = nombre, tipo = tipo, icono = icono))
            }
                .onSuccess { _estadoForm.value = CategoriaFormEstado(guardado = true) }
                .onFailure { e -> _estadoForm.value = CategoriaFormEstado(error = e.message) }
        }
    }

    fun actualizar(categoria: Categoria) {
        _estadoForm.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            val editorId = authRepository.observeSesion().first().uid.orEmpty()
            runCatching { actualizarCategoria(categoria, editorId) }
                .onSuccess { _estadoForm.value = CategoriaFormEstado(guardado = true) }
                .onFailure { e -> _estadoForm.value = CategoriaFormEstado(error = e.message) }
        }
    }

    fun eliminar(categoria: Categoria) {
        _estadoForm.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            val editorId = authRepository.observeSesion().first().uid.orEmpty()
            runCatching { eliminarCategoria(categoria, editorId) }
                .onSuccess { _estadoForm.value = CategoriaFormEstado(guardado = true) }
                .onFailure { e -> _estadoForm.value = CategoriaFormEstado(error = e.message) }
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

data class CategoriaFormEstado(
    val guardando: Boolean = false,
    val guardado: Boolean = false,
    val error: String? = null,
)
