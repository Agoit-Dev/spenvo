package com.agoitdev.spenvo.movimientos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.agoitdev.spenvo.data.remote.sync.MovimientoSincronizacion
import com.agoitdev.spenvo.designsystem.components.TAG_AVATAR_TOPBAR_PLACEHOLDER
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
import com.agoitdev.spenvo.domain.usecase.ActualizarGastoUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarIngresoUseCase
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
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoGastoUsandoLocalUseCase
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoGastoUsandoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoIngresoUsandoLocalUseCase
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoIngresoUsandoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.ValidarMontoUseCase
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    private lateinit var movimientosViewModel: MovimientosViewModel
    private lateinit var localeOriginal: Locale
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Money rendering is locale-driven (NumberFormat), so the locale has to be pinned for the
        // formatting assertions below to mean anything.
        localeOriginal = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("es-ES"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(localeOriginal)
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
        resolverConflictoGastoUsandoLocal = ResolverConflictoGastoUsandoLocalUseCase(
            movimientoRepo,
            ValidarMontoUseCase(),
        ),
        resolverConflictoIngresoUsandoLocal = ResolverConflictoIngresoUsandoLocalUseCase(
            movimientoRepo,
            ValidarMontoUseCase(),
        ),
        resolverConflictoGastoUsandoRemoto = ResolverConflictoGastoUsandoRemotoUseCase(movimientoRepo),
        resolverConflictoIngresoUsandoRemoto = ResolverConflictoIngresoUsandoRemotoUseCase(movimientoRepo),
        sincronizador = sincronizador,
        authRepository = authRepository,
        registroConflictosPendientes = FakeRegistroConflictosPendientesHomeScreen(),
    )

    private fun montarHome(
        avatarUrl: String? = null,
        onAbrirCuenta: () -> Unit = {},
        onAbrirAjustes: () -> Unit = {},
    ) {
        movimientosViewModel = crearMovimientosViewModel()
        composeTestRule.setContent {
            HomeScreen(
                planId = "p1",
                movimientosViewModel = movimientosViewModel,
                viewModel = crearHomeViewModel(),
                avatarUrl = avatarUrl,
                onAbrirCuenta = onAbrirCuenta,
                onAbrirAjustes = onAbrirAjustes,
            )
        }
    }

    @Test
    fun `tocar el avatar y luego Cuenta invoca onAbrirCuenta`() {
        var invocado = false
        montarHome(onAbrirCuenta = { invocado = true })

        // The placeholder's testTag lives on the IconButton's content, which TopAppBar's actions
        // Row merges into a single accessibility node -- performClick() needs the unmerged tree to
        // still address it by its own tag.
        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText("Cuenta").performClick()

        assertEquals(true, invocado)
    }

    @Test
    fun `tocar el avatar y luego Ajustes invoca onAbrirAjustes`() {
        var invocado = false
        montarHome(onAbrirAjustes = { invocado = true })

        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText("Ajustes").performClick()

        assertEquals(true, invocado)
    }

    @Test
    fun `muestra el nombre del plan y el titulo de balance acumulado`() {
        montarHome()

        // "Casa" now renders twice by design (Task 3: TopAppBar title + HomeContenido's own
        // heading) -- assert the first match is displayed rather than requiring exactly one node.
        composeTestRule.onAllNodesWithText("Casa")[0].assertIsDisplayed()
        composeTestRule.onNodeWithText("Balance acumulado").assertIsDisplayed()
    }

    // AGENTS.md rule 3: snapshot listeners attach when opening a screen. Home is the plan's
    // landing tab, so it must attach the listener itself instead of waiting for Movimientos to be
    // opened.
    @Test
    fun `abrir Home sincroniza el plan activo`() {
        montarHome()

        assertEquals(listOf("p1"), sincronizador.planesSincronizados)
    }

    @Test
    fun `tocar Nuevo Gasto abre el formulario con tipo gasto preseleccionado`() {
        montarHome()

        composeTestRule.onNodeWithText("Nuevo Gasto").performClick()

        composeTestRule.onNodeWithText("Nuevo movimiento").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gastos").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `tocar Nuevo Ingreso abre el formulario con tipo ingreso preseleccionado`() {
        montarHome()

        composeTestRule.onNodeWithText("Nuevo Ingreso").performClick()

        composeTestRule.onNodeWithText("Nuevo movimiento").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ingresos").assertIsDisplayed().assertIsSelected()
    }

    // Pins each figure to its own card: the two amounts are deliberately different, so swapping
    // `resumen.ingresosMes` and `resumen.gastosMes` at the call site fails this test.
    @Test
    fun `los ingresos y los gastos del mes se muestran cada uno en su tarjeta`() {
        val hoy = LocalDate.now()
        movimientoRepo.gastos = listOf(gastoDe(id = "g1", unidadesMenores = 2_500, fecha = hoy))
        movimientoRepo.ingresos = listOf(ingresoDe(id = "i1", unidadesMenores = 4_000, fecha = hoy))

        montarHome()

        composeTestRule.onNodeWithTag(TAG_HOME_INGRESOS_MES).assertTextContains("40,00", substring = true)
        composeTestRule.onNodeWithTag(TAG_HOME_GASTOS_MES).assertTextContains("25,00", substring = true)
        composeTestRule.onNodeWithTag(TAG_HOME_BALANCE_ACUMULADO).assertTextContains("15,00", substring = true)
    }

    // AGENTS.md i18n rule: money goes through NumberFormat, never hand-concatenation. A negative
    // balance must keep the locale's sign/decimal separator and render the currency SYMBOL for
    // `plan.moneda`, not the raw ISO code the old hand-rolled formatter appended.
    @Test
    fun `un balance negativo se formatea con NumberFormat y el simbolo de la moneda`() {
        movimientoRepo.gastos = listOf(gastoDe(id = "g1", unidadesMenores = 500, fecha = LocalDate.now()))

        montarHome()

        composeTestRule.onNodeWithTag(TAG_HOME_BALANCE_ACUMULADO)
            .assertTextContains("-5,00", substring = true)
        composeTestRule.onNodeWithTag(TAG_HOME_BALANCE_ACUMULADO)
            .assertTextContains("€", substring = true)
    }

    // Home shares MovimientosViewModel with the Movimientos tab: an unhandled `estadoForm.error`
    // both leaves the user with no feedback here AND fires a stale snackbar in the other tab later.
    @Test
    fun `un guardado fallido muestra el error y lo consume`() {
        categoriaRepo.categorias = listOf(
            Categoria(id = "cat-comida", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
        )
        movimientoRepo.errorAlGuardar = "No tienes permiso para guardar"

        montarHome()

        composeTestRule.onNodeWithText("Nuevo Gasto").performClick()
        composeTestRule.onNodeWithText("Monto").performTextInput("10")
        composeTestRule.onNodeWithText("Guardar").performClick()

        composeTestRule.onNodeWithText("No tienes permiso para guardar").assertIsDisplayed()

        // consumir(error = true) only runs after showSnackbar()'s internal delay resolves the
        // snackbar's dismissal; waitUntil polls while pumping Compose's clock, which is what
        // actually lets that delay elapse (a one-shot waitForIdle()/advanceUntilIdle() on our own
        // Dispatchers.Main override does not -- the effect runs on Compose's own test scheduler).
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            movimientosViewModel.estadoForm.value.error == null
        }
    }
}

