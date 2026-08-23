package com.agoitdev.spenvo.cuenta

import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.StorageRepository
import com.agoitdev.spenvo.domain.usecase.SubirAvatarUseCase
import com.agoitdev.spenvo.domain.usecase.VincularCredencialUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CuentaViewModelTest {

    private val authRepository = FakeAuthRepositorioCuenta()
    private val storageRepository = FakeStorageRepositorioCuenta()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel() = CuentaViewModel(
        vincularCredencial = VincularCredencialUseCase(authRepository),
        authRepository = authRepository,
        subirAvatarUseCase = SubirAvatarUseCase(storageRepository),
    )

    @Test
    fun `subirAvatar exitoso sube el archivo y actualiza el photoUrl del perfil`() = runTest {
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = false, email = "ana@spenvo.dev")
        storageRepository.urlDevuelta = "https://cdn.spenvo.dev/avatars/user-1/avatar.jpg"
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.subirAvatar(bytes = byteArrayOf(1, 2, 3), contentType = "image/jpeg")
        advanceUntilIdle()

        assertEquals("user-1", storageRepository.ultimoUid)
        assertEquals("https://cdn.spenvo.dev/avatars/user-1/avatar.jpg", authRepository.ultimoPhotoUrlActualizado)
        assertFalse(viewModel.perfilEstado.value.subiendoAvatar)
        assertNull(viewModel.perfilEstado.value.avatarError)
        job.cancel()
    }

    @Test
    fun `subirAvatar fallido expone un error sin actualizar el perfil`() = runTest {
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = false)
        storageRepository.fallar = true
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.subirAvatar(bytes = byteArrayOf(1), contentType = "image/png")
        advanceUntilIdle()

        assertEquals("subida fallida", viewModel.perfilEstado.value.avatarError)
        assertFalse(viewModel.perfilEstado.value.subiendoAvatar)
        assertNull(authRepository.ultimoPhotoUrlActualizado)
        job.cancel()
    }

    @Test
    fun `subirAvatar en una sesion anonima no hace nada`() = runTest {
        authRepository.sesionFlow.value = Sesion.Anonima
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.subirAvatar(bytes = byteArrayOf(1), contentType = "image/jpeg")
        advanceUntilIdle()

        assertNull(storageRepository.ultimoUid)
        assertNull(authRepository.ultimoPhotoUrlActualizado)
        job.cancel()
    }

    @Test
    fun `logout invoca cerrarSesion del repositorio de autenticacion`() = runTest {
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = false)
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertTrue(authRepository.cerrarSesionLlamado)
        job.cancel()
    }
}

private class FakeAuthRepositorioCuenta : AuthRepository {
    val sesionFlow = MutableStateFlow(Sesion.Anonima)
    var ultimoPhotoUrlActualizado: String? = null
    var cerrarSesionLlamado = false

    override fun observeSesion(): Flow<Sesion> = sesionFlow
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) {
        ultimoPhotoUrlActualizado = photoUrl
    }
    override suspend fun cerrarSesion() {
        cerrarSesionLlamado = true
    }
}

private class FakeStorageRepositorioCuenta : StorageRepository {
    var urlDevuelta: String = "https://cdn.spenvo.dev/avatars/user-1/avatar.jpg"
    var fallar = false
    var ultimoUid: String? = null

    override suspend fun subirAvatar(uid: String, bytes: ByteArray, contentType: String): String {
        ultimoUid = uid
        if (fallar) error("subida fallida")
        return urlDevuelta
    }
}
