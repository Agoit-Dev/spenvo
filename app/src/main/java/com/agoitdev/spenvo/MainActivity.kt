package com.agoitdev.spenvo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.agoitdev.spenvo.categorias.CategoriasScreen
import com.agoitdev.spenvo.cuenta.AuthTab
import com.agoitdev.spenvo.cuenta.CuentaScreen
import com.agoitdev.spenvo.designsystem.theme.SpenvoTheme
import com.agoitdev.spenvo.movimientos.HomeScreen
import com.agoitdev.spenvo.movimientos.MovimientosScreen
import com.agoitdev.spenvo.movimientos.MovimientosViewModel
import com.agoitdev.spenvo.planes.MiembrosScreen
import com.agoitdev.spenvo.planes.PlanesScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@Serializable
data object PlanesRoute : NavKey

@Serializable
data class PlanRoute(val planId: String) : NavKey

@Serializable
data object CuentaRoute : NavKey

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpenvoTheme {
                SpenvoApp()
            }
        }
    }
}

/**
 * Pure state-machine step: decides how [estado] mutates [backStack]. Extracted from
 * [SpenvoApp]'s `LaunchedEffect` so the gate's navigation branching (the riskiest logic in
 * the login-gate flow) is directly unit-testable without Compose or Hilt.
 */
fun aplicarEstadoGate(estado: EstadoGate, backStack: MutableList<NavKey>) {
    when (estado) {
        EstadoGate.MostrarGate -> {
            backStack.clear()
            backStack.add(CuentaRoute)
        }
        EstadoGate.MostrarApp -> {
            if (backStack.singleOrNull() == CuentaRoute) {
                backStack.clear()
                backStack.add(PlanesRoute)
            }
        }
        EstadoGate.Cargando -> Unit
    }
}

/** Test tag of the blank placeholder shown while the gate is still resolving the session. */
const val TAG_GATE_CARGANDO = "gate_cargando"

/**
 * The backstack starts at [PlanesRoute], so rendering [NavDisplay] before the gate has decided
 * would flash [PlanesScreen] at a user who just logged out, until [aplicarEstadoGate] clears it.
 * The native splash window ([installSplashScreen]) covers the window before this.
 */
fun debeMostrarNavegacion(estado: EstadoGate): Boolean = estado != EstadoGate.Cargando

/**
 * Which auth form [CuentaScreen] opens on, decided by *how the user got there*: the post-logout
 * gate opens on sign-in (the user demonstrably already has an account), while `PlanesScreen`'s
 * account menu — reached from a live anonymous session — keeps opening on sign-up.
 */
fun tabInicialPara(estado: EstadoGate): AuthTab =
    if (estado == EstadoGate.MostrarGate) AuthTab.INICIAR_SESION else AuthTab.CREAR_CUENTA

/**
 * "Continuar como invitado" belongs to the gate wrapper, never to [CuentaScreen] itself: reached
 * from the account menu, [CuentaScreen] may be showing an already-linked account, for which
 * "continue as guest" would make no sense.
 */
fun mostrarContinuarComoInvitado(estado: EstadoGate): Boolean = estado == EstadoGate.MostrarGate

/**
 * `CuentaRoute`'s content, in either of the two ways it is reachable: as the post-logout gate
 * (sign-in tab plus "continuar como invitado"), or as the plain account screen opened from
 * `PlanesScreen`'s account menu.
 */
@Composable
private fun EntradaCuenta(
    estadoGate: EstadoGate,
    onContinuarComoInvitado: () -> Unit,
    onCerrar: () -> Unit,
) {
    val contenidoCuenta: @Composable (Modifier) -> Unit = { modificador ->
        CuentaScreen(
            onRegistroCompletado = onCerrar,
            tabInicial = tabInicialPara(estadoGate),
            modifier = modificador,
        )
    }
    if (mostrarContinuarComoInvitado(estadoGate)) {
        GateInvitado(
            onContinuarComoInvitado = onContinuarComoInvitado,
            contenidoCuenta = contenidoCuenta,
        )
    } else {
        contenidoCuenta(Modifier)
    }
}

