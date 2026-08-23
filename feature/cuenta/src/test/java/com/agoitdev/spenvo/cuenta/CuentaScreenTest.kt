package com.agoitdev.spenvo.cuenta

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.StorageRepository
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
        authRepository = authRepository,
        subirAvatarUseCase = SubirAvatarUseCase(storageRepository),
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
    fun `sesion vinculada muestra el perfil con nombre email e informacion de cuenta`() {
        val sesion = Sesion(uid = "user-1", esAnonima = false, email = "ana@spenvo.dev", nombre = "Ana")
        val viewModel = crearViewModel(FakeAuthRepositorioPantalla(sesion))

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithText("Ana").assertIsDisplayed()
        composeTestRule.onNodeWithText("ana@spenvo.dev", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Información de la cuenta").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cerrar sesión").assertIsDisplayed()
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

        composeTestRule.onNodeWithText("Cerrar sesión").performClick()
        composeTestRule.waitForIdle()

        assertTrue(authRepository.cerrarSesionLlamado)
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
}

private class FakeAuthRepositorioPantalla(sesionInicial: Sesion) : AuthRepository {
    private val sesionFlow = MutableStateFlow(sesionInicial)
    var cerrarSesionLlamado = false

    override fun observeSesion(): Flow<Sesion> = sesionFlow
    override suspend fun iniciarSesionAnonima() = Unit
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
