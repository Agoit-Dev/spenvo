package com.agoitdev.spenvo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class EstadoGate { Cargando, MostrarApp, MostrarGate }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SesionGateViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @LogoutExplicitoFlag flagLogoutExplicito: Flow<Boolean>,
) : ViewModel() {

    val estado: StateFlow<EstadoGate> = combine(
        authRepository.observeSesion(),
        flagLogoutExplicito,
    ) { sesion, flagPendiente -> sesion to flagPendiente }
        .distinctUntilChanged()
        .flatMapLatest { (sesion, flagPendiente) ->
            when {
                sesion.uid != null -> flowOf(EstadoGate.MostrarApp)
                flagPendiente -> flowOf(EstadoGate.MostrarGate)
                else -> flowOf(EstadoGate.MostrarApp).also {
                    viewModelScope.launch { authRepository.iniciarSesionAnonima() }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoGate.Cargando)
}
