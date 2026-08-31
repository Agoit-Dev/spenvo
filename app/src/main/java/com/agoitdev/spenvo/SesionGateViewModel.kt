package com.agoitdev.spenvo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class EstadoGate { Cargando, MostrarApp, MostrarGate }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SesionGateViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @LogoutExplicitoFlag flagLogoutExplicito: Flow<Boolean>,
    private val limpiarLogoutExplicito: LimpiarLogoutExplicito,
) : ViewModel() {

    /**
     * Single-flight handle over anonymous bootstrap: both the fresh-install path and an explicit
     * "continuar como invitado" go through it, so a retry in progress is never duplicated into two
     * concurrent `signInAnonymously()` calls (which would create two anonymous accounts).
     */
    private var bootstrapAnonimo: Job? = null

    val estado: StateFlow<EstadoGate> = combine(
        authRepository.observeSesion(),
        flagLogoutExplicito,
    ) { sesion, flagPendiente -> sesion to flagPendiente }
        .distinctUntilChanged()
        .flatMapLatest { (sesion, flagPendiente) ->
            when {
                sesion.uid != null -> flowOf(EstadoGate.MostrarApp)
                flagPendiente -> flowOf(EstadoGate.MostrarGate)
                else -> flowOf(EstadoGate.MostrarApp).also { lanzarBootstrapAnonimo() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoGate.Cargando)

    /**
     * The current session's avatar photo, for the "open my account" action every screen exposes
     * (front 3 of the auth/identity series). `null` covers both an anonymous session and a
     * registered user who never uploaded a photo — [AvatarTopBarAction] falls back to a generic
     * icon either way.
     */
    val avatarUrl: StateFlow<String?> = authRepository.observeSesion()
        .map { it.photoUrl }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * "Continuar como invitado" from the post-logout gate: clears the persisted logout flag and
     * establishes a fresh anonymous session, mirroring what a successful sign-in does via
     * `FirebaseAuthRepository.iniciarSesionConEmail`. This is the only non-sign-in way out of
     * [EstadoGate.MostrarGate]; without it, logging out is a dead end for a user who can't sign in.
     */
    fun continuarComoInvitado() {
        bootstrapAnonimo?.cancel()
        bootstrapAnonimo = viewModelScope.launch {
            limpiarLogoutExplicito()
            asegurarSesionAnonima()
        }
    }

    private fun lanzarBootstrapAnonimo() {
        if (bootstrapAnonimo?.isActive == true) return
        bootstrapAnonimo = viewModelScope.launch { asegurarSesionAnonima() }
    }

    /**
     * Bounded retry around anonymous sign-in. A fresh install can legitimately fail here
     * (no network, or an App Check token exchange that hasn't settled yet), and this used to run
     * uncaught inside `viewModelScope.launch` — a single failure crashed the app. It replaces the
     * unbounded 30s retry loop that used to live in `PlanesViewModel.init`; bounded, because a
     * loop that never gives up keeps a dead session alive forever with no way to surface it.
     */
    private suspend fun asegurarSesionAnonima() {
        repeat(MAX_INTENTOS_ANONIMO) { intento ->
            val resultado = runCatching { authRepository.iniciarSesionAnonima() }
            if (resultado.isSuccess) return
            // runCatching swallows CancellationException too; rethrowing keeps viewModelScope
            // cancellation (and continuarComoInvitado's cancel()) working as structured concurrency.
            (resultado.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (intento < MAX_INTENTOS_ANONIMO - 1) delay(RETRY_DELAY_MS)
        }
    }

    companion object {
        const val MAX_INTENTOS_ANONIMO = 3
        private const val RETRY_DELAY_MS = 5_000L
    }
}
