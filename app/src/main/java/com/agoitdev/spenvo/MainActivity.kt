package com.agoitdev.spenvo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SpenvoApp(modifier: Modifier = Modifier, gateViewModel: SesionGateViewModel = hiltViewModel()) {
    val estadoGate by gateViewModel.estado.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(PlanesRoute)

    LaunchedEffect(estadoGate) {
        aplicarEstadoGate(estadoGate, backStack)
    }

    Surface(modifier = modifier.fillMaxSize()) {
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
                    val movimientosViewModel: MovimientosViewModel = hiltViewModel()
                    PlanScaffold(
                        contenidoHome = {
                            HomeScreen(planId = route.planId, movimientosViewModel = movimientosViewModel)
                        },
                        contenidoMovimientos = {
                            MovimientosScreen(planId = route.planId, viewModel = movimientosViewModel)
                        },
                        contenidoCategorias = { CategoriasScreen(planId = route.planId) },
                        contenidoMiembros = { MiembrosScreen(planId = route.planId) },
                    )
                }
                entry<CuentaRoute> {
                    CuentaScreen(
                        onRegistroCompletado = { if (backStack.size > 1) backStack.removeLastOrNull() },
                    )
                }
            },
        )
    }
}
