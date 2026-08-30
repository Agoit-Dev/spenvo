package com.agoitdev.spenvo.cuenta

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.InvitacionPendiente
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.InvitacionPendienteRepository
import com.agoitdev.spenvo.domain.repository.StorageRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import com.agoitdev.spenvo.domain.usecase.AsegurarUsuarioUseCase
import com.agoitdev.spenvo.domain.usecase.EnviarRecuperacionPasswordUseCase
import com.agoitdev.spenvo.domain.usecase.GenerarNombreUsuarioUnicoUseCase
import com.agoitdev.spenvo.domain.usecase.IniciarSesionConEmailUseCase
import com.agoitdev.spenvo.domain.usecase.RenombrarUsuarioUseCase
import com.agoitdev.spenvo.domain.usecase.SubirAvatarUseCase
import com.agoitdev.spenvo.domain.usecase.VincularCredencialUseCase
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CuentaViewModelTest {

    private val authRepository = FakeAuthRepositorioCuenta()
    private val storageRepository = FakeStorageRepositorioCuenta()
    private val usuarioRepository = FakeUsuarioRepositorioCuenta()
    private val accesosRepository = FakeAccesoPlanRepositorioCuenta()
    private val pendientesRepository = FakePendientesRepositorioCuenta()

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
        iniciarSesionConEmail = IniciarSesionConEmailUseCase(authRepository),
        enviarRecuperacionPassword = EnviarRecuperacionPasswordUseCase(authRepository),
        authRepository = authRepository,
        usuarioRepository = usuarioRepository,
        subirAvatarUseCase = SubirAvatarUseCase(storageRepository),
        asegurarUsuario = AsegurarUsuarioUseCase(
            usuarioRepository,
            GenerarNombreUsuarioUnicoUseCase(usuarioRepository),
            accesosRepository,
            pendientesRepository,
        ),
        renombrarUsuario = RenombrarUsuarioUseCase(usuarioRepository),
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

    @Test
    fun `registrar exitoso actualiza el Usuario con nombre y email conservando el nombreUsuario`() = runTest {
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = true)
        usuarioRepository.usuarios["user-1"] = Usuario(id = "user-1", nombreUsuario = "GatoAzul1")
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.registrar(nombre = "Ana", email = "ana@example.com", password = "secret123")
        advanceUntilIdle()

        val actualizado = usuarioRepository.actualizados.single()
        assertEquals("GatoAzul1", actualizado.nombreUsuario)
        assertEquals("Ana", actualizado.nombre)
        assertEquals("ana@example.com", actualizado.email)
        assertTrue(viewModel.estado.value.completado)
        job.cancel()
    }

    @Test
    fun `iniciarSesion exitoso marca el estado como completado`() = runTest {
        authRepository.sesionFlow.value = Sesion.Anonima
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.iniciarSesion(email = "ana@example.com", password = "secret123")
        advanceUntilIdle()

        assertEquals("ana@example.com", authRepository.ultimoEmailLogin)
        assertEquals("secret123", authRepository.ultimaPasswordLogin)
        assertTrue(viewModel.estado.value.completado)
        job.cancel()
    }

    @Test
    fun `iniciarSesion con credenciales invalidas expone el mismo mensaje que usuario inexistente`() = runTest {
        authRepository.sesionFlow.value = Sesion.Anonima
        authRepository.excepcionLogin = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "wrong password")
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.iniciarSesion(email = "ana@example.com", password = "wrong")
        advanceUntilIdle()

        assertEquals(R.string.account_error_credenciales_invalidas, viewModel.estado.value.errorRes)
        job.cancel()
    }

    @Test
    fun `recuperarPassword siempre marca exito visible sin importar si el email existe`() = runTest {
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.recuperarPassword(email = "quien-sea@example.com")
        advanceUntilIdle()

        assertEquals("quien-sea@example.com", authRepository.ultimoEmailRecovery)
        assertTrue(viewModel.recoveryEstado.value.exito)
        job.cancel()
    }

    @Test
    fun `al abrir el perfil de una sesion vinculada carga el nombreUsuario existente`() = runTest {
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = false)
        usuarioRepository.usuarios["user-1"] = Usuario(id = "user-1", nombreUsuario = "GatoAzul1")
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        assertEquals("GatoAzul1", viewModel.perfilEstado.value.nombreUsuario)
        job.cancel()
    }

    @Test
    fun `editarNombreUsuario exitoso actualiza el estado con el nuevo valor`() = runTest {
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = false)
        usuarioRepository.usuarios["user-1"] = Usuario(id = "user-1", nombreUsuario = "GatoAzul1")
        usuarioRepository.resultadoRenombrar = true
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.editarNombreUsuario("ZorroVeloz9")
        advanceUntilIdle()

        assertEquals("ZorroVeloz9", viewModel.perfilEstado.value.nombreUsuario)
        assertNull(viewModel.perfilEstado.value.nombreUsuarioError)
        job.cancel()
    }

    @Test
    fun `editarNombreUsuario fallido expone un error sin tocar el estado previo`() = runTest {
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = false)
        usuarioRepository.usuarios["user-1"] = Usuario(id = "user-1", nombreUsuario = "GatoAzul1")
        usuarioRepository.resultadoRenombrar = false
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.editarNombreUsuario("ZorroVeloz9")
        advanceUntilIdle()

        assertEquals("GatoAzul1", viewModel.perfilEstado.value.nombreUsuario)
        assertEquals(R.string.account_profile_nombre_usuario_en_uso, viewModel.perfilEstado.value.nombreUsuarioError)
        job.cancel()
    }

    @Test
    fun `editarNombreUsuario con entrada en blanco no llama al caso de uso y expone error de validacion`() = runTest {
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = false)
        usuarioRepository.usuarios["user-1"] = Usuario(id = "user-1", nombreUsuario = "GatoAzul1")
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.editarNombreUsuario("   ")
        advanceUntilIdle()

        assertFalse(usuarioRepository.renombrarLlamado)
        assertEquals("GatoAzul1", viewModel.perfilEstado.value.nombreUsuario)
        assertEquals(R.string.account_profile_nombre_usuario_vacio, viewModel.perfilEstado.value.nombreUsuarioError)
        job.cancel()
    }

    @Test
    fun `editarNombreUsuario cuando el caso de uso lanza una excepcion expone un error sin crashear`() = runTest {
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = false)
        usuarioRepository.usuarios["user-1"] = Usuario(id = "user-1", nombreUsuario = "GatoAzul1")
        usuarioRepository.excepcionRenombrar = IllegalStateException("PERMISSION_DENIED")
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.editarNombreUsuario("ZorroVeloz9")
        advanceUntilIdle()

        assertEquals("GatoAzul1", viewModel.perfilEstado.value.nombreUsuario)
        assertEquals(R.string.account_profile_nombre_usuario_error, viewModel.perfilEstado.value.nombreUsuarioError)
        job.cancel()
    }

    @Test
    fun `si obtener falla al cargar el perfil no crashea el colector y una emision posterior si actualiza`() = runTest {
        usuarioRepository.usuarios["user-1"] = Usuario(id = "user-1", nombreUsuario = "GatoAzul1")
        usuarioRepository.excepcionObtener = IllegalStateException("network down")
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }

        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = false)
        advanceUntilIdle()

        assertNull(viewModel.perfilEstado.value.nombreUsuario)

        usuarioRepository.excepcionObtener = null
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = false, email = "ana@spenvo.dev")
        advanceUntilIdle()

        assertEquals("GatoAzul1", viewModel.perfilEstado.value.nombreUsuario)
        job.cancel()
    }

    @Test
    fun `si la sesion pasa a anonima el nombreUsuario previo se limpia del estado`() = runTest {
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = false)
        usuarioRepository.usuarios["user-1"] = Usuario(id = "user-1", nombreUsuario = "GatoAzul1")
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()
        assertEquals("GatoAzul1", viewModel.perfilEstado.value.nombreUsuario)

        authRepository.sesionFlow.value = Sesion.Anonima
        advanceUntilIdle()

        assertNull(viewModel.perfilEstado.value.nombreUsuario)
        assertNull(viewModel.perfilEstado.value.nombreUsuarioError)
        job.cancel()
    }
}

