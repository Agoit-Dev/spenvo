package com.agoitdev.spenvo

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// Plain Application on purpose: the real SpenvoApplication boots Firebase App Check on onCreate,
// which has no place (and no credentials) in a JVM test. Nothing here needs Hilt — the gate
// ViewModel is constructed directly with fakes.
@Config(sdk = [34], qualifiers = "es", application = Application::class)
class SpenvoAppTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val limpiarLogout = FakeLimpiarLogoutPantalla()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `el gate ofrece continuar como invitado y eso limpia el logout y crea la sesion anonima`() {
        val authRepository = FakeAuthRepositoryPantalla()
        val viewModel = SesionGateViewModel(authRepository, MutableStateFlow(true), limpiarLogout)

        composeTestRule.setContent {
            GateInvitado(
                onContinuarComoInvitado = viewModel::continuarComoInvitado,
                contenidoCuenta = { modifier -> Box(modifier.fillMaxSize()) },
            )
        }

        composeTestRule.onNodeWithText("Continuar como invitado").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continuar como invitado").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, limpiarLogout.llamadas)
        assertEquals(1, authRepository.anonimaLlamadaCount)
    }

    @Test
    fun `mientras el gate resuelve la sesion no se renderiza PlanesScreen`() {
        // A session flow that never emits keeps the gate at EstadoGate.Cargando. Rendering
        // NavDisplay there would compose PlanesRoute — the backstack's initial entry — and flash
        // PlanesScreen at a user who may well be logged out.
        val viewModel = SesionGateViewModel(
            FakeAuthRepositoryPantalla(sesiones = MutableSharedFlow()),
            MutableStateFlow(false),
            limpiarLogout,
        )

        composeTestRule.setContent {
            SpenvoApp(gateViewModel = viewModel)
        }

        composeTestRule.onNodeWithTag(TAG_GATE_CARGANDO).assertExists()
        composeTestRule.onNodeWithText("Planes").assertDoesNotExist()
    }
}

private class FakeLimpiarLogoutPantalla : LimpiarLogoutExplicito {
    var llamadas = 0
    override suspend fun invoke() {
        llamadas++
    }
}

private class FakeAuthRepositoryPantalla(
    private val sesiones: Flow<Sesion> = MutableStateFlow(Sesion.Anonima),
) : AuthRepository {
    var anonimaLlamadaCount = 0

    override fun observeSesion(): Flow<Sesion> = sesiones
    override suspend fun iniciarSesionAnonima() {
        anonimaLlamadaCount++
    }
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
