package com.agoitdev.spenvo

import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SesionGateViewModelTest {

    private val authRepository = FakeAuthRepositoryGate()
    private val flagLogoutExplicito = MutableStateFlow(false)
    private val limpiarLogout = FakeLimpiarLogoutExplicito()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel() =
        SesionGateViewModel(authRepository, flagLogoutExplicito, limpiarLogout)

    @Test
    fun `uid nulo y flag false dispara login anonimo y muestra MostrarApp`() = runTest {
        authRepository.sesionFlow.value = Sesion.Anonima
        flagLogoutExplicito.value = false
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()

        assertTrue(authRepository.anonimaLlamada)
        assertEquals(EstadoGate.MostrarApp, viewModel.estado.value)
        job.cancel()
    }

    @Test
    fun `uid nulo y flag true muestra MostrarGate sin recrear anonimo`() = runTest {
        authRepository.sesionFlow.value = Sesion.Anonima
        flagLogoutExplicito.value = true
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()

        assertEquals(false, authRepository.anonimaLlamada)
        assertEquals(EstadoGate.MostrarGate, viewModel.estado.value)
        job.cancel()
    }

    @Test
    fun `uid presente muestra MostrarApp sin importar el flag`() = runTest {
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = false)
        flagLogoutExplicito.value = true
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()

        assertEquals(false, authRepository.anonimaLlamada)
        assertEquals(EstadoGate.MostrarApp, viewModel.estado.value)
        job.cancel()
    }

    @Test
    fun `al cerrar sesion el estado pasa de MostrarApp a MostrarGate sin recrear anonimo`() = runTest {
        // The transition this ViewModel actually owns: a live session drops to uid == null while
        // the logout flag flips true. Persistence across process death is not observable here (the
        // flag is a plain in-memory flow in this test) — that is SesionPreferencesTest's job.
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = false)
        flagLogoutExplicito.value = false
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()
        assertEquals(EstadoGate.MostrarApp, viewModel.estado.value)

        flagLogoutExplicito.value = true
        authRepository.sesionFlow.value = Sesion.Anonima
        advanceUntilIdle()

        assertEquals(EstadoGate.MostrarGate, viewModel.estado.value)
        assertEquals(0, authRepository.anonimaLlamadaCount)
        job.cancel()
    }

    @Test
    fun `combine repetido con el mismo par sesion-flag solo dispara login anonimo una vez`() = runTest {
        // Reproduces a repeated AuthStateListener-style callback that re-emits the identical
        // (Sesion, Boolean) pair: without distinctUntilChanged() after combine(), flatMapLatest
        // (and its iniciarSesionAnonima() side-effect launch) would re-run on every re-emission
        // even though the combined state is structurally unchanged.
        val flagFlow = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 1)
        authRepository.sesionFlow.value = Sesion.Anonima
        flagFlow.tryEmit(false)
        val viewModel = SesionGateViewModel(authRepository, flagFlow, limpiarLogout)
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()

        assertEquals(1, authRepository.anonimaLlamadaCount)

        flagFlow.tryEmit(false) // same value re-emitted, unchanged (Sesion, Boolean) pair
        advanceUntilIdle()

        assertEquals(1, authRepository.anonimaLlamadaCount)
        job.cancel()
    }

    @Test
    fun `un fallo de iniciarSesionAnonima no propaga la excepcion y se reintenta`() = runTest {
        // Fresh install with a flaky network / App Check exchange: the bootstrap used to run
        // uncaught inside viewModelScope.launch, so the first failure crashed the app.
        authRepository.fallosAnonimaPendientes = 1
        authRepository.sesionFlow.value = Sesion.Anonima
        flagLogoutExplicito.value = false
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()

        assertEquals(2, authRepository.anonimaLlamadaCount)
        assertEquals(EstadoGate.MostrarApp, viewModel.estado.value)
        job.cancel()
    }

    @Test
    fun `si iniciarSesionAnonima falla siempre el reintento esta acotado y no crashea`() = runTest {
        authRepository.fallosAnonimaPendientes = Int.MAX_VALUE
        authRepository.sesionFlow.value = Sesion.Anonima
        flagLogoutExplicito.value = false
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()

        assertEquals(SesionGateViewModel.MAX_INTENTOS_ANONIMO, authRepository.anonimaLlamadaCount)
        job.cancel()
    }

    @Test
    fun `continuarComoInvitado limpia el flag de logout y establece una sesion anonima`() = runTest {
        authRepository.sesionFlow.value = Sesion.Anonima
        flagLogoutExplicito.value = true
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()
        assertEquals(EstadoGate.MostrarGate, viewModel.estado.value)

        viewModel.continuarComoInvitado()
        advanceUntilIdle()

        assertEquals(1, limpiarLogout.llamadas)
        assertEquals(1, authRepository.anonimaLlamadaCount)
        job.cancel()
    }

    @Test
    fun `continuarComoInvitado con un login anonimo fallido reintenta sin crashear`() = runTest {
        authRepository.fallosAnonimaPendientes = 1
        authRepository.sesionFlow.value = Sesion.Anonima
        flagLogoutExplicito.value = true
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()

        viewModel.continuarComoInvitado()
        advanceUntilIdle()

        assertEquals(1, limpiarLogout.llamadas)
        assertEquals(2, authRepository.anonimaLlamadaCount)
        job.cancel()
    }

    @Test
    fun `avatarUrl refleja el photoUrl de la sesion actual`() = runTest {
        authRepository.sesionFlow.value =
            Sesion(uid = "user-1", esAnonima = false, photoUrl = "https://example.com/avatar.jpg")
        val viewModel = crearViewModel()
        val job = launch { viewModel.avatarUrl.collect {} }
        advanceUntilIdle()

        assertEquals("https://example.com/avatar.jpg", viewModel.avatarUrl.value)
        job.cancel()
    }

    @Test
    fun `avatarUrl es null para una sesion sin foto`() = runTest {
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = true)
        val viewModel = crearViewModel()
        val job = launch { viewModel.avatarUrl.collect {} }
        advanceUntilIdle()

        assertEquals(null, viewModel.avatarUrl.value)
        job.cancel()
    }
}

private class FakeLimpiarLogoutExplicito : LimpiarLogoutExplicito {
    var llamadas = 0
    override suspend fun invoke() {
        llamadas++
    }
}

private class FakeAuthRepositoryGate : AuthRepository {
    val sesionFlow = MutableStateFlow(Sesion.Anonima)
    var anonimaLlamada = false
    var anonimaLlamadaCount = 0

    /** How many upcoming [iniciarSesionAnonima] calls should throw before one succeeds. */
    var fallosAnonimaPendientes = 0

    override fun observeSesion(): Flow<Sesion> = sesionFlow
    override suspend fun iniciarSesionAnonima() {
        anonimaLlamada = true
        anonimaLlamadaCount++
        if (fallosAnonimaPendientes > 0) {
            fallosAnonimaPendientes--
            error("no se pudo establecer la sesión anónima")
        }
    }
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
