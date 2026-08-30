package com.agoitdev.spenvo.cuenta

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class CuentaScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val storageRepository = FakeStorageRepositorioPantalla()
    private val usuarioRepository = FakeUsuarioRepositorioPantalla()
    private val accesosRepository = FakeAccesoPlanRepositorioPantalla()
    private val pendientesRepository = FakePendientesRepositorioPantalla()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(authRepository: AuthRepository) = CuentaViewModel(
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
    fun `sesion anonima muestra el formulario de registro`() {
        val viewModel = crearViewModel(FakeAuthRepositorioPantalla(Sesion.Anonima))

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithText("Tus datos de invitado", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Cerrar sesión").assertDoesNotExist()
    }

    @Test
    fun `sesion anonima real con uid tambien muestra el formulario de registro`() {
        val viewModel = crearViewModel(FakeAuthRepositorioPantalla(Sesion(uid = "anon-1", esAnonima = true)))

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel, tabInicial = AuthTab.CREAR_CUENTA)
        }

        composeTestRule.onNodeWithText("Tus datos de invitado", substring = true).assertIsDisplayed()
    }

    @Test
    fun `sesion vinculada muestra el perfil con nombre email e informacion de cuenta`() {
        val sesion = Sesion(uid = "user-1", esAnonima = false, email = "ana@spenvo.dev", nombre = "Ana")
        val viewModel = crearViewModel(FakeAuthRepositorioPantalla(sesion))

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithText("Ana").assertIsDisplayed()
        composeTestRule.onNodeWithText("ana@spenvo.dev", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Información de la cuenta").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cerrar sesión").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Tus datos de invitado", substring = true).assertDoesNotExist()
    }

    @Test
    fun `tocar cerrar sesion invoca logout del viewmodel`() {
        val authRepository = FakeAuthRepositorioPantalla(
            Sesion(uid = "user-1", esAnonima = false, email = "ana@spenvo.dev", nombre = "Ana"),
        )
        val viewModel = crearViewModel(authRepository)

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertTrue(authRepository.cerrarSesionLlamado)
    }

    @Test
    fun `el perfil muestra el campo de nombreUsuario con el valor seeded y permite guardarlo`() {
        val sesion = Sesion(uid = "user-1", esAnonima = false, email = "ana@spenvo.dev", nombre = "Ana")
        usuarioRepository.usuarios["user-1"] = Usuario(id = "user-1", nombreUsuario = "GatoAzul1")
        val viewModel = crearViewModel(FakeAuthRepositorioPantalla(sesion))

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("GatoAzul1").assertIsDisplayed()

        composeTestRule.onNodeWithText("GatoAzul1").performTextReplacement("ZorroVeloz9")
        composeTestRule.onNodeWithText("Guardar").performClick()
        composeTestRule.waitForIdle()

        assertEquals("user-1", usuarioRepository.usuarioIdRecibido)
        assertEquals("GatoAzul1", usuarioRepository.nombreUsuarioAnteriorRecibido)
        assertEquals("ZorroVeloz9", usuarioRepository.nombreUsuarioNuevoRecibido)
    }

    @Test
    fun `el perfil no incluye filas fuera de alcance`() {
        val sesion = Sesion(uid = "user-1", esAnonima = false, email = "ana@spenvo.dev", nombre = "Ana")
        val viewModel = crearViewModel(FakeAuthRepositorioPantalla(sesion))

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel)
        }

        composeTestRule.onAllNodesWithText("Base Currency", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Notifications", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Backup", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Privacy", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Export Data", substring = true).assertCountEquals(0)
    }

    @Test
    fun `sesion anonima con tabInicial CREAR_CUENTA muestra el formulario de registro por defecto`() {
        val viewModel = crearViewModel(FakeAuthRepositorioPantalla(Sesion.Anonima))

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel, tabInicial = AuthTab.CREAR_CUENTA)
        }

        composeTestRule.onNodeWithText("Tus datos de invitado", substring = true).assertIsDisplayed()
    }

    @Test
    fun `sesion anonima con tabInicial INICIAR_SESION muestra el formulario de login por defecto`() {
        val viewModel = crearViewModel(FakeAuthRepositorioPantalla(Sesion.Anonima))

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel, tabInicial = AuthTab.INICIAR_SESION)
        }

        composeTestRule.onNodeWithText("¿Olvidaste tu contraseña?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tus datos de invitado", substring = true).assertDoesNotExist()
    }

    @Test
    fun `tocar la pestana de iniciar sesion cambia del formulario de registro al de login`() {
        val viewModel = crearViewModel(FakeAuthRepositorioPantalla(Sesion.Anonima))

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel, tabInicial = AuthTab.CREAR_CUENTA)
        }

        composeTestRule.onNodeWithContentDescription("Iniciar sesión").performClick()

        composeTestRule.onNodeWithText("¿Olvidaste tu contraseña?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tus datos de invitado", substring = true).assertDoesNotExist()
    }

    @Test
    fun `tocar iniciar sesion invoca iniciarSesion del viewmodel con email y password`() {
        val authRepository = FakeAuthRepositorioPantalla(Sesion.Anonima)
        val viewModel = crearViewModel(authRepository)

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel, tabInicial = AuthTab.INICIAR_SESION)
        }

        composeTestRule.onNodeWithText("Correo").performTextReplacement("ana@example.com")
        composeTestRule.onNodeWithText("Contraseña").performTextReplacement("secret123")
        composeTestRule.onNodeWithText("Iniciar sesión").performClick()
        composeTestRule.waitForIdle()

        assertEquals("ana@example.com", authRepository.ultimoEmailLogin)
        assertEquals("secret123", authRepository.ultimaPasswordLogin)
    }

    @Test
    fun `abrir y confirmar el dialogo de recuperacion invoca recuperarPassword`() {
        val authRepository = FakeAuthRepositorioPantalla(Sesion.Anonima)
        val viewModel = crearViewModel(authRepository)

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel, tabInicial = AuthTab.INICIAR_SESION)
        }

        composeTestRule.onNodeWithText("¿Olvidaste tu contraseña?").performClick()
        composeTestRule.onNodeWithText("Correo").performTextReplacement("ana@example.com")
        composeTestRule.onNodeWithText("Enviar").performClick()
        composeTestRule.waitForIdle()

        assertEquals("ana@example.com", authRepository.ultimoEmailRecovery)
    }
}