private fun gastoDe(id: String, unidadesMenores: Long, fecha: LocalDate) = Gasto(
    id = id,
    planId = "p1",
    categoriaId = "cat-comida",
    monto = Monto(unidadesMenores),
    fecha = fecha,
    creadoPor = "user-1",
)

private fun ingresoDe(id: String, unidadesMenores: Long, fecha: LocalDate) = Ingreso(
    id = id,
    planId = "p1",
    categoriaId = "cat-sueldo",
    monto = Monto(unidadesMenores),
    fecha = fecha,
    creadoPor = "user-1",
)

private class FakePlanFinancieroRepositorioHomeScreen(private val plan: PlanFinanciero?) : PlanFinancieroRepository {
    override fun observarPlanesDelUsuario(usuarioId: String): Flow<List<PlanFinanciero>> = flowOf(listOfNotNull(plan))
    override fun observarPlan(planId: String): Flow<PlanFinanciero?> = flowOf(plan)
    override suspend fun crearPlan(plan: PlanFinanciero) = Unit
    override suspend fun actualizarPlan(plan: PlanFinanciero) = Unit
}

private class FakeMovimientoRepositorioHomeScreen : MovimientoRepository {
    var gastos: List<Gasto> = emptyList()
    var ingresos: List<Ingreso> = emptyList()

    /** Non-null makes every create fail with this message, like a Firestore PERMISSION_DENIED. */
    var errorAlGuardar: String? = null

    override suspend fun addGasto(gasto: Gasto) = fallarSiCorresponde()
    override suspend fun addIngreso(ingreso: Ingreso) = fallarSiCorresponde()
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(gastos.filter { it.planId == planId })
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(ingresos.filter { it.planId == planId })
    override suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoLocal(ingreso: Ingreso, clave: String) = Unit
    override suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String) = Unit

    private fun fallarSiCorresponde() {
        errorAlGuardar?.let { throw IllegalStateException(it) }
    }
}

private class FakeRegistroConflictosPendientesHomeScreen : RegistroConflictosPendientes {
    private val _conflictos = MutableStateFlow<Map<String, ConflictoEdicion>>(emptyMap())
    override val conflictos: Flow<Map<String, ConflictoEdicion>> = _conflictos.asStateFlow()
    override suspend fun conflictoPara(clave: String): ConflictoEdicion? = _conflictos.value[clave]
    override suspend fun registrar(clave: String, conflicto: ConflictoEdicion) {
        _conflictos.value = _conflictos.value + (clave to conflicto)
    }
    override suspend fun resolver(clave: String) {
        _conflictos.value = _conflictos.value - clave
    }
}

private class FakeCategoriaRepositorioHomeScreen : CategoriaRepository {
    var categorias: List<Categoria> = emptyList()
    override fun observarCategorias(planId: String): Flow<List<Categoria>> = flowOf(categorias)
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        flowOf(categorias).map { cats -> cats.filter { it.tipo == tipo } }
    override suspend fun crearCategoria(categoria: Categoria) = Unit
    override suspend fun crearCategorias(categorias: List<Categoria>) = Unit
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) = Unit
}

private class FakeMovimientoSincronizacionHomeScreen : MovimientoSincronizacion {
    val planesSincronizados = mutableListOf<String>()
    override fun sincronizar(planId: String): Flow<Unit> {
        planesSincronizados += planId
        return flowOf(Unit)
    }
}

private class FakeAuthRepositorioHomeScreen : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = flowOf(Sesion(uid = "user-1", esAnonima = true))
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