private class FakeAuthRepositorioCuenta : AuthRepository {
    val sesionFlow = MutableStateFlow(Sesion.Anonima)
    var ultimoPhotoUrlActualizado: String? = null
    var cerrarSesionLlamado = false
    var ultimoEmailLogin: String? = null
    var ultimaPasswordLogin: String? = null
    var excepcionLogin: Throwable? = null
    var ultimoEmailRecovery: String? = null

    override fun observeSesion(): Flow<Sesion> = sesionFlow
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun iniciarSesionConEmail(email: String, password: String) {
        ultimoEmailLogin = email
        ultimaPasswordLogin = password
        excepcionLogin?.let { throw it }
        sesionFlow.value = Sesion(uid = "user-1", esAnonima = false, email = email)
    }
    override suspend fun enviarRecuperacionPassword(email: String) {
        ultimoEmailRecovery = email
    }
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

private class FakeUsuarioRepositorioCuenta : UsuarioRepository {
    val usuarios = mutableMapOf<String, Usuario>()
    val actualizados = mutableListOf<Usuario>()
    val indicesEmail = mutableListOf<Pair<String, String>>()
    var resultadoRenombrar = true
    var renombrarLlamado = false
    var excepcionRenombrar: Throwable? = null
    var excepcionObtener: Throwable? = null

    override suspend fun obtener(usuarioId: String): Usuario? {
        excepcionObtener?.let { throw it }
        return usuarios[usuarioId]
    }
    override suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario> =
        usuarioIds.mapNotNull { usuarios[it] }

    override suspend fun intentarReservarNombreUsuario(
        nombreUsuarioNormalizado: String,
        usuarioId: String,
    ): Boolean = true

    override suspend fun crear(usuario: Usuario) {
        usuarios[usuario.id] = usuario
    }

    override suspend fun actualizar(usuario: Usuario) {
        actualizados.add(usuario)
        usuarios[usuario.id] = usuario
    }

    override suspend fun renombrar(
        usuarioId: String,
        nombreUsuarioAnterior: String,
        nombreUsuarioNuevo: String,
    ): Boolean {
        renombrarLlamado = true
        excepcionRenombrar?.let { throw it }
        return resultadoRenombrar
    }

    override suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String) {
        indicesEmail.add(emailNormalizado to usuarioId)
    }

    override suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String? = null
    override suspend fun resolverPorEmail(emailNormalizado: String): String? = null
}

private class FakeAccesoPlanRepositorioCuenta : AccesoPlanRepository {
    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> = MutableStateFlow(emptyList())
    override fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>> = MutableStateFlow(emptyList())
    override suspend fun invitarMiembro(acceso: AccesoPlan) = Unit
    override suspend fun aceptarInvitacion(usuarioId: String, planId: String) = Unit
}

private class FakePendientesRepositorioCuenta : InvitacionPendienteRepository {
    override suspend fun crear(invitacion: InvitacionPendiente) = Unit
    override suspend fun obtenerPorEmail(emailNormalizado: String): List<InvitacionPendiente> = emptyList()
    override suspend fun eliminar(emailNormalizado: String, planId: String) = Unit
}