private class FakeAuthRepositorioPantalla(sesionInicial: Sesion) : AuthRepository {
    private val sesionFlow = MutableStateFlow(sesionInicial)
    var cerrarSesionLlamado = false
    var ultimoEmailLogin: String? = null
    var ultimaPasswordLogin: String? = null
    var ultimoEmailRecovery: String? = null

    override fun observeSesion(): Flow<Sesion> = sesionFlow
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun iniciarSesionConEmail(email: String, password: String) {
        ultimoEmailLogin = email
        ultimaPasswordLogin = password
        sesionFlow.value = Sesion(uid = "user-1", esAnonima = false, email = email)
    }
    override suspend fun enviarRecuperacionPassword(email: String) {
        ultimoEmailRecovery = email
    }
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() {
        cerrarSesionLlamado = true
    }
}

private class FakeStorageRepositorioPantalla : StorageRepository {
    override suspend fun subirAvatar(uid: String, bytes: ByteArray, contentType: String): String =
        "https://cdn.spenvo.dev/avatars/user-1/avatar.jpg"
}

private class FakeUsuarioRepositorioPantalla : UsuarioRepository {
    val usuarios = mutableMapOf<String, Usuario>()
    var usuarioIdRecibido: String? = null
    var nombreUsuarioAnteriorRecibido: String? = null
    var nombreUsuarioNuevoRecibido: String? = null

    override suspend fun obtener(usuarioId: String): Usuario? = usuarios[usuarioId]
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
        usuarios[usuario.id] = usuario
    }

    override suspend fun renombrar(
        usuarioId: String,
        nombreUsuarioAnterior: String,
        nombreUsuarioNuevo: String,
    ): Boolean {
        usuarioIdRecibido = usuarioId
        nombreUsuarioAnteriorRecibido = nombreUsuarioAnterior
        nombreUsuarioNuevoRecibido = nombreUsuarioNuevo
        return true
    }

    override suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String) = Unit
    override suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String? = null
    override suspend fun resolverPorEmail(emailNormalizado: String): String? = null
}

private class FakeAccesoPlanRepositorioPantalla : AccesoPlanRepository {
    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> = MutableStateFlow(emptyList())
    override fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>> = MutableStateFlow(emptyList())
    override suspend fun invitarMiembro(acceso: AccesoPlan) = Unit
    override suspend fun aceptarInvitacion(usuarioId: String, planId: String) = Unit
}

private class FakePendientesRepositorioPantalla : InvitacionPendienteRepository {
    override suspend fun crear(invitacion: InvitacionPendiente) = Unit
    override suspend fun obtenerPorEmail(emailNormalizado: String): List<InvitacionPendiente> = emptyList()
    override suspend fun eliminar(emailNormalizado: String, planId: String) = Unit
}
