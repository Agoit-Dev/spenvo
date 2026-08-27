package com.agoitdev.spenvo.movimientos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agoitdev.spenvo.data.remote.sync.MovimientoSincronizacion
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import com.agoitdev.spenvo.domain.sync.ConflictosPendientes
import com.agoitdev.spenvo.domain.usecase.ActualizarGastoUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.AplicarGastoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.AplicarIngresoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearGastoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarGastoUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarBalanceAcumuladoPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasPorTipoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarMovimientosUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarResumenMensualPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ValidarMontoUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val plan = PlanFinanciero(id = "p1", nombre = "Casa", moneda = "EUR", createdBy = "user-1")

    private val planRepo = FakePlanFinancieroRepositorioHomeScreen(plan)
    private val movimientoRepo = FakeMovimientoRepositorioHomeScreen()
    private val categoriaRepo = FakeCategoriaRepositorioHomeScreen()
    private val sincronizador = FakeMovimientoSincronizacionHomeScreen()
    private val authRepository = FakeAuthRepositorioHomeScreen()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearHomeViewModel() = HomeViewModel(
        observarPlan = ObservarPlanUseCase(planRepo),
        observarResumenMensual = ObservarResumenMensualPlanUseCase(movimientoRepo),
        observarBalanceAcumulado = ObservarBalanceAcumuladoPlanUseCase(movimientoRepo),
    )

    private fun crearMovimientosViewModel() = MovimientosViewModel(
        observarMovimientos = ObservarMovimientosUseCase(movimientoRepo),
        observarCategoriasPorTipo = ObservarCategoriasPorTipoUseCase(categoriaRepo),
        observarCategorias = ObservarCategoriasUseCase(categoriaRepo),
        crearGasto = CrearGastoUseCase(movimientoRepo, ValidarMontoUseCase()),
        crearIngreso = CrearIngresoUseCase(movimientoRepo, ValidarMontoUseCase()),
        actualizarGasto = ActualizarGastoUseCase(movimientoRepo, ValidarMontoUseCase()),
        eliminarGasto = EliminarGastoUseCase(movimientoRepo),
        actualizarIngreso = ActualizarIngresoUseCase(movimientoRepo, ValidarMontoUseCase()),
        eliminarIngreso = EliminarIngresoUseCase(movimientoRepo),
        aplicarGastoRemoto = AplicarGastoRemotoUseCase(movimientoRepo),
        aplicarIngresoRemoto = AplicarIngresoRemotoUseCase(movimientoRepo),
        sincronizador = sincronizador,
        authRepository = authRepository,
        conflictosPendientes = ConflictosPendientes(),
    )

    @Test
    fun `muestra el nombre del plan y el titulo de balance acumulado`() {
        composeTestRule.setContent {
            HomeScreen(
                planId = "p1",
                movimientosViewModel = crearMovimientosViewModel(),
                viewModel = crearHomeViewModel(),
            )
        }

        composeTestRule.onNodeWithText("Casa").assertIsDisplayed()
        composeTestRule.onNodeWithText("Balance acumulado").assertIsDisplayed()
    }

    @Test
    fun `tocar Nuevo Gasto abre el formulario con tipo gasto preseleccionado`() {
        composeTestRule.setContent {
            HomeScreen(
                planId = "p1",
                movimientosViewModel = crearMovimientosViewModel(),
                viewModel = crearHomeViewModel(),
            )
        }

        composeTestRule.onNodeWithText("Nuevo Gasto").performClick()

        composeTestRule.onNodeWithText("Nuevo movimiento").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gastos").assertIsDisplayed()
    }
}

private class FakePlanFinancieroRepositorioHomeScreen(private val plan: PlanFinanciero?) : PlanFinancieroRepository {
    override fun observarPlanesDelUsuario(usuarioId: String): Flow<List<PlanFinanciero>> = flowOf(listOfNotNull(plan))
    override fun observarPlan(planId: String): Flow<PlanFinanciero?> = flowOf(plan)
    override suspend fun crearPlan(plan: PlanFinanciero) = Unit
    override suspend fun actualizarPlan(plan: PlanFinanciero) = Unit
}

private class FakeMovimientoRepositorioHomeScreen : MovimientoRepository {
    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override suspend fun aplicarGastoRemoto(id: String) = Unit
    override suspend fun aplicarIngresoRemoto(id: String) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(emptyList())
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(emptyList())
}

private class FakeCategoriaRepositorioHomeScreen : CategoriaRepository {
    override fun observarCategorias(planId: String): Flow<List<Categoria>> = flowOf(emptyList())
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        flowOf(emptyList())
    override suspend fun crearCategoria(categoria: Categoria) = Unit
    override suspend fun crearCategorias(categorias: List<Categoria>) = Unit
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) = Unit
}

private class FakeMovimientoSincronizacionHomeScreen : MovimientoSincronizacion {
    override fun sincronizar(planId: String): Flow<Unit> = flowOf(Unit)
}

private class FakeAuthRepositorioHomeScreen : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = flowOf(Sesion(uid = "user-1", esAnonima = true))
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