/**
 * Post-logout gate wrapper: [contenidoCuenta] (the sign-in form) plus the only non-sign-in way
 * out of [EstadoGate.MostrarGate].
 */
@Composable
fun GateInvitado(
    onContinuarComoInvitado: () -> Unit,
    contenidoCuenta: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().navigationBarsPadding()) {
        contenidoCuenta(Modifier.weight(1f))
        TextButton(
            onClick = onContinuarComoInvitado,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.gate_continue_as_guest))
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SpenvoApp(modifier: Modifier = Modifier, gateViewModel: SesionGateViewModel = hiltViewModel()) {
    val estadoGate by gateViewModel.estado.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(PlanesRoute)
    val avatarUrl by gateViewModel.avatarUrl.collectAsStateWithLifecycle()

    LaunchedEffect(estadoGate) {
        aplicarEstadoGate(estadoGate, backStack)
    }

    Surface(modifier = modifier.fillMaxSize()) {
        if (!debeMostrarNavegacion(estadoGate)) {
            Box(Modifier.fillMaxSize().testTag(TAG_GATE_CARGANDO))
            return@Surface
        }
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            // Forward-compatible plumbing for future cross-route list/detail pairs (M6 Slice B):
            // a no-op today since no `entry<T>` carries pane metadata yet. The real, working
            // list-detail split lives locally inside MovimientosScreen.
            sceneStrategies = listOf(rememberListDetailSceneStrategy<NavKey>()),
            entryProvider = entryProvider {
                entry<PlanesRoute> {
                    PlanesScreen(
                        onCrearCuenta = { backStack.add(CuentaRoute) },
                        onAbrirPlan = { planId -> backStack.add(PlanRoute(planId)) },
                    )
                }
                entry<PlanRoute> { route ->
                    ContenidoPlanRoute(
                        route = route,
                        avatarUrl = avatarUrl,
                        onAbrirCuenta = { backStack.add(CuentaRoute) },
                    )
                }
                entry<CuentaRoute> {
                    EntradaCuenta(
                        estadoGate = estadoGate,
                        onContinuarComoInvitado = gateViewModel::continuarComoInvitado,
                        onCerrar = { if (backStack.size > 1) backStack.removeLastOrNull() },
                    )
                }
            },
        )
    }
}

/**
 * [PlanRoute]'s tab content: extracted out of [SpenvoApp] so the four tab screens' shared
 * [avatarUrl]/[onAbrirCuenta] wiring doesn't grow `SpenvoApp` past the project's `LongMethod`
 * detekt threshold.
 */
@Composable
private fun ContenidoPlanRoute(
    route: PlanRoute,
    avatarUrl: String?,
    onAbrirCuenta: () -> Unit,
) {
    val movimientosViewModel: MovimientosViewModel = hiltViewModel()
    PlanScaffold(
        contenidoHome = {
            HomeScreen(
                planId = route.planId,
                movimientosViewModel = movimientosViewModel,
                avatarUrl = avatarUrl,
                onAbrirCuenta = onAbrirCuenta,
            )
        },
        contenidoMovimientos = {
            MovimientosScreen(
                planId = route.planId,
                avatarUrl = avatarUrl,
                onAbrirCuenta = onAbrirCuenta,
                viewModel = movimientosViewModel,
            )
        },
        contenidoCategorias = {
            CategoriasScreen(
                planId = route.planId,
                avatarUrl = avatarUrl,
                onAbrirCuenta = onAbrirCuenta,
            )
        },
        contenidoMiembros = {
            MiembrosScreen(
                planId = route.planId,
                avatarUrl = avatarUrl,
                onAbrirCuenta = onAbrirCuenta,
            )
        },
    )
}
