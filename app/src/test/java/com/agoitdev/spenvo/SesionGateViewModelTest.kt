package com.agoitdev.spenvo

import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel() = SesionGateViewModel(authRepository, flagLogoutExplicito)

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
    fun `REGRESION cold start con flag true nunca llama iniciarSesionAnonima`() = runTest {
        // Simulates: process was killed right after an explicit logout (flag persisted true),
        // then relaunched — this is the exact scenario a merely in-memory flag would have failed.
        authRepository.sesionFlow.value = Sesion.Anonima
        flagLogoutExplicito.value = true
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()

        assertEquals(false, authRepository.anonimaLlamada)
        assertEquals(EstadoGate.MostrarGate, viewModel.estado.value)
        job.cancel()
    }
}

private class FakeAuthRepositoryGate : AuthRepository {
    val sesionFlow = MutableStateFlow(Sesion.Anonima)
    var anonimaLlamada = false

    override fun observeSesion(): Flow<Sesion> = sesionFlow
    override suspend fun iniciarSesionAnonima() {
        anonimaLlamada = true
        sesionFlow.value = Sesion(uid = "anon-1", esAnonima = true)
    }
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
